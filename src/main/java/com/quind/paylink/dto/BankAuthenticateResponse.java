package com.quind.paylink.dto;

import java.time.LocalDateTime;
import java.util.UUID;

public record BankAuthenticateResponse(
        String bankSessionToken,
        UUID transactionId,
        String bankCode,
        String holderName,
        LocalDateTime expiresAt) {
}
