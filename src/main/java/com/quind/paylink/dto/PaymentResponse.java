package com.quind.paylink.dto;

import com.quind.paylink.model.DocumentType;
import com.quind.paylink.model.PersonType;
import com.quind.paylink.model.StatusReason;
import com.quind.paylink.model.Transaction;
import com.quind.paylink.model.TransactionStatus;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.UUID;

public record PaymentResponse(
        UUID transactionId,
        String reference,
        String merchantName,
        BigDecimal amount,
        String currency,
        String description,
        TransactionStatus status,
        StatusReason statusReason,
        String payerBankCode,
        PersonType personType,
        DocumentType payerDocumentType,
        String payerDocumentNumber,
        LocalDateTime createdAt,
        LocalDateTime updatedAt,
        String returnUrl) {

    public static PaymentResponse from(Transaction tx) {
        return new PaymentResponse(
                tx.getId(),
                tx.getReference(),
                tx.getMerchant().getName(),
                tx.getAmount(),
                "COP",
                tx.getDescription(),
                tx.getStatus(),
                tx.getStatusReason(),
                tx.getPayerBankCode(),
                tx.getPersonType(),
                tx.getPayerDocumentType(),
                tx.getPayerDocumentNumber(),
                tx.getCreatedAt(),
                tx.getUpdatedAt(),
                tx.getReturnUrl());
    }
}
