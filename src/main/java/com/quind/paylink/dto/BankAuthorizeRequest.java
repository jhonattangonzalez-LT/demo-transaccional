package com.quind.paylink.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import java.util.UUID;

public record BankAuthorizeRequest(
        @Schema(description = "transactionId devuelto al crear la orden")
        @NotNull(message = "es obligatorio") UUID transactionId,
        @Schema(description = "Token devuelto por /authenticate (un solo uso)")
        @NotBlank(message = "es obligatorio") String bankSessionToken,
        @Schema(description = "true = aprobar el débito, false = rechazarlo", example = "true")
        @NotNull(message = "es obligatorio") Boolean approve) {
}
