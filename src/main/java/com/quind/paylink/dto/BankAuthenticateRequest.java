package com.quind.paylink.dto;

import com.quind.paylink.model.DocumentType;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import java.util.UUID;

public record BankAuthenticateRequest(
        @Schema(description = "transactionId devuelto al crear la orden")
        @NotNull(message = "es obligatorio") UUID transactionId,
        @Schema(example = "CC")
        @NotNull(message = "es obligatorio") DocumentType documentType,
        @Schema(example = "1000000001")
        @NotBlank(message = "es obligatorio") String documentNumber,
        @Schema(description = "Clave simulada", example = "1234")
        @NotBlank(message = "es obligatoria") String password) {
}
