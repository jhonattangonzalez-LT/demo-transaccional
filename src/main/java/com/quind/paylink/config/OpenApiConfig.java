package com.quind.paylink.config;

import io.swagger.v3.oas.models.Components;
import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.info.Info;
import io.swagger.v3.oas.models.security.SecurityScheme;
import io.swagger.v3.oas.models.servers.Server;
import java.util.List;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/** Documentación OpenAPI / Swagger UI de la API REST de PayLink. */
@Configuration
public class OpenApiConfig {

    public static final String API_KEY_SCHEME = "merchantApiKey";

    @Bean
    public OpenAPI payLinkOpenApi() {
        return new OpenAPI()
                .info(new Info()
                        .title("PayLink API")
                        .version("v1")
                        .description("""
                                Pasarela de pagos **simulada** (mockup educativo) por débito a cuenta bancaria.
                                Todos los datos son ficticios.

                                Flujo: crear orden → listar bancos → iniciar pago → autenticar en el banco →
                                autorizar/rechazar → verificar estado, saldos y movimientos.

                                API key del comercio demo: `demo-api-key-123`. Clave simulada de los pagadores: `1234`.
                                """))
                // Sin servidor fijo: Swagger usa el host desde el que se abre (local o Vercel)
                .servers(List.of(new Server().url("/").description("Servidor actual")))
                .components(new Components().addSecuritySchemes(API_KEY_SCHEME, new SecurityScheme()
                        .type(SecurityScheme.Type.APIKEY)
                        .in(SecurityScheme.In.HEADER)
                        .name("X-Api-Key")
                        .description("API key del comercio (solo requerida para crear órdenes)")));
    }
}
