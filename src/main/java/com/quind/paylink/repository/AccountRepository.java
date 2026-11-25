package com.quind.paylink.repository;

import com.quind.paylink.model.Account;
import com.quind.paylink.model.AccountType;
import com.quind.paylink.model.DocumentType;
import jakarta.persistence.LockModeType;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface AccountRepository extends JpaRepository<Account, Long> {

    Optional<Account> findByNumber(String number);

    Optional<Account> findFirstByBankCodeAndDocumentTypeAndDocumentNumberAndType(
            String bankCode, DocumentType documentType, String documentNumber, AccountType type);

    /** Lee la cuenta con bloqueo pesimista para debitar/acreditar sin condiciones de carrera. */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select a from Account a where a.number = :number")
    Optional<Account> findByNumberForUpdate(@Param("number") String number);
}
