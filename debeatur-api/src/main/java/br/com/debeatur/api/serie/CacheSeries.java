package br.com.debeatur.api.serie;

import br.com.debeatur.core.indice.Indice;
import br.com.debeatur.core.indice.SalarioMinimoEmMemoria;
import br.com.debeatur.core.indice.SerieIndicesEmMemoria;
import br.com.debeatur.indices.SgsClient;
import br.com.debeatur.indices.SgsException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.InstantSource;
import java.time.YearMonth;
import java.time.ZoneId;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

/**
 * Séries do SGS guardadas no banco. O cálculo lê do cache, e o SGS só é consultado quando o
 * cache não cobre o período pedido ou quando falta um mês recente que já pode ter saído.
 * <p>
 * O cache acompanha as revisões do BCB ({@link #atualizarTodas()}); o que fixa os valores de um
 * cálculo já feito é o snapshot dele, e não o cache.
 */
@Service
public class CacheSeries implements FonteSeries {

    /** Chave do salário mínimo em {@code serie_mensal}; os índices usam o nome do {@link Indice}. */
    static final String SALARIO_MINIMO = "SALARIO_MINIMO";

    /** Fuso que define o mês corrente: o do contêiner (UTC) viraria o mês três horas antes. */
    static final ZoneId FUSO = ZoneId.of("America/Sao_Paulo");

    private static final Logger LOG = LoggerFactory.getLogger(CacheSeries.class);

    @FunctionalInterface
    private interface ConsultaSgs {
        Map<YearMonth, BigDecimal> buscar(YearMonth de, YearMonth ate);
    }

    private final SgsClient sgs;
    private final RepositorioSeries repositorio;
    private final PropriedadesSeries propriedades;
    private final InstantSource relogio;

    public CacheSeries(SgsClient sgs, RepositorioSeries repositorio, PropriedadesSeries propriedades,
                       InstantSource relogio) {
        this.sgs = sgs;
        this.repositorio = repositorio;
        this.propriedades = propriedades;
        this.relogio = relogio;
    }

    @Override
    public SerieIndicesEmMemoria carregar(Set<Indice> indices, YearMonth de, YearMonth ate) {
        SerieIndicesEmMemoria serie = new SerieIndicesEmMemoria();
        for (Indice indice : indices) {
            serie.comTodos(indice, buscar(indice.name(), de, ate, (d, a) -> sgs.buscar(indice, d, a)));
        }
        return serie;
    }

    @Override
    public SalarioMinimoEmMemoria carregarSalarioMinimo(YearMonth de, YearMonth ate) {
        return new SalarioMinimoEmMemoria().comTodos(buscar(SALARIO_MINIMO, de, ate, sgs::buscarSalarioMinimo));
    }

    /**
     * Relê no SGS os últimos {@link PropriedadesSeries#mesesRevisao()} meses de cada série já
     * consultada, até o mês corrente. Uma série que falha não impede as outras.
     */
    public void atualizarTodas() {
        YearMonth mesAtual = mesAtual();
        for (RepositorioSeries.Consulta consulta : repositorio.consultas()) {
            // começa no fim já consultado, se ele for mais antigo que a janela de revisão, para não deixar buraco
            YearMonth janela = mesAtual.minusMonths(propriedades.mesesRevisao());
            YearMonth de = maior(consulta.inicio(), menor(janela, consulta.fim()));
            YearMonth ate = maior(consulta.fim(), mesAtual);
            try {
                consultarSgs(consulta.serie(), de, ate, consultaDa(consulta.serie()));
            } catch (SgsException | IllegalArgumentException e) {
                LOG.warn("Falha ao atualizar a série {} no SGS: {}", consulta.serie(), e.getMessage());
            }
        }
    }

    private Map<YearMonth, BigDecimal> buscar(String serie, YearMonth de, YearMonth ate, ConsultaSgs sgs) {
        Optional<RepositorioSeries.Consulta> consulta = repositorio.consulta(serie);
        if (consulta.isEmpty() || !cobre(consulta.get(), de)) {
            YearMonth inicio = consulta.map(c -> menor(c.inicio(), de)).orElse(de);
            YearMonth fim = consulta.map(c -> maior(c.fim(), ate)).orElse(ate);
            consultarSgs(serie, inicio, fim, sgs);
        } else if (precisaReconsultar(consulta.get(), ate)) {
            RepositorioSeries.Consulta c = consulta.get();
            try {
                consultarSgs(serie, c.inicio(), maior(c.fim(), ate), sgs);
            } catch (SgsException e) {
                // os meses que o cache já tem continuam valendo; o que faltar o motor acusa
                LOG.warn("SGS indisponível ao reconsultar {}; usando o cache: {}", serie, e.getMessage());
            }
        }
        return repositorio.ler(serie, de, ate);
    }

    /** Se o cache já consultou o SGS desde o mês {@code de}. */
    private static boolean cobre(RepositorioSeries.Consulta consulta, YearMonth de) {
        return !de.isBefore(consulta.inicio());
    }

    /**
     * Reconsulta quando o período vai além do intervalo consultado, ou quando o último mês pedido
     * ainda não tinha saído e a última consulta já tem mais que {@link PropriedadesSeries#reconsulta()}.
     */
    private boolean precisaReconsultar(RepositorioSeries.Consulta consulta, YearMonth ate) {
        if (ate.isAfter(consulta.fim())) {
            return true;
        }
        boolean faltaMesRecente = consulta.ultimoMesComValor() == null || ate.isAfter(consulta.ultimoMesComValor());
        return faltaMesRecente && consulta.consultadoEm().plus(propriedades.reconsulta()).isBefore(relogio.instant());
    }

    private void consultarSgs(String serie, YearMonth de, YearMonth ate, ConsultaSgs sgs) {
        Map<YearMonth, BigDecimal> valores = sgs.buscar(de, ate);
        Instant agora = relogio.instant();
        repositorio.gravar(serie, valores, agora);
        repositorio.registrarConsulta(serie, de, ate, agora);
    }

    private ConsultaSgs consultaDa(String serie) {
        if (SALARIO_MINIMO.equals(serie)) {
            return sgs::buscarSalarioMinimo;
        }
        Indice indice = Indice.valueOf(serie);
        return (de, ate) -> sgs.buscar(indice, de, ate);
    }

    private YearMonth mesAtual() {
        return YearMonth.from(relogio.instant().atZone(FUSO));
    }

    private static YearMonth menor(YearMonth a, YearMonth b) {
        return a.isBefore(b) ? a : b;
    }

    private static YearMonth maior(YearMonth a, YearMonth b) {
        return a.isAfter(b) ? a : b;
    }
}
