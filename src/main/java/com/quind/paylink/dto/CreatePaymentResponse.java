package com.quind.paylink.dto;

import com.quind.paylink.model.TransactionStatus;
import java.math.BigDecimal;
import java.util.UUID;

public record CreatePaymentResponse(
        UUID transactionId,
        String reference,
        TransactionStatus status,
        BigDecimal amount,
        String checkoutUrl) {
}
