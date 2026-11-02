package br.com.debeatur.api.calculo;

import br.com.debeatur.api.serie.SnapshotSeries;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.Instant;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.time.YearMonth;
import java.time.ZoneOffset;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

/** Tabelas {@code calculo} e {@code calculo_serie}; toda consulta é restrita a um escritório. */
@Repository
class RepositorioCalculos {

    record Resumo(UUID id, String titulo, LocalDate dataBase, String regime, BigDecimal total, Instant criadoEm) {
    }

    /** @param pedidoJson o {@link CalculoRequest} em JSON, como foi gravado */
    record Salvo(Resumo resumo, String pedidoJson, SnapshotSeries snapshot) {
    }

    private static final String SELECT_RESUMO = "SELECT id, titulo, data_base, regime, total, criado_em FROM calculo";

    private final JdbcClient jdbc;

    RepositorioCalculos(JdbcClient jdbc) {
        this.jdbc = jdbc;
    }

    @Transactional
    public void inserir(UUID escritorio, Resumo resumo, String pedidoJson, SnapshotSeries snapshot) {
        jdbc.sql("""
                        INSERT INTO calculo (id, escritorio_id, titulo, data_base, regime, total, pedido, criado_em)
                        VALUES (?, ?, ?, ?, ?, ?, CAST(? AS JSONB), ?)
                        """)
                .params(resumo.id(), escritorio, resumo.titulo(), resumo.dataBase(), resumo.regime(), resumo.total(),
                        pedidoJson, resumo.criadoEm().atOffset(ZoneOffset.UTC))
                .update();
        snapshot.valores().forEach((serie, meses) -> meses.forEach((mes, valor) ->
                jdbc.sql("INSERT INTO calculo_serie (calculo_id, serie, mes, valor) VALUES (?, ?, ?, ?)")
                        .params(resumo.id(), serie, mes.atDay(1), valor)
                        .update()));
    }

    /** Do mais recente para o mais antigo. */
    List<Resumo> listar(UUID escritorio) {
        return jdbc.sql(SELECT_RESUMO + " WHERE escritorio_id = ? ORDER BY criado_em DESC, id")
                .param(escritorio)
                .query(RepositorioCalculos::resumo)
                .list();
    }

    @Transactional(readOnly = true)
    public Optional<Salvo> buscar(UUID escritorio, UUID id) {
        return jdbc.sql("""
                        SELECT id, titulo, data_base, regime, total, criado_em, pedido::text AS pedido
                        FROM calculo WHERE escritorio_id = ? AND id = ?
                        """)
                .params(escritorio, id)
                .query((rs, linha) -> new Salvo(resumo(rs, linha), rs.getString("pedido"), snapshot(id)))
                .optional();
    }

    private SnapshotSeries snapshot(UUID calculo) {
        Map<String, Map<YearMonth, BigDecimal>> valores = new HashMap<>();
        jdbc.sql("SELECT serie, mes, valor FROM calculo_serie WHERE calculo_id = ?")
                .param(calculo)
                .query(rs -> {
                    valores.computeIfAbsent(rs.getString("serie"), s -> new HashMap<>())
                            .put(YearMonth.from(rs.getObject("mes", LocalDate.class)), rs.getBigDecimal("valor"));
                });
        return new SnapshotSeries(valores);
    }

    private static Resumo resumo(ResultSet rs, int linha) throws SQLException {
        return new Resumo(rs.getObject("id", UUID.class), rs.getString("titulo"),
                rs.getObject("data_base", LocalDate.class), rs.getString("regime"), rs.getBigDecimal("total"),
                rs.getObject("criado_em", OffsetDateTime.class).toInstant());
    }
}
