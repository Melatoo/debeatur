package br.com.debeatur.api.calculo;

import br.com.debeatur.api.acesso.UsuarioAutenticado;
import br.com.debeatur.api.serie.SnapshotSeries;
import org.springframework.jdbc.core.JdbcTemplate;
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

/**
 * Tabelas {@code calculo} e {@code calculo_serie}; toda consulta é restrita a um {@link Alcance}:
 * o escritório e, para o membro, os cálculos que ele mesmo salvou.
 */
@Repository
class RepositorioCalculos {

    /** @param processo o número do processo no pedido, ou nulo; não tem coluna própria, sai do pedido */
    record Resumo(UUID id, String titulo, String processo, LocalDate dataBase, String regime, BigDecimal total,
                  Instant criadoEm) {
    }

    /** @param pedidoJson o {@link CalculoRequest} em JSON, como foi gravado */
    record Salvo(Resumo resumo, String pedidoJson, SnapshotSeries snapshot) {
    }

    /**
     * Os cálculos que um usuário enxerga: os do escritório, e só os dele se não for admin.
     *
     * @param autor nulo quando vale todo o escritório
     */
    record Alcance(UUID escritorio, UUID autor) {

        static Alcance de(UsuarioAutenticado usuario) {
            return new Alcance(usuario.escritorio(), usuario.admin() ? null : usuario.id());
        }

        /** A condição do {@code WHERE}, com os parâmetros {@code :escritorio} e {@code :autor}. */
        String condicao() {
            return autor == null ? "escritorio_id = :escritorio" : "escritorio_id = :escritorio AND autor_id = :autor";
        }

        JdbcClient.StatementSpec aplicar(JdbcClient.StatementSpec consulta) {
            consulta = consulta.param("escritorio", escritorio);
            return autor == null ? consulta : consulta.param("autor", autor);
        }
    }

    private static final String SELECT_RESUMO = """
            SELECT id, titulo, NULLIF(btrim(pedido -> 'processo' ->> 'numero'), '') AS processo, data_base, regime,
                   total, criado_em
            FROM calculo
            """;

    private final JdbcClient jdbc;
    /** O {@link JdbcClient} não grava em lote. */
    private final JdbcTemplate lote;

    RepositorioCalculos(JdbcClient jdbc, JdbcTemplate lote) {
        this.jdbc = jdbc;
        this.lote = lote;
    }

    /** @param autor quem salvou; o cálculo fica no escritório dele */
    @Transactional
    public void inserir(UsuarioAutenticado autor, Resumo resumo, String pedidoJson, SnapshotSeries snapshot) {
        jdbc.sql("""
                        INSERT INTO calculo (id, escritorio_id, autor_id, titulo, data_base, regime, total, pedido, criado_em)
                        VALUES (?, ?, ?, ?, ?, ?, ?, CAST(? AS JSONB), ?)
                        """)
                .params(resumo.id(), autor.escritorio(), autor.id(), resumo.titulo(), resumo.dataBase(), resumo.regime(),
                        resumo.total(), pedidoJson, resumo.criadoEm().atOffset(ZoneOffset.UTC))
                .update();
        List<Object[]> linhas = snapshot.valores().entrySet().stream()
                .flatMap(serie -> serie.getValue().entrySet().stream()
                        .map(mes -> new Object[] {resumo.id(), serie.getKey(), mes.getKey().atDay(1), mes.getValue()}))
                .toList();
        lote.batchUpdate("INSERT INTO calculo_serie (calculo_id, serie, mes, valor) VALUES (?, ?, ?, ?)", linhas);
    }

    /** Do mais recente para o mais antigo. */
    List<Resumo> listar(Alcance alcance) {
        return alcance.aplicar(jdbc.sql(SELECT_RESUMO + "WHERE " + alcance.condicao() + " ORDER BY criado_em DESC, id"))
                .query(RepositorioCalculos::resumo)
                .list();
    }

    @Transactional(readOnly = true)
    public Optional<Salvo> buscar(Alcance alcance, UUID id) {
        return alcance.aplicar(jdbc.sql("""
                        SELECT id, titulo, NULLIF(btrim(pedido -> 'processo' ->> 'numero'), '') AS processo,
                               data_base, regime, total,
                               criado_em, pedido::text AS pedido
                        FROM calculo WHERE id = :id AND\s""" + alcance.condicao()))
                .param("id", id)
                .query((rs, linha) -> Map.entry(resumo(rs, linha), rs.getString("pedido")))
                .optional()
                .map(linha -> new Salvo(linha.getKey(), linha.getValue(), snapshot(id, linha.getKey().criadoEm())));
    }

    /** Troca só o título: o pedido, o total e o snapshot ficam como foram salvos. */
    @Transactional
    public Optional<Resumo> renomear(Alcance alcance, UUID id, String titulo) {
        int alterados = alcance.aplicar(jdbc.sql("UPDATE calculo SET titulo = :titulo WHERE id = :id AND " + alcance.condicao()))
                .param("titulo", titulo)
                .param("id", id)
                .update();
        if (alterados == 0) {
            return Optional.empty();
        }
        return jdbc.sql(SELECT_RESUMO + "WHERE id = ?")
                .param(id)
                .query(RepositorioCalculos::resumo)
                .optional();
    }

    /** Apaga o cálculo e, pelo {@code ON DELETE CASCADE}, o snapshot dele. */
    boolean excluir(Alcance alcance, UUID id) {
        return alcance.aplicar(jdbc.sql("DELETE FROM calculo WHERE id = :id AND " + alcance.condicao()))
                .param("id", id)
                .update() > 0;
    }

    private SnapshotSeries snapshot(UUID calculo, Instant criadoEm) {
        Map<String, Map<YearMonth, BigDecimal>> valores = new HashMap<>();
        jdbc.sql("SELECT serie, mes, valor FROM calculo_serie WHERE calculo_id = ?")
                .param(calculo)
                .query(rs -> {
                    valores.computeIfAbsent(rs.getString("serie"), s -> new HashMap<>())
                            .put(YearMonth.from(rs.getObject("mes", LocalDate.class)), rs.getBigDecimal("valor"));
                });
        return new SnapshotSeries(valores, criadoEm);
    }

    private static Resumo resumo(ResultSet rs, int linha) throws SQLException {
        return new Resumo(rs.getObject("id", UUID.class), rs.getString("titulo"), rs.getString("processo"),
                rs.getObject("data_base", LocalDate.class), rs.getString("regime"), rs.getBigDecimal("total"),
                rs.getObject("criado_em", OffsetDateTime.class).toInstant());
    }
}
