package com.quind.paylink.service;

import com.quind.paylink.dto.BankAuthenticateRequest;
import com.quind.paylink.exception.ApiException;
import com.quind.paylink.model.Account;
import com.quind.paylink.model.Bank;
import com.quind.paylink.model.BankSession;
import com.quind.paylink.model.Transaction;
import com.quind.paylink.model.TransactionStatus;
import com.quind.paylink.repository.AccountRepository;
import com.quind.paylink.repository.BankRepository;
import com.quind.paylink.repository.BankSessionRepository;
import java.time.Clock;
import java.time.Duration;
import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Simula el portal de los bancos: autenticación del pagador (documento + clave simulada)
 * y autorización del débito. Las sesiones bancarias se guardan en BD (un solo uso, 10 minutos).
 */
@Service
public class BankService {

    public static final Duration SESSION_TTL = Duration.ofMinutes(10);

    private final BankRepository bankRepository;
    private final AccountRepository accountRepository;
    private final BankSessionRepository sessionRepository;
    private final PaymentService paymentService;
    private final Clock clock;

    public BankService(BankRepository bankRepository, AccountRepository accountRepository,
                       BankSessionRepository sessionRepository, PaymentService paymentService, Clock clock) {
        this.bankRepository = bankRepository;
        this.accountRepository = accountRepository;
        this.sessionRepository = sessionRepository;
        this.paymentService = paymentService;
        this.clock = clock;
    }

    @Transactional(readOnly = true)
    public List<Bank> listBanks() {
        return bankRepository.findAll(Sort.by("name"));
    }

    @Transactional(readOnly = true)
    public Bank getBank(String bankCode) {
        String code = PaymentService.normalizeBankCode(bankCode);
        return bankRepository.findByCode(code)
                .orElseThrow(() -> ApiException.notFound("No existe el banco " + bankCode));
    }

    @Transactional
    public BankSession authenticate(String bankCode, BankAuthenticateRequest request) {
        Bank bank = getBank(bankCode);
        Transaction tx = paymentService.getPayment(request.transactionId());
        paymentService.requireStatus(tx, TransactionStatus.PENDING, "autenticar");
        paymentService.requireBank(tx, bank.getCode());

        boolean sameDocument = tx.getPayerDocumentType() == request.documentType()
                && tx.getPayerDocumentNumber().equals(request.documentNumber().trim());
        Account account = accountRepository.findByNumber(tx.getPayerAccountNumber())
                .orElseThrow(() -> ApiException.notFound("No existe la cuenta del pagador"));
        if (!sameDocument || !request.password().equals(account.getSimulatedPin())) {
            throw ApiException.unauthorized("Credenciales bancarias inválidas");
        }

        LocalDateTime now = LocalDateTime.now(clock);
        sessionRepository.deleteExpired(now);
        return sessionRepository.save(new BankSession(UUID.randomUUID().toString(), tx.getId(), bank.getCode(),
                account.getHolderName(), now.plus(SESSION_TTL)));
    }

    /** Devuelve la sesión si existe, corresponde a la transacción y al banco, y no ha vencido; si no, 401. */
    @Transactional(readOnly = true)
    public BankSession getValidSession(String sessionToken, UUID transactionId, String bankCode) {
        BankSession session = sessionToken == null ? null : sessionRepository.findById(sessionToken).orElse(null);
        if (session == null
                || !session.getTransactionId().equals(transactionId)
                || !session.getBankCode().equals(PaymentService.normalizeBankCode(bankCode))
                || session.getExpiresAt().isBefore(LocalDateTime.now(clock))) {
            throw ApiException.unauthorized("Sesión bancaria inválida o expirada");
        }
        return session;
    }

    @Transactional
    public Transaction authorize(String bankCode, UUID transactionId, String sessionToken, boolean approve) {
        Bank bank = getBank(bankCode);
        Transaction tx = paymentService.getPayment(transactionId);
        // Primero el estado (409 / idempotencia), luego la sesión (401).
        paymentService.requireStatus(tx, TransactionStatus.PENDING, "autorizar");
        getValidSession(sessionToken, tx.getId(), bank.getCode());
        // El token es de un solo uso: solo una petición concurrente logra borrarlo.
        if (sessionRepository.consume(sessionToken) == 0) {
            throw ApiException.unauthorized("Sesión bancaria inválida o expirada");
        }
        return paymentService.authorizePayment(tx.getId(), bank.getCode(), approve);
    }
}
