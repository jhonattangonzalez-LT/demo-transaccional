package com.quind.paylink.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.quind.paylink.dto.CreatePaymentRequest;
import com.quind.paylink.dto.StartPaymentRequest;
import com.quind.paylink.exception.ApiException;
import com.quind.paylink.model.Account;
import com.quind.paylink.model.AccountType;
import com.quind.paylink.model.Bank;
import com.quind.paylink.model.DocumentType;
import com.quind.paylink.model.Merchant;
import com.quind.paylink.model.Movement;
import com.quind.paylink.model.PersonType;
import com.quind.paylink.model.StatusReason;
import com.quind.paylink.model.Transaction;
import com.quind.paylink.model.TransactionStatus;
import com.quind.paylink.repository.AccountRepository;
import com.quind.paylink.repository.BankRepository;
import com.quind.paylink.repository.MerchantRepository;
import com.quind.paylink.repository.MovementRepository;
import com.quind.paylink.repository.TransactionRepository;
import java.math.BigDecimal;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;

class PaymentServiceTest {

    private final Clock clock = Clock.fixed(Instant.parse("2026-01-15T10:00:00Z"), ZoneOffset.UTC);
    private final LocalDateTime now = LocalDateTime.now(clock);

    private TransactionRepository transactionRepository;
    private MerchantRepository merchantRepository;
    private BankRepository bankRepository;
    private AccountRepository accountRepository;
    private MovementRepository movementRepository;
    private PaymentService service;

    private Bank andino;
    private Account payer;
    private Account merchantAccount;
    private Merchant merchant;

    @BeforeEach
    void setUp() {
        transactionRepository = mock(TransactionRepository.class);
        merchantRepository = mock(MerchantRepository.class);
        bankRepository = mock(BankRepository.class);
        accountRepository = mock(AccountRepository.class);
        movementRepository = mock(MovementRepository.class);
        AccountService accountService = new AccountService(accountRepository, movementRepository, clock);
        service = new PaymentService(transactionRepository, merchantRepository, bankRepository,
                accountRepository, accountService, clock);

        andino = new Bank(1L, "ANDINO", "Banco Andino");
        payer = account("1000000001", "Ana Prueba", DocumentType.CC, "1000000001", "100000.00", AccountType.PAYER);
        merchantAccount = account("9000000001", "Tienda Demo", DocumentType.NIT, "900000001", "0.00", AccountType.MERCHANT);
        merchant = new Merchant();
        merchant.setId(1L);
        merchant.setName("Tienda Demo S.A.S.");
        merchant.setAccount(merchantAccount);
        merchant.setApiKey("key");

        when(bankRepository.findByCode("ANDINO")).thenReturn(Optional.of(andino));
        when(accountRepository.findByNumberForUpdate(payer.getNumber())).thenReturn(Optional.of(payer));
        when(accountRepository.findByNumberForUpdate(merchantAccount.getNumber())).thenReturn(Optional.of(merchantAccount));
    }

    // ------------------------------------------------------------ creación e inicio

    @Test
    void createPayment_createsTransactionInCreatedState() {
        when(merchantRepository.findByApiKey("key")).thenReturn(Optional.of(merchant));
        when(transactionRepository.save(any(Transaction.class))).thenAnswer(inv -> inv.getArgument(0));

        Transaction tx = service.createPayment("key", new CreatePaymentRequest(new BigDecimal("1500.5"), "Compra", null));

        assertThat(tx.getStatus()).isEqualTo(TransactionStatus.CREATED);
        assertThat(tx.getReference()).startsWith("PL-20260115-");
        assertThat(tx.getAmount()).isEqualByComparingTo("1500.50");
        assertThat(tx.getId()).isNotNull();
    }

    @Test
    void createPayment_rejectsInvalidApiKey() {
        when(merchantRepository.findByApiKey(anyString())).thenReturn(Optional.empty());

        assertApiError(() -> service.createPayment("bad", new CreatePaymentRequest(BigDecimal.TEN, "x", null)),
                HttpStatus.UNAUTHORIZED);
    }

    @Test
    void createPayment_rejectsNonPositiveAmountAndMoreThanTwoDecimals() {
        when(merchantRepository.findByApiKey("key")).thenReturn(Optional.of(merchant));

        assertApiError(() -> service.createPayment("key", new CreatePaymentRequest(new BigDecimal("-1"), "x", null)),
                HttpStatus.BAD_REQUEST);
        assertApiError(() -> service.createPayment("key", new CreatePaymentRequest(new BigDecimal("1.234"), "x", null)),
                HttpStatus.BAD_REQUEST);
    }

    @Test
    void startPayment_movesToPendingAndLinksPayerAccount() {
        Transaction tx = stored(TransactionStatus.CREATED, "50000.00");
        when(accountRepository.findFirstByBankCodeAndDocumentTypeAndDocumentNumberAndType(
                "ANDINO", DocumentType.CC, "1000000001", AccountType.PAYER)).thenReturn(Optional.of(payer));

        service.startPayment(tx.getId(), new StartPaymentRequest("andino", PersonType.NATURAL, DocumentType.CC, "1000000001"));

        assertThat(tx.getStatus()).isEqualTo(TransactionStatus.PENDING);
        assertThat(tx.getPayerBankCode()).isEqualTo("ANDINO");
        assertThat(tx.getPayerAccountNumber()).isEqualTo(payer.getNumber());
    }

    @Test
    void startPayment_unknownDocumentReturns404() {
        Transaction tx = stored(TransactionStatus.CREATED, "50000.00");
        when(accountRepository.findFirstByBankCodeAndDocumentTypeAndDocumentNumberAndType(
                any(), any(), any(), any())).thenReturn(Optional.empty());

        assertApiError(() -> service.startPayment(tx.getId(),
                new StartPaymentRequest("ANDINO", PersonType.NATURAL, DocumentType.CC, "999")), HttpStatus.NOT_FOUND);
        assertThat(tx.getStatus()).isEqualTo(TransactionStatus.CREATED);
    }

    // ------------------------------------------------------------ autorización

    @Test
    void authorize_approved_debitsPayerCreditsMerchantAndApproves() {
        Transaction tx = pending("30000.00");

        Transaction result = service.authorizePayment(tx.getId(), "ANDINO", true);

        assertThat(result.getStatus()).isEqualTo(TransactionStatus.APPROVED);
        assertThat(result.getStatusReason()).isNull();
        assertThat(payer.getBalance()).isEqualByComparingTo("70000.00");
        assertThat(merchantAccount.getBalance()).isEqualByComparingTo("30000.00");
        verify(movementRepository, times(2)).save(any(Movement.class));
    }

    @Test
    void authorize_rejectedByUser_doesNotMoveMoney() {
        Transaction tx = pending("30000.00");

        Transaction result = service.authorizePayment(tx.getId(), "ANDINO", false);

        assertThat(result.getStatus()).isEqualTo(TransactionStatus.REJECTED);
        assertThat(result.getStatusReason()).isEqualTo(StatusReason.USER_CANCELLED);
        assertThat(payer.getBalance()).isEqualByComparingTo("100000.00");
        assertThat(merchantAccount.getBalance()).isEqualByComparingTo("0.00");
        verify(movementRepository, never()).save(any());
    }

    @Test
    void authorize_insufficientFunds_rejectsWithoutMovingMoney() {
        Transaction tx = pending("100000.01");

        Transaction result = service.authorizePayment(tx.getId(), "ANDINO", true);

        assertThat(result.getStatus()).isEqualTo(TransactionStatus.REJECTED);
        assertThat(result.getStatusReason()).isEqualTo(StatusReason.INSUFFICIENT_FUNDS);
        assertThat(payer.getBalance()).isEqualByComparingTo("100000.00");
        assertThat(merchantAccount.getBalance()).isEqualByComparingTo("0.00");
        verify(movementRepository, never()).save(any());
    }

    @Test
    void authorize_invalidTransition_returns409() {
        Transaction created = stored(TransactionStatus.CREATED, "1000.00");
        assertApiError(() -> service.authorizePayment(created.getId(), "ANDINO", true), HttpStatus.CONFLICT);

        Transaction approved = pending("1000.00");
        approved.setStatus(TransactionStatus.APPROVED);
        assertApiError(() -> service.authorizePayment(approved.getId(), "ANDINO", true), HttpStatus.CONFLICT);
        assertThat(payer.getBalance()).isEqualByComparingTo("100000.00");
    }

    @Test
    void startPayment_onAlreadyPendingTransaction_returns409() {
        Transaction tx = pending("1000.00");

        assertApiError(() -> service.startPayment(tx.getId(),
                new StartPaymentRequest("ANDINO", PersonType.NATURAL, DocumentType.CC, "1000000001")), HttpStatus.CONFLICT);
    }

    @Test
    void getPayment_pendingForMoreThan15Minutes_isExpired() {
        Transaction tx = pending("1000.00");
        tx.setUpdatedAt(now.minusMinutes(16));

        Transaction result = service.getPayment(tx.getId());

        assertThat(result.getStatus()).isEqualTo(TransactionStatus.EXPIRED);
        assertThat(result.getStatusReason()).isEqualTo(StatusReason.PENDING_TIMEOUT);
        assertApiError(() -> service.authorizePayment(tx.getId(), "ANDINO", true), HttpStatus.CONFLICT);
    }

    // ------------------------------------------------------------ helpers

    private Account account(String number, String holder, DocumentType docType, String doc, String balance, AccountType type) {
        Account a = new Account();
        a.setNumber(number);
        a.setHolderName(holder);
        a.setDocumentType(docType);
        a.setDocumentNumber(doc);
        a.setBank(andino);
        a.setBalance(new BigDecimal(balance));
        a.setType(type);
        a.setSimulatedPin("1234");
        return a;
    }

    private Transaction stored(TransactionStatus status, String amount) {
        Transaction tx = new Transaction();
        tx.setId(UUID.randomUUID());
        tx.setReference("PL-TEST-" + tx.getId().toString().substring(0, 6));
        tx.setMerchant(merchant);
        tx.setAmount(new BigDecimal(amount));
        tx.setDescription("Prueba");
        tx.setStatus(status);
        tx.setCreatedAt(now);
        tx.setUpdatedAt(now);
        when(transactionRepository.findById(tx.getId())).thenReturn(Optional.of(tx));
        return tx;
    }

    private Transaction pending(String amount) {
        Transaction tx = stored(TransactionStatus.PENDING, amount);
        tx.setPayerBankCode("ANDINO");
        tx.setPersonType(PersonType.NATURAL);
        tx.setPayerDocumentType(DocumentType.CC);
        tx.setPayerDocumentNumber(payer.getDocumentNumber());
        tx.setPayerAccountNumber(payer.getNumber());
        return tx;
    }

    private static void assertApiError(Runnable action, HttpStatus expected) {
        assertThatThrownBy(action::run)
                .isInstanceOf(ApiException.class)
                .satisfies(ex -> assertThat(((ApiException) ex).getStatus()).isEqualTo(expected));
    }
}
