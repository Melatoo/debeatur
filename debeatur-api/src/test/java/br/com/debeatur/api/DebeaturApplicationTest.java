package br.com.debeatur.api;

import br.com.debeatur.api.serie.CacheSeries;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.testcontainers.junit.jupiter.Testcontainers;

import static org.assertj.core.api.Assertions.assertThat;

/** A aplicação sobe contra o PostgreSQL, com as migrações da Flyway aplicadas. */
@SpringBootTest(properties = "debeatur.series.atualizacao=-")
@Import(PostgresDeTeste.class)
@Testcontainers(disabledWithoutDocker = true)
class DebeaturApplicationTest {

    @Autowired
    private JdbcClient jdbc;

    @Autowired
    private CacheSeries cache;

    @Test
    void sobeComAsMigracoesAplicadas() {
        assertThat(cache).isNotNull();
        assertThat(jdbc.sql("SELECT count(*) FROM serie_mensal").query(Long.class).single()).isZero();
        assertThat(jdbc.sql("SELECT count(*) FROM flyway_schema_history WHERE success").query(Long.class).single())
                .isPositive();
    }
}
