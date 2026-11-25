package com.quind.paylink.repository;

import com.quind.paylink.model.BankSession;
import java.time.LocalDateTime;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface BankSessionRepository extends JpaRepository<BankSession, String> {

    /** Borrado atómico: devuelve 1 solo para la primera petición que consume el token. */
    @Modifying
    @Query("delete from BankSession s where s.token = :token")
    int consume(@Param("token") String token);

    @Modifying
    @Query("delete from BankSession s where s.expiresAt < :now")
    int deleteExpired(@Param("now") LocalDateTime now);
}
