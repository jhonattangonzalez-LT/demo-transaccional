package com.quind.paylink.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Digits;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.math.BigDecimal;

public record CreatePaymentRequest(
        @Schema(description = "Valor en COP, > 0 y máximo 2 decimales", example = "150000.00")
        @NotNull(message = "es obligatorio")
        @DecimalMin(value = "0.00", inclusive = false, message = "debe ser mayor que 0")
        @Digits(integer = 15, fraction = 2, message = "máximo 15 enteros y 2 decimales")
        BigDecimal amount,

        @Schema(example = "Compra de prueba en Tienda Demo")
        @NotBlank(message = "es obligatoria")
        @Size(max = 255, message = "máximo 255 caracteres")
        String description,

        @Schema(description = "URL http(s) de retorno al comercio (opcional)", example = "https://tienda-demo.example/retorno")
        @Size(max = 500, message = "máximo 500 caracteres")
        String returnUrl) {
}
