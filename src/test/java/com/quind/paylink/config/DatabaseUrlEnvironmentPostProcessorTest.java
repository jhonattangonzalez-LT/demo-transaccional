package com.quind.paylink.config;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.Map;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

class DatabaseUrlEnvironmentPostProcessorTest {

    @ParameterizedTest
    @ValueSource(strings = {
            "postgresql://usuario:cl%40ve@ep-demo-pooler.us-east-2.aws.example.tech/neondb?sslmode=require&channel_binding=require",
            "  postgresql://usuario:cl%40ve@ep-demo-pooler.us-east-2.aws.example.tech/neondb?sslmode=require&channel_binding=require  ",
            "\"postgresql://usuario:cl%40ve@ep-demo-pooler.us-east-2.aws.example.tech/neondb?sslmode=require&channel_binding=require\"",
            "psql 'postgresql://usuario:cl%40ve@ep-demo-pooler.us-east-2.aws.example.tech/neondb?sslmode=require&channel_binding=require'",
            "postgres postgresql://usuario:cl%40ve@ep-demo-pooler.us-east-2.aws.example.tech/neondb?sslmode=require&channel_binding=require"
    })
    void convertsNeonStyleUrlsToJdbcProperties(String raw) {
        Map<String, Object> props = DatabaseUrlEnvironmentPostProcessor.toDataSourceProperties(raw).orElseThrow();

        assertThat(props.get("spring.datasource.url")).isEqualTo(
                "jdbc:postgresql://ep-demo-pooler.us-east-2.aws.example.tech/neondb?sslmode=require&channelBinding=require");
        assertThat(props.get("spring.datasource.username")).isEqualTo("usuario");
        assertThat(props.get("spring.datasource.password")).isEqualTo("cl@ve");
    }

    @Test
    void ignoresValuesWithoutPostgresUrl() {
        assertThat(DatabaseUrlEnvironmentPostProcessor.toDataSourceProperties("jdbc:h2:mem:x")).isEmpty();
        assertThat(DatabaseUrlEnvironmentPostProcessor.toDataSourceProperties("")).isEmpty();
        assertThat(DatabaseUrlEnvironmentPostProcessor.toDataSourceProperties(null)).isEmpty();
    }
}
