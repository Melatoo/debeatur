package br.com.debeatur.api.serie;

import br.com.debeatur.api.PostgresDeTeste;
import br.com.debeatur.core.Dinheiro;
import br.com.debeatur.core.indice.Indice;
import br.com.debeatur.core.indice.IndiceIndisponivelException;
import br.com.debeatur.core.indice.SerieIndicesEmMemoria;
import br.com.debeatur.indices.SgsClient;
import br.com.debeatur.indices.SgsException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.jdbc.test.autoconfigure.AutoConfigureTestDatabase;
import org.springframework.boot.jdbc.test.autoconfigure.JdbcTest;
import org.springframework.context.annotation.Import;
import org.testcontainers.junit.jupiter.Testcontainers;

import java.math.BigDecimal;
import java.time.Duration;
import java.time.Instant;
import java.time.InstantSource;
import java.time.YearMonth;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/** Cache das séries no PostgreSQL, com o SGS simulado. Os valores são fictícios. */
@JdbcTest
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@Import({PostgresDeTeste.class, RepositorioSeries.class})
@Testcontainers(disabledWithoutDocker = true)
class CacheSeriesTest {

    private static final YearMonth JAN = YearMonth.of(2024, 1);
    private static final YearMonth FEV = YearMonth.of(2024, 2);
    private static final YearMonth MAR = YearMonth.of(2024, 3);
    private static final YearMonth ABR = YearMonth.of(2024, 4);
    private static final YearMonth MAI = YearMonth.of(2024, 5);

    @Autowired
    private RepositorioSeries repositorio;

    private final SgsClient sgs = mock(SgsClient.class);
    private Instant agora = Instant.parse("2024-04-05T12:00:00Z");
    private CacheSeries cache;

    @BeforeEach
    void criarCache() {
        InstantSource relogio = () -> agora;
        cache = new CacheSeries(sgs, repositorio, new PropriedadesSeries(Duration.ofHours(1), 24, "-"), relogio);
    }

    private static Map<YearMonth, BigDecimal> valores(Object... mesEValor) {
        Map<YearMonth, BigDecimal> valores = new TreeMap<>();
        for (int i = 0; i < mesEValor.length; i += 2) {
            valores.put((YearMonth) mesEValor[i], new BigDecimal((String) mesEValor[i + 1]));
        }
        return valores;
    }

    private SerieIndicesEmMemoria ipca(YearMonth de, YearMonth ate) {
        return cache.carregar(Set.of(Indice.IPCA), de, ate);
    }

    @Test
    void primeiraConsultaVaiAoSgsEAsSeguintesLeemDoBanco() {
        when(sgs.buscar(Indice.IPCA, JAN, MAR)).thenReturn(valores(JAN, "0.42", FEV, "0.83", MAR, "0.16"));

        ipca(JAN, MAR);
        SerieIndicesEmMemoria serie = ipca(FEV, MAR);

        assertThat(serie.variacaoMensal(Indice.IPCA, FEV)).isEqualByComparingTo("0.83");
        assertThat(serie.variacaoMensal(Indice.IPCA, MAR)).isEqualByComparingTo("0.16");
        assertThatThrownBy(() -> serie.variacaoMensal(Indice.IPCA, JAN))
                .as("só os meses pedidos").isInstanceOf(IndiceIndisponivelException.class);
        verify(sgs, times(1)).buscar(any(), any(), any());
    }

    @Test
    void guardaOValorComoOSgsPublicou() {
        when(sgs.buscarSalarioMinimo(JAN, JAN)).thenReturn(valores(JAN, "1412.00"));
        when(sgs.buscar(Indice.TAXA_LEGAL, JAN, JAN)).thenReturn(valores(JAN, "0.605306"));

        cache.carregarSalarioMinimo(JAN, JAN);
        cache.carregar(Set.of(Indice.TAXA_LEGAL), JAN, JAN);

        assertThat(repositorio.ler(CacheSeries.SALARIO_MINIMO, JAN, JAN)).containsEntry(JAN, new BigDecimal("1412.00"));
        assertThat(repositorio.ler("TAXA_LEGAL", JAN, JAN)).containsEntry(JAN, new BigDecimal("0.605306"));
        assertThat(cache.carregarSalarioMinimo(JAN, JAN).vigente(JAN)).isEqualTo(Dinheiro.de("1412.00"));
        verify(sgs, times(1)).buscarSalarioMinimo(any(), any());
    }

    @Test
    void periodoAnteriorAoConsultadoAmpliaAConsulta() {
        when(sgs.buscar(Indice.IPCA, MAR, ABR)).thenReturn(valores(MAR, "0.16", ABR, "0.38"));
        when(sgs.buscar(Indice.IPCA, JAN, ABR)).thenReturn(valores(JAN, "0.42", FEV, "0.83", MAR, "0.16", ABR, "0.38"));

        ipca(MAR, ABR);
        SerieIndicesEmMemoria serie = ipca(JAN, MAR);

        assertThat(serie.variacaoMensal(Indice.IPCA, JAN)).isEqualByComparingTo("0.42");
        verify(sgs).buscar(Indice.IPCA, JAN, ABR);
    }

    @Test
    void periodoAlemDoConsultadoVaiAoSgsNaHora() {
        when(sgs.buscar(Indice.IPCA, JAN, FEV)).thenReturn(valores(JAN, "0.42", FEV, "0.83"));
        when(sgs.buscar(Indice.IPCA, JAN, MAR)).thenReturn(valores(JAN, "0.42", FEV, "0.83", MAR, "0.16"));

        ipca(JAN, FEV);
        SerieIndicesEmMemoria serie = ipca(FEV, MAR);

        assertThat(serie.variacaoMensal(Indice.IPCA, MAR)).isEqualByComparingTo("0.16");
    }

    @Test
    void mesAindaNaoPublicadoSoEReconsultadoDepoisDoIntervalo() {
        when(sgs.buscar(Indice.IPCA, JAN, MAR))
                .thenReturn(valores(JAN, "0.42", FEV, "0.83"))
                .thenReturn(valores(JAN, "0.42", FEV, "0.83", MAR, "0.16"));

        ipca(JAN, MAR);
        agora = agora.plus(Duration.ofMinutes(30));
        SerieIndicesEmMemoria antes = ipca(JAN, MAR);
        assertThatThrownBy(() -> antes.variacaoMensal(Indice.IPCA, MAR)).isInstanceOf(IndiceIndisponivelException.class);
        verify(sgs, times(1)).buscar(any(), any(), any());

        agora = agora.plus(Duration.ofMinutes(31));
        SerieIndicesEmMemoria depois = ipca(JAN, MAR);

        assertThat(depois.variacaoMensal(Indice.IPCA, MAR)).isEqualByComparingTo("0.16");
        verify(sgs, times(2)).buscar(Indice.IPCA, JAN, MAR);
    }

    @Test
    void mesQueNuncaTeraValorNaoForcaReconsulta() {
        // a taxa legal só existe a partir de 08/2024: os meses anteriores ficam sem valor para sempre
        YearMonth ago = YearMonth.of(2024, 8);
        YearMonth set = YearMonth.of(2024, 9);
        when(sgs.buscar(Indice.TAXA_LEGAL, JAN, set)).thenReturn(valores(ago, "0.605306", set, "0.676227"));

        cache.carregar(Set.of(Indice.TAXA_LEGAL), JAN, set);
        agora = agora.plus(Duration.ofDays(2));
        cache.carregar(Set.of(Indice.TAXA_LEGAL), JAN, set);

        verify(sgs, times(1)).buscar(any(), any(), any());
    }

    @Test
    void sgsForaDoArNaReconsultaUsaOQueOCacheJaTem() {
        when(sgs.buscar(Indice.IPCA, JAN, MAR))
                .thenReturn(valores(JAN, "0.42", FEV, "0.83"))
                .thenThrow(new SgsException("SGS respondeu 503 para IPCA"));

        ipca(JAN, MAR);
        agora = agora.plus(Duration.ofHours(2));
        SerieIndicesEmMemoria serie = ipca(JAN, MAR);

        assertThat(serie.variacaoMensal(Indice.IPCA, FEV)).isEqualByComparingTo("0.83");
        assertThatThrownBy(() -> serie.variacaoMensal(Indice.IPCA, MAR)).isInstanceOf(IndiceIndisponivelException.class);
    }

    @Test
    void sgsForaDoArSemCacheDoPeriodoFalha() {
        when(sgs.buscar(any(), any(), any())).thenThrow(new SgsException("SGS respondeu 503 para IPCA"));

        assertThatThrownBy(() -> ipca(JAN, MAR)).isInstanceOf(SgsException.class);
    }

    @Test
    void atualizacaoPeriodicaGravaRevisoesEOsMesesNovos() {
        when(sgs.buscar(Indice.IPCA, JAN, MAR)).thenReturn(valores(JAN, "0.42", FEV, "0.83", MAR, "0.16"));
        ipca(JAN, MAR);

        agora = Instant.parse("2024-05-20T09:00:00Z");
        when(sgs.buscar(Indice.IPCA, JAN, MAI))
                .thenReturn(valores(JAN, "0.42", FEV, "0.84", MAR, "0.16", ABR, "0.38"));
        cache.atualizarTodas();
        SerieIndicesEmMemoria serie = ipca(JAN, ABR);

        assertThat(serie.variacaoMensal(Indice.IPCA, FEV)).as("revisado pelo BCB").isEqualByComparingTo("0.84");
        assertThat(serie.variacaoMensal(Indice.IPCA, ABR)).isEqualByComparingTo("0.38");
        verify(sgs, times(2)).buscar(any(), any(), any());
    }

    @Test
    void atualizacaoPeriodicaReleSoAJanelaDeRevisao() {
        YearMonth inicio = YearMonth.of(2003, 2);
        when(sgs.buscar(eq(Indice.INPC), eq(inicio), eq(MAR))).thenReturn(valores(inicio, "1.46", MAR, "0.19"));
        cache.carregar(Set.of(Indice.INPC), inicio, MAR);

        cache.atualizarTodas();

        verify(sgs).buscar(Indice.INPC, YearMonth.of(2022, 4), ABR);
    }

    @Test
    void atualizacaoPeriodicaSegueQuandoUmaSerieFalha() {
        when(sgs.buscar(Indice.INPC, JAN, MAR)).thenReturn(valores(JAN, "0.57"));
        when(sgs.buscarSalarioMinimo(JAN, MAR)).thenReturn(valores(JAN, "1412.00"));
        cache.carregar(Set.of(Indice.INPC), JAN, MAR);
        cache.carregarSalarioMinimo(JAN, MAR);

        when(sgs.buscar(Indice.INPC, JAN, ABR)).thenThrow(new SgsException("SGS respondeu 503 para INPC"));
        when(sgs.buscarSalarioMinimo(JAN, ABR)).thenReturn(valores(JAN, "1412.00", ABR, "1412.00"));
        cache.atualizarTodas();

        assertThat(repositorio.ler(CacheSeries.SALARIO_MINIMO, ABR, ABR)).containsKey(ABR);
    }

    @Test
    void semConsultaAnteriorAAtualizacaoNaoVaiAoSgs() {
        cache.atualizarTodas();

        verify(sgs, never()).buscar(any(), any(), any());
        verify(sgs, never()).buscarSalarioMinimo(any(), any());
    }
}
