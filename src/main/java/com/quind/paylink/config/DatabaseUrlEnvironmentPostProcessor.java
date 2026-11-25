package com.quind.paylink.config;

import java.net.URI;
import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.env.EnvironmentPostProcessor;
import org.springframework.core.env.ConfigurableEnvironment;
import org.springframework.core.env.MapPropertySource;

/**
 * Permite configurar la BD con la cadena que entregan Neon / Vercel
 * ({@code postgresql://usuario:clave@host/db?sslmode=require}) convirtiéndola a las propiedades JDBC de Spring.
 * <p>
 * Busca, en orden, {@code DATABASE_URL}, {@code POSTGRES_URL} y {@code NEON_DATABASE_URL}. Es tolerante con lo que
 * suele copiarse del panel de Neon: comillas, espacios o el prefijo {@code psql '...'}.
 * Si se define {@code SPRING_DATASOURCE_URL} explícitamente, esta clase no hace nada.
 */
public class DatabaseUrlEnvironmentPostProcessor implements EnvironmentPostProcessor {

    static final List<String> CANDIDATE_VARIABLES = List.of("DATABASE_URL", "POSTGRES_URL", "NEON_DATABASE_URL");
    private static final Pattern POSTGRES_URL = Pattern.compile("postgres(?:ql)?://[^\\s'\"]+");

    @Override
    public void postProcessEnvironment(ConfigurableEnvironment environment, SpringApplication application) {
        if (environment.containsProperty("SPRING_DATASOURCE_URL")) {
            return;
        }
        Optional<Map<String, Object>> props = CANDIDATE_VARIABLES.stream()
                .map(environment::getProperty)
                .map(DatabaseUrlEnvironmentPostProcessor::toDataSourceProperties)
                .flatMap(Optional::stream)
                .findFirst();

        if (props.isPresent()) {
            environment.getPropertySources().addFirst(new MapPropertySource("databaseUrl", props.get()));
        } else if (Arrays.asList(environment.getActiveProfiles()).contains("vercel")) {
            // El logging aún no está inicializado en esta fase: se escribe directo a stderr para que se vea en los logs.
            System.err.println("[PayLink] ERROR: perfil 'vercel' activo pero no se encontró una URL de PostgreSQL válida en "
                    + CANDIDATE_VARIABLES + " ni SPRING_DATASOURCE_URL. Configure DATABASE_URL en Vercel "
                    + "(Settings > Environment Variables) y vuelva a desplegar.");
        }
    }

    /** Convierte una cadena postgresql://... (posiblemente con ruido alrededor) en propiedades spring.datasource.*. */
    static Optional<Map<String, Object>> toDataSourceProperties(String raw) {
        if (raw == null) {
            return Optional.empty();
        }
        Matcher m = POSTGRES_URL.matcher(raw);
        if (!m.find()) {
            return Optional.empty();
        }
        URI uri = URI.create(m.group());
        if (uri.getHost() == null) {
            return Optional.empty();
        }
        Map<String, Object> props = new HashMap<>();
        props.put("spring.datasource.url", "jdbc:postgresql://" + uri.getHost()
                + (uri.getPort() > 0 ? ":" + uri.getPort() : "") + uri.getRawPath() + jdbcQuery(uri.getRawQuery()));
        props.put("spring.datasource.driver-class-name", "org.postgresql.Driver");
        String userInfo = uri.getRawUserInfo();
        if (userInfo != null) {
            int sep = userInfo.indexOf(':');
            props.put("spring.datasource.username", decode(sep < 0 ? userInfo : userInfo.substring(0, sep)));
            if (sep >= 0) {
                props.put("spring.datasource.password", decode(userInfo.substring(sep + 1)));
            }
        }
        return Optional.of(props);
    }

    /** Conserva los parámetros de la URL, traduciendo channel_binding al nombre que usa el driver JDBC. */
    private static String jdbcQuery(String rawQuery) {
        if (rawQuery == null || rawQuery.isBlank()) {
            return "?sslmode=require";
        }
        return "?" + rawQuery.replace("channel_binding=", "channelBinding=");
    }

    private static String decode(String value) {
        return URLDecoder.decode(value, StandardCharsets.UTF_8);
    }
}
