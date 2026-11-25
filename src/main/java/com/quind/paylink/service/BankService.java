package com.quind.paylink.service;

import com.quind.paylink.dto.BankAuthenticateRequest;
import com.quind.paylink.exception.ApiException;
import com.quind.paylink.model.Account;
import com.quind.paylink.model.Bank;
import com.quind.paylink.model.Transaction;
import com.quind.paylink.model.TransactionStatus;
import com.quind.paylink.repository.AccountRepository;
import com.quind.paylink.repository.BankRepository;
import java.time.Clock;
import java.time.Duration;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Simula el portal de los bancos: autenticación del pagador (documento + clave simulada)
 * y autorización del débito. Las sesiones bancarias viven en memoria (mockup).
 */
@Service
public class BankService {

    public static final Duration SESSION_TTL = Duration.ofMinutes(10);

    private final BankRepository bankRepository;
    private final AccountRepository accountRepository;
    private final PaymentService paymentService;
    private final Clock clock;
    private final Map<String, BankSession> sessions = new ConcurrentHashMap<>();

    public BankService(BankRepository bankRepository, AccountRepository accountRepository,
                       PaymentService paymentService, Clock clock) {
        this.bankRepository = bankRepository;
        this.accountRepository = accountRepository;
        this.paymentService = paymentService;
        this.clock = clock;
    }

    public record BankSession(String token, UUID transactionId, String bankCode, String holderName,
                              LocalDateTime expiresAt) {
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

        String token = UUID.randomUUID().toString();
        BankSession session = new BankSession(token, tx.getId(), bank.getCode(), account.getHolderName(),
                LocalDateTime.now(clock).plus(SESSION_TTL));
        sessions.put(token, session);
        return session;
    }

    @Transactional
    public Transaction authorize(String bankCode, UUID transactionId, String sessionToken, boolean approve) {
        Bank bank = getBank(bankCode);
        Transaction tx = paymentService.getPayment(transactionId);
        // Primero el estado (409 / idempotencia), luego la sesión (401).
        paymentService.requireStatus(tx, TransactionStatus.PENDING, "autorizar");

        BankSession session = sessionToken == null ? null : sessions.get(sessionToken);
        if (session == null
                || !session.transactionId().equals(tx.getId())
                || !session.bankCode().equals(bank.getCode())
                || session.expiresAt().isBefore(LocalDateTime.now(clock))) {
            throw ApiException.unauthorized("Sesión bancaria inválida o expirada");
        }
        // El token es de un solo uso: remove(key, value) es atómico, solo una petición concurrente lo consume.
        if (!sessions.remove(sessionToken, session)) {
            throw ApiException.unauthorized("Sesión bancaria inválida o expirada");
        }
        return paymentService.authorizePayment(tx.getId(), bank.getCode(), approve);
    }
}
