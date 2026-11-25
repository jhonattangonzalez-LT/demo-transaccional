package com.quind.paylink.repository;

import com.quind.paylink.model.Bank;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface BankRepository extends JpaRepository<Bank, Long> {

    Optional<Bank> findByCode(String code);
}
