package com.quind.paylink.controller.api;

import com.quind.paylink.config.OpenApiConfig;
import com.quind.paylink.dto.ErrorResponse;
import com.quind.paylink.service.DemoDataService;
import com.quind.paylink.service.PaymentService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import java.time.LocalDateTime;
import java.util.Map;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@Tag(name = "4. Demo", description = "Utilidades del mockup")
@RestController
@RequestMapping("/api/v1/demo")
public class DemoApiController {

    private final DemoDataService demoDataService;
    private final PaymentService paymentService;

    public DemoApiController(DemoDataService demoDataService, PaymentService paymentService) {
        this.demoDataService = demoDataService;
        this.paymentService = paymentService;
    }

    @Operation(summary = "Restablecer datos de prueba",
            description = "Borra transacciones, movimientos y sesiones bancarias y devuelve las cuentas a sus "
                    + "saldos iniciales. Requiere la API key del comercio demo.",
            security = @SecurityRequirement(name = OpenApiConfig.API_KEY_SCHEME))
    @ApiResponse(responseCode = "200", description = "Datos restablecidos")
    @ApiResponse(responseCode = "401", description = "API key ausente o inválida", content = @Content(schema = @Schema(implementation = ErrorResponse.class)))
    @PostMapping("/reset")
    public Map<String, Object> reset(
            @Parameter(hidden = true) @RequestHeader(name = "X-Api-Key", required = false) String apiKey) {
        paymentService.authenticateMerchant(apiKey);
        demoDataService.reset();
        return Map.of("message", "Datos de prueba restablecidos", "resetAt", LocalDateTime.now().toString());
    }
}
