package br.com.debeatur.api;

import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.context.annotation.Bean;
import org.testcontainers.postgresql.PostgreSQLContainer;

/**
 * PostgreSQL em contêiner, na mesma versão do {@code compose.yaml}. Os testes que o importam
 * levam {@code @Testcontainers(disabledWithoutDocker = true)}: sem Docker eles são pulados, e
 * o CI, que tem Docker, sempre os roda.
 */
@TestConfiguration(proxyBeanMethods = false)
public class PostgresDeTeste {

    @Bean
    @ServiceConnection
    PostgreSQLContainer postgres() {
        return new PostgreSQLContainer("postgres:18-alpine");
    }
}
