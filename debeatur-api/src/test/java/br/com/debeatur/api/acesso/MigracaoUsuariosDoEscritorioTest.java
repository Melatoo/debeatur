package br.com.debeatur.api.acesso;

import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.Test;
import org.postgresql.ds.PGSimpleDataSource;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.postgresql.PostgreSQLContainer;

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * A V5 sobre um banco que já está em produção: o único usuário de cada escritório vira admin e
 * autor dos cálculos do escritório, e o escritório passa a aceitar outro usuário.
 */
@Testcontainers(disabledWithoutDocker = true)
class MigracaoUsuariosDoEscritorioTest {

    @Container
    private static final PostgreSQLContainer POSTGRES = new PostgreSQLContainer("postgres:18-alpine");

    private static final UUID PADRAO = UsuarioInicial.ESCRITORIO_PADRAO;

    @Test
    void oUsuarioQueJaExistiaViraAdminEAutorDosCalculosDoEscritorio() {
        PGSimpleDataSource banco = new PGSimpleDataSource();
        banco.setUrl(POSTGRES.getJdbcUrl());
        banco.setUser(POSTGRES.getUsername());
        banco.setPassword(POSTGRES.getPassword());
        JdbcClient jdbc = JdbcClient.create(banco);
        Flyway.configure().dataSource(banco).target("4").load().migrate();

        UUID ana = UUID.randomUUID();
        UUID semUsuario = UUID.randomUUID();
        jdbc.sql("INSERT INTO usuario (id, escritorio_id, email, senha) VALUES (?, ?, 'ana@escritorio.adv.br', '{noop}x')")
                .params(ana, PADRAO).update();
        jdbc.sql("INSERT INTO escritorio (id, nome) VALUES (?, 'Sem usuário')").param(semUsuario).update();
        UUID daAna = calculo(jdbc, PADRAO);
        UUID orfao = calculo(jdbc, semUsuario);

        Flyway.configure().dataSource(banco).load().migrate();

        assertThat(jdbc.sql("SELECT papel FROM usuario WHERE id = ?").param(ana).query(String.class).single()).isEqualTo("ADMIN");
        assertThat(jdbc.sql("SELECT ativo FROM usuario WHERE id = ?").param(ana).query(Boolean.class).single()).isTrue();
        assertThat(jdbc.sql("SELECT autor_id FROM calculo WHERE id = ?").param(daAna).query(UUID.class).single()).isEqualTo(ana);
        assertThat(jdbc.sql("SELECT autor_id FROM calculo WHERE id = ?").param(orfao).query(UUID.class).optional()).isEmpty();

        // um segundo usuário no mesmo escritório, que a V3 recusava, e com o papel padrão de membro
        jdbc.sql("INSERT INTO usuario (id, escritorio_id, email, senha) VALUES (?, ?, 'bia@escritorio.adv.br', '{noop}x')")
                .params(UUID.randomUUID(), PADRAO).update();
        assertThat(jdbc.sql("SELECT papel FROM usuario WHERE email = 'bia@escritorio.adv.br'").query(String.class).single())
                .isEqualTo("MEMBRO");
    }

    private static UUID calculo(JdbcClient jdbc, UUID escritorio) {
        UUID id = UUID.randomUUID();
        jdbc.sql("""
                        INSERT INTO calculo (id, escritorio_id, titulo, data_base, regime, total, pedido, criado_em)
                        VALUES (?, ?, 'Cálculo', DATE '2024-10-15', 'Regime', 100.00, '{}'::jsonb, now())
                        """)
                .params(id, escritorio).update();
        return id;
    }
}
