package com.quind.paylink.repository;

import com.quind.paylink.model.Movement;
import java.util.List;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface MovementRepository extends JpaRepository<Movement, Long> {

    List<Movement> findByAccountNumberOrderByIdAsc(String accountNumber);

    List<Movement> findByAccountNumberAndTransactionIdOrderByIdAsc(String accountNumber, UUID transactionId);
}
