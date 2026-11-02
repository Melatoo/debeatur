package br.com.debeatur.api.serie;

import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Repository;

import java.math.BigDecimal;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.Instant;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.time.YearMonth;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.TreeMap;

/** Tabelas {@code serie_mensal} e {@code serie_consulta}; o mês é gravado como o seu dia 1º. */
@Repository
class RepositorioSeries {

    /**
     * Intervalo de uma série já consultado no SGS.
     *
     * @param ultimoMesComValor último mês com valor gravado; nulo se o SGS não devolveu nenhum
     */
    record Consulta(String serie, YearMonth inicio, YearMonth fim, Instant consultadoEm, YearMonth ultimoMesComValor) {
    }

    private final JdbcClient jdbc;

    RepositorioSeries(JdbcClient jdbc) {
        this.jdbc = jdbc;
    }

    Map<YearMonth, BigDecimal> ler(String serie, YearMonth de, YearMonth ate) {
        Map<YearMonth, BigDecimal> valores = new TreeMap<>();
        jdbc.sql("SELECT mes, valor FROM serie_mensal WHERE serie = ? AND mes BETWEEN ? AND ?")
                .params(serie, dia1(de), dia1(ate))
                .query((rs, i) -> Map.entry(YearMonth.from(rs.getObject("mes", LocalDate.class)), rs.getBigDecimal("valor")))
                .list()
                .forEach(e -> valores.put(e.getKey(), e.getValue()));
        return valores;
    }

    /** Grava os valores, substituindo os que o SGS revisou. */
    void gravar(String serie, Map<YearMonth, BigDecimal> valores, Instant agora) {
        valores.forEach((mes, valor) -> jdbc.sql("""
                        INSERT INTO serie_mensal (serie, mes, valor, atualizado_em) VALUES (?, ?, ?, ?)
                        ON CONFLICT (serie, mes) DO UPDATE SET valor = EXCLUDED.valor, atualizado_em = EXCLUDED.atualizado_em
                        WHERE serie_mensal.valor IS DISTINCT FROM EXCLUDED.valor
                        """)
                .params(serie, dia1(mes), valor, utc(agora))
                .update());
    }

    Optional<Consulta> consulta(String serie) {
        return jdbc.sql(SELECT_CONSULTA + " WHERE c.serie = ?").param(serie).query(RepositorioSeries::consulta).optional();
    }

    List<Consulta> consultas() {
        return jdbc.sql(SELECT_CONSULTA + " ORDER BY c.serie").query(RepositorioSeries::consulta).list();
    }

    /** Amplia o intervalo consultado da série, que nunca encolhe. */
    void registrarConsulta(String serie, YearMonth inicio, YearMonth fim, Instant agora) {
        jdbc.sql("""
                        INSERT INTO serie_consulta (serie, inicio, fim, consultado_em) VALUES (?, ?, ?, ?)
                        ON CONFLICT (serie) DO UPDATE SET
                            inicio = LEAST(serie_consulta.inicio, EXCLUDED.inicio),
                            fim = GREATEST(serie_consulta.fim, EXCLUDED.fim),
                            consultado_em = EXCLUDED.consultado_em
                        """)
                .params(serie, dia1(inicio), dia1(fim), utc(agora))
                .update();
    }

    private static final String SELECT_CONSULTA = """
            SELECT c.serie, c.inicio, c.fim, c.consultado_em,
                   (SELECT max(m.mes) FROM serie_mensal m WHERE m.serie = c.serie) AS ultimo_mes
            FROM serie_consulta c""";

    private static Consulta consulta(ResultSet rs, int linha) throws SQLException {
        LocalDate ultimo = rs.getObject("ultimo_mes", LocalDate.class);
        return new Consulta(rs.getString("serie"),
                YearMonth.from(rs.getObject("inicio", LocalDate.class)),
                YearMonth.from(rs.getObject("fim", LocalDate.class)),
                rs.getObject("consultado_em", OffsetDateTime.class).toInstant(),
                ultimo == null ? null : YearMonth.from(ultimo));
    }

    private static LocalDate dia1(YearMonth mes) {
        return mes.atDay(1);
    }

    private static OffsetDateTime utc(Instant instante) {
        return instante.atOffset(ZoneOffset.UTC);
    }
}
