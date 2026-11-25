package com.quind.paylink.config;

import java.net.URI;
import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.Map;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.env.EnvironmentPostProcessor;
import org.springframework.core.env.ConfigurableEnvironment;
import org.springframework.core.env.MapPropertySource;

/**
 * Permite configurar la BD con la variable {@code DATABASE_URL} en el formato que entregan Neon / Vercel
 * ({@code postgresql://usuario:clave@host/db?sslmode=require}), convirtiéndola a las propiedades JDBC de Spring.
 * Si se define {@code SPRING_DATASOURCE_URL} explícitamente, esta clase no hace nada.
 */
public class DatabaseUrlEnvironmentPostProcessor implements EnvironmentPostProcessor {

    @Override
    public void postProcessEnvironment(ConfigurableEnvironment environment, SpringApplication application) {
        String databaseUrl = environment.getProperty("DATABASE_URL");
        if (databaseUrl == null || databaseUrl.isBlank() || environment.containsProperty("SPRING_DATASOURCE_URL")
                || !databaseUrl.matches("^postgres(ql)?://.*")) {
            return;
        }
        URI uri = URI.create(databaseUrl.trim());
        Map<String, Object> props = new HashMap<>();
        props.put("spring.datasource.url", "jdbc:postgresql://" + uri.getHost()
                + (uri.getPort() > 0 ? ":" + uri.getPort() : "") + uri.getPath() + jdbcQuery(uri.getRawQuery()));
        props.put("spring.datasource.driver-class-name", "org.postgresql.Driver");
        String userInfo = uri.getRawUserInfo();
        if (userInfo != null) {
            int sep = userInfo.indexOf(':');
            props.put("spring.datasource.username", decode(sep < 0 ? userInfo : userInfo.substring(0, sep)));
            if (sep >= 0) {
                props.put("spring.datasource.password", decode(userInfo.substring(sep + 1)));
            }
        }
        environment.getPropertySources().addFirst(new MapPropertySource("databaseUrl", props));
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
