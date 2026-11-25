package com.quind.paylink.dto;

import com.quind.paylink.model.Movement;
import com.quind.paylink.model.MovementType;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.UUID;

public record MovementResponse(
        Long id,
        MovementType type,
        BigDecimal amount,
        BigDecimal balanceAfter,
        UUID transactionId,
        String reference,
        LocalDateTime createdAt) {

    public static MovementResponse from(Movement m) {
        return new MovementResponse(m.getId(), m.getType(), m.getAmount(), m.getBalanceAfter(),
                m.getTransaction().getId(), m.getTransaction().getReference(), m.getCreatedAt());
    }
}
