package com.quind.paylink.service;

import com.quind.paylink.dto.CreatePaymentRequest;
import com.quind.paylink.dto.StartPaymentRequest;
import com.quind.paylink.exception.ApiException;
import com.quind.paylink.model.Account;
import com.quind.paylink.model.AccountType;
import com.quind.paylink.model.Bank;
import com.quind.paylink.model.Merchant;
import com.quind.paylink.model.StatusReason;
import com.quind.paylink.model.Transaction;
import com.quind.paylink.model.TransactionStatus;
import com.quind.paylink.repository.AccountRepository;
import com.quind.paylink.repository.BankRepository;
import com.quind.paylink.repository.MerchantRepository;
import com.quind.paylink.repository.TransactionRepository;
import java.math.BigDecimal;
import java.security.SecureRandom;
import java.time.Clock;
import java.time.Duration;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.Locale;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** Ciclo de vida de una transacción: CREATED -> PENDING -> APPROVED | REJECTED | FAILED | EXPIRED. */
@Service
public class PaymentService {

    public static final Duration PENDING_TIMEOUT = Duration.ofMinutes(15);

    private static final String REF_ALPHABET = "ABCDEFGHJKLMNPQRSTUVWXYZ23456789";
    private static final DateTimeFormatter REF_DATE = DateTimeFormatter.ofPattern("yyyyMMdd");

    private final TransactionRepository transactionRepository;
    private final MerchantRepository merchantRepository;
    private final BankRepository bankRepository;
    private final AccountRepository accountRepository;
    private final AccountService accountService;
    private final Clock clock;
    private final SecureRandom random = new SecureRandom();

    public PaymentService(TransactionRepository transactionRepository, MerchantRepository merchantRepository,
                          BankRepository bankRepository, AccountRepository accountRepository,
                          AccountService accountService, Clock clock) {
        this.transactionRepository = transactionRepository;
        this.merchantRepository = merchantRepository;
        this.bankRepository = bankRepository;
        this.accountRepository = accountRepository;
        this.accountService = accountService;
        this.clock = clock;
    }

    // ---------------------------------------------------------------- 1. crear orden

    @Transactional
    public Transaction createPayment(String apiKey, CreatePaymentRequest request) {
        Merchant merchant = authenticateMerchant(apiKey);
        validateAmount(request.amount());
        if (request.description() == null || request.description().isBlank()) {
            throw ApiException.badRequest("description: es obligatoria");
        }
        // Solo URLs http(s): evita enlaces "javascript:" u otros esquemas en el botón de retorno.
        if (request.returnUrl() != null && !request.returnUrl().isBlank()
                && !request.returnUrl().matches("(?i)^https?://\\S+$")) {
            throw ApiException.badRequest("returnUrl: debe ser una URL http(s) válida");
        }

        LocalDateTime now = now();
        Transaction tx = new Transaction();
        tx.setId(UUID.randomUUID());
        tx.setReference(generateReference(now));
        tx.setMerchant(merchant);
        tx.setAmount(request.amount().setScale(2));
        tx.setDescription(request.description().trim());
        tx.setReturnUrl(request.returnUrl());
        tx.setStatus(TransactionStatus.CREATED);
        tx.setCreatedAt(now);
        tx.setUpdatedAt(now);
        return transactionRepository.save(tx);
    }

    public Merchant authenticateMerchant(String apiKey) {
        if (apiKey == null || apiKey.isBlank()) {
            throw ApiException.unauthorized("Falta el header X-Api-Key");
        }
        return merchantRepository.findByApiKey(apiKey)
                .orElseThrow(() -> ApiException.unauthorized("API key inválida"));
    }

    // ---------------------------------------------------------------- consultas

    /** Consulta la transacción aplicando la regla de expiración (PENDING > 15 min => EXPIRED). */
    @Transactional
    public Transaction getPayment(UUID id) {
        Transaction tx = transactionRepository.findById(id)
                .orElseThrow(() -> ApiException.notFound("No existe la transacción " + id));
        return applyExpiration(tx);
    }

    @Transactional
    public Transaction getPaymentByReference(String reference) {
        Transaction tx = transactionRepository.findByReference(reference)
                .orElseThrow(() -> ApiException.notFound("No existe la transacción con referencia " + reference));
        return applyExpiration(tx);
    }

    // ---------------------------------------------------------------- 2. iniciar pago

    @Transactional
    public Transaction startPayment(UUID id, StartPaymentRequest request) {
        Transaction tx = getPayment(id);
        requireStatus(tx, TransactionStatus.CREATED, "iniciar");

        String bankCode = normalizeBankCode(request.bankCode());
        Bank bank = bankRepository.findByCode(bankCode)
                .orElseThrow(() -> ApiException.notFound("No existe el banco " + bankCode));
        String documentNumber = request.documentNumber().trim();
        Account payer = accountRepository
                .findFirstByBankCodeAndDocumentTypeAndDocumentNumberAndType(
                        bank.getCode(), request.documentType(), documentNumber, AccountType.PAYER)
                .orElseThrow(() -> ApiException.notFound("No existe una cuenta en " + bank.getName()
                        + " para el documento " + request.documentType() + " " + documentNumber));

        tx.setPayerBankCode(bank.getCode());
        tx.setPersonType(request.personType());
        tx.setPayerDocumentType(request.documentType());
        tx.setPayerDocumentNumber(documentNumber);
        tx.setPayerAccountNumber(payer.getNumber());
        tx.setStatus(TransactionStatus.PENDING);
        tx.setUpdatedAt(now());
        return tx;
    }

    // ---------------------------------------------------------------- 3. autorizar / rechazar

    /**
     * Procesa la decisión del pagador. Débito, crédito y cambio de estado ocurren en una sola
     * transacción de base de datos: o se aplican los tres o ninguno.
     * La validación de la sesión bancaria la hace {@link BankService} antes de llamar a este método.
     */
    @Transactional
    public Transaction authorizePayment(UUID id, String bankCode, boolean approve) {
        Transaction tx = getPayment(id);
        requireStatus(tx, TransactionStatus.PENDING, "autorizar");
        requireBank(tx, bankCode);

        if (!approve) {
            return finish(tx, TransactionStatus.REJECTED, StatusReason.USER_CANCELLED);
        }

        Account payer = accountRepository.findByNumberForUpdate(tx.getPayerAccountNumber())
                .orElseThrow(() -> ApiException.notFound("No existe la cuenta del pagador"));
        Account merchantAccount = accountRepository.findByNumberForUpdate(tx.getMerchant().getAccount().getNumber())
                .orElseThrow(() -> ApiException.notFound("No existe la cuenta del comercio"));

        if (payer.getBalance().compareTo(tx.getAmount()) < 0) {
            return finish(tx, TransactionStatus.REJECTED, StatusReason.INSUFFICIENT_FUNDS);
        }

        accountService.transfer(payer, merchantAccount, tx.getAmount(), tx);
        return finish(tx, TransactionStatus.APPROVED, null);
    }

    // ---------------------------------------------------------------- reglas auxiliares

    public void requireStatus(Transaction tx, TransactionStatus expected, String action) {
        if (tx.getStatus() != expected) {
            throw ApiException.conflict("No se puede " + action + " la transacción " + tx.getReference()
                    + ": estado actual " + tx.getStatus() + ", se esperaba " + expected);
        }
    }

    public void requireBank(Transaction tx, String bankCode) {
        if (!tx.getPayerBankCode().equals(normalizeBankCode(bankCode))) {
            throw ApiException.badRequest("La transacción " + tx.getReference()
                    + " fue iniciada con el banco " + tx.getPayerBankCode() + ", no con " + bankCode);
        }
    }

    public static String normalizeBankCode(String bankCode) {
        return bankCode == null ? "" : bankCode.trim().toUpperCase(Locale.ROOT);
    }

    private Transaction applyExpiration(Transaction tx) {
        if (tx.getStatus() == TransactionStatus.PENDING
                && tx.getUpdatedAt().plus(PENDING_TIMEOUT).isBefore(now())) {
            finish(tx, TransactionStatus.EXPIRED, StatusReason.PENDING_TIMEOUT);
        }
        return tx;
    }

    private Transaction finish(Transaction tx, TransactionStatus status, StatusReason reason) {
        tx.setStatus(status);
        tx.setStatusReason(reason);
        tx.setUpdatedAt(now());
        return tx;
    }

    private void validateAmount(BigDecimal amount) {
        if (amount == null) {
            throw ApiException.badRequest("amount: es obligatorio");
        }
        if (amount.signum() <= 0) {
            throw ApiException.badRequest("amount: debe ser mayor que 0");
        }
        if (amount.stripTrailingZeros().scale() > 2) {
            throw ApiException.badRequest("amount: máximo 2 decimales");
        }
    }

    private String generateReference(LocalDateTime now) {
        String reference;
        do {
            StringBuilder sb = new StringBuilder("PL-").append(REF_DATE.format(now)).append('-');
            for (int i = 0; i < 6; i++) {
                sb.append(REF_ALPHABET.charAt(random.nextInt(REF_ALPHABET.length())));
            }
            reference = sb.toString();
        } while (transactionRepository.existsByReference(reference));
        return reference;
    }

    private LocalDateTime now() {
        return LocalDateTime.now(clock);
    }
}
