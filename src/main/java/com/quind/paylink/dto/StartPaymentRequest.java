package com.quind.paylink.dto;

import com.quind.paylink.model.DocumentType;
import com.quind.paylink.model.PersonType;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

public record StartPaymentRequest(
        @Schema(description = "ANDINO | CONDOR | PACIFICO", example = "ANDINO")
        @NotBlank(message = "es obligatorio") String bankCode,
        @Schema(example = "NATURAL")
        @NotNull(message = "es obligatorio") PersonType personType,
        @Schema(example = "CC")
        @NotNull(message = "es obligatorio") DocumentType documentType,
        @Schema(example = "1000000001")
        @NotBlank(message = "es obligatorio") @Size(max = 20, message = "máximo 20 caracteres") String documentNumber) {
}
