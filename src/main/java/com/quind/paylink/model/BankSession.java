package com.quind.paylink.model;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.LocalDateTime;
import java.util.UUID;

/**
 * Sesión del portal bancario simulado. Se guarda en BD (no en memoria) para que funcione
 * aunque cada petición la atienda una instancia distinta (p. ej. contenedores en Vercel).
 */
@Entity
@Table(name = "bank_session")
public class BankSession {

    @Id
    @Column(length = 36)
    private String token;

    @Column(nullable = false)
    private UUID transactionId;

    @Column(nullable = false, length = 20)
    private String bankCode;

    @Column(nullable = false, length = 120)
    private String holderName;

    @Column(nullable = false)
    private LocalDateTime expiresAt;

    public BankSession() {
    }

    public BankSession(String token, UUID transactionId, String bankCode, String holderName, LocalDateTime expiresAt) {
        this.token = token;
        this.transactionId = transactionId;
        this.bankCode = bankCode;
        this.holderName = holderName;
        this.expiresAt = expiresAt;
    }

    public String getToken() { return token; }
    public UUID getTransactionId() { return transactionId; }
    public String getBankCode() { return bankCode; }
    public String getHolderName() { return holderName; }
    public LocalDateTime getExpiresAt() { return expiresAt; }
}
