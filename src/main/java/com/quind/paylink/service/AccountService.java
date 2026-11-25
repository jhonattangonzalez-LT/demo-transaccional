package com.quind.paylink.service;

import com.quind.paylink.exception.ApiException;
import com.quind.paylink.model.Account;
import com.quind.paylink.model.Movement;
import com.quind.paylink.model.MovementType;
import com.quind.paylink.model.Transaction;
import com.quind.paylink.repository.AccountRepository;
import com.quind.paylink.repository.MovementRepository;
import java.math.BigDecimal;
import java.time.Clock;
import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class AccountService {

    private final AccountRepository accountRepository;
    private final MovementRepository movementRepository;
    private final Clock clock;

    public AccountService(AccountRepository accountRepository, MovementRepository movementRepository, Clock clock) {
        this.accountRepository = accountRepository;
        this.movementRepository = movementRepository;
        this.clock = clock;
    }

    @Transactional(readOnly = true)
    public Account getByNumber(String number) {
        return accountRepository.findByNumber(number)
                .orElseThrow(() -> ApiException.notFound("No existe la cuenta " + number));
    }

    @Transactional(readOnly = true)
    public List<Movement> getMovements(String accountNumber, UUID transactionId) {
        getByNumber(accountNumber); // 404 si la cuenta no existe
        return transactionId == null
                ? movementRepository.findByAccountNumberOrderByIdAsc(accountNumber)
                : movementRepository.findByAccountNumberAndTransactionIdOrderByIdAsc(accountNumber, transactionId);
    }

    /**
     * Debita {@code from}, acredita {@code to} y registra ambos movimientos.
     * No abre transacción propia: debe invocarse dentro de la transacción del llamador
     * para que débito, crédito y cambio de estado sean atómicos.
     */
    public void transfer(Account from, Account to, BigDecimal amount, Transaction transaction) {
        if (from.getBalance().compareTo(amount) < 0) {
            throw new IllegalStateException("Fondos insuficientes en la cuenta " + from.getNumber());
        }
        LocalDateTime now = LocalDateTime.now(clock);
        from.setBalance(from.getBalance().subtract(amount));
        to.setBalance(to.getBalance().add(amount));
        movementRepository.save(new Movement(from, transaction, MovementType.DEBIT, amount, from.getBalance(), now));
        movementRepository.save(new Movement(to, transaction, MovementType.CREDIT, amount, to.getBalance(), now));
    }
}
