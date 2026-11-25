package com.quind.paylink.model;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import jakarta.persistence.Version;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.UUID;

/** Transacción de pago. Se mapea a "payment_transaction" para no chocar con palabras reservadas SQL. */
@Entity
@Table(name = "payment_transaction")
public class Transaction {

    @Id
    private UUID id;

    @Column(nullable = false, unique = true, length = 40)
    private String reference;

    @ManyToOne(optional = false)
    @JoinColumn(name = "merchant_id")
    private Merchant merchant;

    @Column(nullable = false, precision = 19, scale = 2)
    private BigDecimal amount;

    @Column(nullable = false, length = 255)
    private String description;

    @Column(length = 20)
    private String payerBankCode;

    @Enumerated(EnumType.STRING)
    @Column(length = 5)
    private DocumentType payerDocumentType;

    @Column(length = 20)
    private String payerDocumentNumber;

    @Column(length = 20)
    private String payerAccountNumber;

    @Enumerated(EnumType.STRING)
    @Column(length = 10)
    private PersonType personType;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 10)
    private TransactionStatus status;

    @Enumerated(EnumType.STRING)
    @Column(length = 30)
    private StatusReason statusReason;

    @Column(nullable = false)
    private LocalDateTime createdAt;

    @Column(nullable = false)
    private LocalDateTime updatedAt;

    @Column(length = 500)
    private String returnUrl;

    /** Bloqueo optimista: evita que dos autorizaciones concurrentes procesen la misma transacción. */
    @Version
    private Long version;

    public Transaction() {
    }

    public UUID getId() { return id; }
    public void setId(UUID id) { this.id = id; }
    public String getReference() { return reference; }
    public void setReference(String reference) { this.reference = reference; }
    public Merchant getMerchant() { return merchant; }
    public void setMerchant(Merchant merchant) { this.merchant = merchant; }
    public BigDecimal getAmount() { return amount; }
    public void setAmount(BigDecimal amount) { this.amount = amount; }
    public String getDescription() { return description; }
    public void setDescription(String description) { this.description = description; }
    public String getPayerBankCode() { return payerBankCode; }
    public void setPayerBankCode(String payerBankCode) { this.payerBankCode = payerBankCode; }
    public DocumentType getPayerDocumentType() { return payerDocumentType; }
    public void setPayerDocumentType(DocumentType payerDocumentType) { this.payerDocumentType = payerDocumentType; }
    public String getPayerDocumentNumber() { return payerDocumentNumber; }
    public void setPayerDocumentNumber(String payerDocumentNumber) { this.payerDocumentNumber = payerDocumentNumber; }
    public String getPayerAccountNumber() { return payerAccountNumber; }
    public void setPayerAccountNumber(String payerAccountNumber) { this.payerAccountNumber = payerAccountNumber; }
    public PersonType getPersonType() { return personType; }
    public void setPersonType(PersonType personType) { this.personType = personType; }
    public TransactionStatus getStatus() { return status; }
    public void setStatus(TransactionStatus status) { this.status = status; }
    public StatusReason getStatusReason() { return statusReason; }
    public void setStatusReason(StatusReason statusReason) { this.statusReason = statusReason; }
    public LocalDateTime getCreatedAt() { return createdAt; }
    public void setCreatedAt(LocalDateTime createdAt) { this.createdAt = createdAt; }
    public LocalDateTime getUpdatedAt() { return updatedAt; }
    public void setUpdatedAt(LocalDateTime updatedAt) { this.updatedAt = updatedAt; }
    public String getReturnUrl() { return returnUrl; }
    public void setReturnUrl(String returnUrl) { this.returnUrl = returnUrl; }
    public Long getVersion() { return version; }
    public void setVersion(Long version) { this.version = version; }
}
