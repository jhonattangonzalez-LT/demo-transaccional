package com.quind.paylink.dto;

import com.quind.paylink.model.StatusReason;
import com.quind.paylink.model.TransactionStatus;
import java.math.BigDecimal;
import java.util.UUID;

public record AuthorizeResponse(
        UUID transactionId,
        String reference,
        TransactionStatus status,
        StatusReason statusReason,
        BigDecimal amount,
        String resultUrl) {
}
