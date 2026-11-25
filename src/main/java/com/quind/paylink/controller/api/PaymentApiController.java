package com.quind.paylink.controller.api;

import com.quind.paylink.config.OpenApiConfig;
import com.quind.paylink.dto.CreatePaymentRequest;
import com.quind.paylink.dto.CreatePaymentResponse;
import com.quind.paylink.dto.ErrorResponse;
import com.quind.paylink.dto.PaymentResponse;
import com.quind.paylink.dto.StartPaymentRequest;
import com.quind.paylink.dto.StartPaymentResponse;
import com.quind.paylink.model.Transaction;
import com.quind.paylink.service.PaymentService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import java.util.UUID;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.servlet.support.ServletUriComponentsBuilder;

@Tag(name = "1. Pagos", description = "Creación, inicio y consulta de transacciones")
@RestController
@RequestMapping("/api/v1/payments")
public class PaymentApiController {

    private final PaymentService paymentService;

    public PaymentApiController(PaymentService paymentService) {
        this.paymentService = paymentService;
    }

    @Operation(summary = "Crear orden de pago (comercio)",
            description = "Crea la transacción en estado CREATED. Requiere el header X-Api-Key del comercio.",
            security = @SecurityRequirement(name = OpenApiConfig.API_KEY_SCHEME))
    @ApiResponse(responseCode = "201", description = "Orden creada")
    @ApiResponse(responseCode = "400", description = "Monto o datos inválidos", content = @Content(schema = @Schema(implementation = ErrorResponse.class)))
    @ApiResponse(responseCode = "401", description = "API key ausente o inválida", content = @Content(schema = @Schema(implementation = ErrorResponse.class)))
    @PostMapping
    public ResponseEntity<CreatePaymentResponse> create(
            @Parameter(hidden = true) @RequestHeader(name = "X-Api-Key", required = false) String apiKey,
            @Valid @RequestBody CreatePaymentRequest request) {
        Transaction tx = paymentService.createPayment(apiKey, request);
        String checkoutUrl = url("/checkout/{id}", tx.getId());
        CreatePaymentResponse body = new CreatePaymentResponse(tx.getId(), tx.getReference(), tx.getStatus(),
                tx.getAmount(), checkoutUrl);
        return ResponseEntity.created(ServletUriComponentsBuilder.fromCurrentRequest().path("/{id}")
                .buildAndExpand(tx.getId()).toUri()).body(body);
    }

    @Operation(summary = "Consultar transacción por id",
            description = "Si lleva más de 15 minutos en PENDING se marca EXPIRED al consultarla.")
    @ApiResponse(responseCode = "200", description = "Detalle de la transacción")
    @ApiResponse(responseCode = "400", description = "Id con formato inválido", content = @Content(schema = @Schema(implementation = ErrorResponse.class)))
    @ApiResponse(responseCode = "404", description = "No existe", content = @Content(schema = @Schema(implementation = ErrorResponse.class)))
    @GetMapping("/{id}")
    public PaymentResponse get(@PathVariable UUID id) {
        return PaymentResponse.from(paymentService.getPayment(id));
    }

    @Operation(summary = "Consultar transacción por referencia")
    @ApiResponse(responseCode = "200", description = "Detalle de la transacción")
    @ApiResponse(responseCode = "404", description = "No existe", content = @Content(schema = @Schema(implementation = ErrorResponse.class)))
    @GetMapping("/reference/{reference}")
    public PaymentResponse getByReference(@Parameter(example = "PL-20261125-ABC234") @PathVariable String reference) {
        return PaymentResponse.from(paymentService.getPaymentByReference(reference));
    }

    @Operation(summary = "Iniciar pago (pagador)",
            description = "El pagador elige banco, tipo de persona y documento. La transacción pasa a PENDING y se "
                    + "devuelve la URL del portal del banco.")
    @ApiResponse(responseCode = "200", description = "Pago iniciado (PENDING)")
    @ApiResponse(responseCode = "400", description = "Datos inválidos", content = @Content(schema = @Schema(implementation = ErrorResponse.class)))
    @ApiResponse(responseCode = "404", description = "Transacción, banco o cuenta del documento inexistente", content = @Content(schema = @Schema(implementation = ErrorResponse.class)))
    @ApiResponse(responseCode = "409", description = "La transacción no está en CREATED", content = @Content(schema = @Schema(implementation = ErrorResponse.class)))
    @PostMapping("/{id}/start")
    public StartPaymentResponse start(@PathVariable UUID id, @Valid @RequestBody StartPaymentRequest request) {
        Transaction tx = paymentService.startPayment(id, request);
        String bankRedirectUrl = ServletUriComponentsBuilder.fromCurrentContextPath()
                .path("/bank/{code}/login").queryParam("tx", tx.getId())
                .buildAndExpand(tx.getPayerBankCode()).toUriString();
        return new StartPaymentResponse(tx.getId(), tx.getReference(), tx.getStatus(), tx.getPayerBankCode(),
                bankRedirectUrl);
    }

    static String url(String path, Object... vars) {
        return ServletUriComponentsBuilder.fromCurrentContextPath().path(path).buildAndExpand(vars).toUriString();
    }
}
