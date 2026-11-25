package com.quind.paylink.dto;

import com.quind.paylink.model.TransactionStatus;
import java.util.UUID;

public record StartPaymentResponse(
        UUID transactionId,
        String reference,
        TransactionStatus status,
        String bankCode,
        String bankRedirectUrl) {
}
