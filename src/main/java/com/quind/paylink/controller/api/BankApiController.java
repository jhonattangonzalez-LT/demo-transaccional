package com.quind.paylink.controller.api;

import com.quind.paylink.dto.AuthorizeResponse;
import com.quind.paylink.dto.BankAuthenticateRequest;
import com.quind.paylink.dto.BankAuthenticateResponse;
import com.quind.paylink.dto.BankAuthorizeRequest;
import com.quind.paylink.dto.BankResponse;
import com.quind.paylink.dto.ErrorResponse;
import com.quind.paylink.model.BankSession;
import com.quind.paylink.model.Transaction;
import com.quind.paylink.service.BankService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import java.util.List;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@Tag(name = "2. Bancos", description = "Bancos disponibles y portal bancario simulado (autenticación y autorización)")
@RestController
@RequestMapping("/api/v1")
public class BankApiController {

    private final BankService bankService;

    public BankApiController(BankService bankService) {
        this.bankService = bankService;
    }

    @Operation(summary = "Listar bancos disponibles")
    @ApiResponse(responseCode = "200", description = "Bancos ficticios")
    @GetMapping("/banks")
    public List<BankResponse> banks() {
        return bankService.listBanks().stream().map(BankResponse::from).toList();
    }

    @Operation(summary = "Autenticar pagador en el banco simulado",
            description = "Valida documento + clave simulada (1234) contra la transacción PENDING y devuelve un "
                    + "bankSessionToken de un solo uso que vence en 10 minutos.")
    @ApiResponse(responseCode = "200", description = "Autenticado")
    @ApiResponse(responseCode = "400", description = "Datos inválidos o banco distinto al de la transacción", content = @Content(schema = @Schema(implementation = ErrorResponse.class)))
    @ApiResponse(responseCode = "401", description = "Credenciales bancarias inválidas", content = @Content(schema = @Schema(implementation = ErrorResponse.class)))
    @ApiResponse(responseCode = "404", description = "Banco o transacción inexistente", content = @Content(schema = @Schema(implementation = ErrorResponse.class)))
    @ApiResponse(responseCode = "409", description = "La transacción no está en PENDING", content = @Content(schema = @Schema(implementation = ErrorResponse.class)))
    @PostMapping("/bank/{bankCode}/authenticate")
    public BankAuthenticateResponse authenticate(
            @Parameter(example = "ANDINO") @PathVariable String bankCode,
            @Valid @RequestBody BankAuthenticateRequest request) {
        BankSession s = bankService.authenticate(bankCode, request);
        return new BankAuthenticateResponse(s.getToken(), s.getTransactionId(), s.getBankCode(), s.getHolderName(),
                s.getExpiresAt());
    }

    @Operation(summary = "Autorizar o rechazar el débito",
            description = "approve=true con saldo suficiente: debita al pagador, acredita al comercio y deja la "
                    + "transacción APPROVED (todo en una sola transacción de BD). Sin saldo: REJECTED / "
                    + "INSUFFICIENT_FUNDS. approve=false: REJECTED / USER_CANCELLED.")
    @ApiResponse(responseCode = "200", description = "Decisión procesada (ver status y statusReason)")
    @ApiResponse(responseCode = "400", description = "Datos inválidos", content = @Content(schema = @Schema(implementation = ErrorResponse.class)))
    @ApiResponse(responseCode = "401", description = "Sesión bancaria inválida, usada o expirada", content = @Content(schema = @Schema(implementation = ErrorResponse.class)))
    @ApiResponse(responseCode = "404", description = "Banco o transacción inexistente", content = @Content(schema = @Schema(implementation = ErrorResponse.class)))
    @ApiResponse(responseCode = "409", description = "La transacción no está en PENDING (p. ej. ya aprobada)", content = @Content(schema = @Schema(implementation = ErrorResponse.class)))
    @PostMapping("/bank/{bankCode}/authorize")
    public AuthorizeResponse authorize(
            @Parameter(example = "ANDINO") @PathVariable String bankCode,
            @Valid @RequestBody BankAuthorizeRequest request) {
        Transaction tx = bankService.authorize(bankCode, request.transactionId(), request.bankSessionToken(),
                request.approve());
        return new AuthorizeResponse(tx.getId(), tx.getReference(), tx.getStatus(), tx.getStatusReason(),
                tx.getAmount(), PaymentApiController.url("/result/{id}", tx.getId()));
    }
}
