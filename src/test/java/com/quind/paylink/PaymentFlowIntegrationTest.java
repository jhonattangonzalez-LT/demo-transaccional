package com.quind.paylink;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.jayway.jsonpath.JsonPath;
import java.math.BigDecimal;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

/** Camino feliz completo contra la aplicación real (H2 + data.sql), verificando saldos finales. */
@SpringBootTest
@AutoConfigureMockMvc
class PaymentFlowIntegrationTest {

    private static final String API_KEY = "demo-api-key-123";
    private static final String PAYER = "1000000001";
    private static final String MERCHANT = "9000000001";

    @Autowired
    private MockMvc mvc;

    @Test
    void happyPath_debitsPayerAndCreditsMerchant() throws Exception {
        BigDecimal payerBefore = balance(PAYER);
        BigDecimal merchantBefore = balance(MERCHANT);
        BigDecimal amount = new BigDecimal("150000.00");

        // 1. Comercio crea la orden
        String created = mvc.perform(post("/api/v1/payments")
                        .header("X-Api-Key", API_KEY)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"amount": 150000.00, "description": "Prueba integración",
                                 "returnUrl": "https://tienda-demo.example/retorno"}
                                """))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.status").value("CREATED"))
                .andExpect(jsonPath("$.checkoutUrl").exists())
                .andReturn().getResponse().getContentAsString();
        String txId = JsonPath.read(created, "$.transactionId");
        String reference = JsonPath.read(created, "$.reference");

        // 2. Bancos
        mvc.perform(get("/api/v1/banks")).andExpect(status().isOk()).andExpect(jsonPath("$.length()").value(3));

        // 3. Pagador inicia el pago
        mvc.perform(post("/api/v1/payments/{id}/start", txId)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"bankCode": "ANDINO", "personType": "NATURAL",
                                 "documentType": "CC", "documentNumber": "1000000001"}
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("PENDING"))
                .andExpect(jsonPath("$.bankRedirectUrl").exists());

        // 4. Banco autentica
        String auth = mvc.perform(post("/api/v1/bank/ANDINO/authenticate")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"transactionId": "%s", "documentType": "CC",
                                 "documentNumber": "1000000001", "password": "1234"}
                                """.formatted(txId)))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();
        String token = JsonPath.read(auth, "$.bankSessionToken");

        // 5. Pagador aprueba
        String authorizeBody = """
                {"transactionId": "%s", "bankSessionToken": "%s", "approve": true}
                """.formatted(txId, token);
        mvc.perform(post("/api/v1/bank/ANDINO/authorize")
                        .contentType(MediaType.APPLICATION_JSON).content(authorizeBody))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("APPROVED"));

        // Idempotencia: no se puede volver a procesar
        mvc.perform(post("/api/v1/bank/ANDINO/authorize")
                        .contentType(MediaType.APPLICATION_JSON).content(authorizeBody))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.status").value(409))
                .andExpect(jsonPath("$.path").value("/api/v1/bank/ANDINO/authorize"));

        // 6. Verificación
        mvc.perform(get("/api/v1/payments/{id}", txId))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("APPROVED"));
        mvc.perform(get("/api/v1/payments/reference/{ref}", reference))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.transactionId").value(txId));

        assertThat(balance(PAYER)).isEqualByComparingTo(payerBefore.subtract(amount));
        assertThat(balance(MERCHANT)).isEqualByComparingTo(merchantBefore.add(amount));

        mvc.perform(get("/api/v1/accounts/{n}/movements", PAYER).param("transactionId", txId))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(1))
                .andExpect(jsonPath("$[0].type").value("DEBIT"));
        mvc.perform(get("/api/v1/accounts/{n}/movements", MERCHANT).param("transactionId", txId))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].type").value("CREDIT"));

        // 7. Comprobante web
        mvc.perform(get("/result/{id}", txId)).andExpect(status().isOk());
    }

    private BigDecimal balance(String account) throws Exception {
        String body = mvc.perform(get("/api/v1/accounts/{n}/balance", account))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();
        return new BigDecimal(JsonPath.parse(body).read("$.balance").toString());
    }
}
