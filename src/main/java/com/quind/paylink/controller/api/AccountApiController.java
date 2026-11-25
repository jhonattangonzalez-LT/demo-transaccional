package com.quind.paylink.controller.api;

import com.quind.paylink.dto.BalanceResponse;
import com.quind.paylink.dto.ErrorResponse;
import com.quind.paylink.dto.MovementResponse;
import com.quind.paylink.service.AccountService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.tags.Tag;
import java.util.List;
import java.util.UUID;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/** Verificación de saldos y movimientos (solo para el mockup; un sistema real lo protegería). */
@Tag(name = "3. Cuentas", description = "Verificación de saldos y movimientos (abierto solo por ser mockup)")
@RestController
@RequestMapping("/api/v1/accounts")
public class AccountApiController {

    private final AccountService accountService;

    public AccountApiController(AccountService accountService) {
        this.accountService = accountService;
    }

    @Operation(summary = "Consultar saldo de una cuenta")
    @ApiResponse(responseCode = "200", description = "Saldo actual")
    @ApiResponse(responseCode = "404", description = "Cuenta inexistente", content = @Content(schema = @Schema(implementation = ErrorResponse.class)))
    @GetMapping("/{number}/balance")
    public BalanceResponse balance(@Parameter(example = "1000000001") @PathVariable String number) {
        return BalanceResponse.from(accountService.getByNumber(number));
    }

    @Operation(summary = "Listar movimientos (DEBIT / CREDIT) de una cuenta",
            description = "Filtro opcional por transacción.")
    @ApiResponse(responseCode = "200", description = "Movimientos en orden cronológico")
    @ApiResponse(responseCode = "404", description = "Cuenta inexistente", content = @Content(schema = @Schema(implementation = ErrorResponse.class)))
    @GetMapping("/{number}/movements")
    public List<MovementResponse> movements(@Parameter(example = "1000000001") @PathVariable String number,
                                            @RequestParam(required = false) UUID transactionId) {
        return accountService.getMovements(number, transactionId).stream().map(MovementResponse::from).toList();
    }
}
