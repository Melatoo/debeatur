package br.com.debeatur.api.serie;

import br.com.debeatur.core.Dinheiro;
import br.com.debeatur.core.indice.Indice;
import br.com.debeatur.core.indice.IndiceIndisponivelException;
import br.com.debeatur.core.indice.SalarioMinimoEmMemoria;
import br.com.debeatur.core.indice.SalarioMinimoIndisponivelException;
import br.com.debeatur.core.indice.SerieIndicesEmMemoria;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.YearMonth;
import java.util.Map;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class SnapshotSeriesTest {

    private static final YearMonth JAN = YearMonth.of(2024, 1);
    private static final YearMonth FEV = YearMonth.of(2024, 2);
    private static final YearMonth MAR = YearMonth.of(2024, 3);

    /** Fonte com mais meses do que o pedido e sem a taxa legal de janeiro. */
    private final FonteSeries origem = new FonteSeries() {
        @Override
        public SerieIndicesEmMemoria carregar(Set<Indice> indices, YearMonth de, YearMonth ate) {
            return new SerieIndicesEmMemoria()
                    .com(Indice.IPCA, JAN, "0.42").com(Indice.IPCA, FEV, "0.83").com(Indice.IPCA, MAR, "0.16")
                    .com(Indice.TAXA_LEGAL, FEV, "0.605306");
        }

        @Override
        public SalarioMinimoEmMemoria carregarSalarioMinimo(YearMonth de, YearMonth ate) {
            return new SalarioMinimoEmMemoria().com(JAN, "1412.00").com(FEV, "1412.00");
        }
    };

    @Test
    void anotaSoOsMesesPedidosQueAFonteEntregou() {
        SnapshotSeries.Gravacao gravacao = SnapshotSeries.gravando(origem);

        gravacao.carregar(Set.of(Indice.IPCA, Indice.TAXA_LEGAL), JAN, FEV);
        gravacao.carregarSalarioMinimo(FEV, FEV);

        assertThat(gravacao.snapshot().valores()).isEqualTo(Map.of(
                "IPCA", Map.of(JAN, new BigDecimal("0.42"), FEV, new BigDecimal("0.83")),
                "TAXA_LEGAL", Map.of(FEV, new BigDecimal("0.605306")),
                SnapshotSeries.SALARIO_MINIMO, Map.of(FEV, new BigDecimal("1412.00"))));
    }

    @Test
    void repassaOQueAFonteEntregou() {
        SerieIndicesEmMemoria serie = SnapshotSeries.gravando(origem).carregar(Set.of(Indice.IPCA), JAN, FEV);

        assertThat(serie.variacaoMensal(Indice.IPCA, FEV)).isEqualByComparingTo("0.83");
    }

    @Test
    void comoFonteEntregaSoOQueEstaNoSnapshot() {
        SnapshotSeries snapshot = new SnapshotSeries(Map.of(
                "IPCA", Map.of(JAN, new BigDecimal("0.42"), FEV, new BigDecimal("0.83")),
                SnapshotSeries.SALARIO_MINIMO, Map.of(JAN, new BigDecimal("1412.00"))));

        SerieIndicesEmMemoria serie = snapshot.carregar(Set.of(Indice.IPCA, Indice.INPC), JAN, MAR);
        SalarioMinimoEmMemoria salario = snapshot.carregarSalarioMinimo(JAN, FEV);

        assertThat(serie.variacaoMensal(Indice.IPCA, FEV)).isEqualTo(new BigDecimal("0.83"));
        assertThatThrownBy(() -> serie.variacaoMensal(Indice.IPCA, MAR)).isInstanceOf(IndiceIndisponivelException.class);
        assertThatThrownBy(() -> serie.variacaoMensal(Indice.INPC, JAN)).isInstanceOf(IndiceIndisponivelException.class);
        assertThat(salario.vigente(JAN)).isEqualTo(Dinheiro.de("1412.00"));
        assertThatThrownBy(() -> salario.vigente(FEV)).isInstanceOf(SalarioMinimoIndisponivelException.class);
    }

    @Test
    void respeitaOPeriodoPedido() {
        SnapshotSeries snapshot = new SnapshotSeries(Map.of(
                "IPCA", Map.of(JAN, new BigDecimal("0.42"), FEV, new BigDecimal("0.83"))));

        SerieIndicesEmMemoria serie = snapshot.carregar(Set.of(Indice.IPCA), FEV, FEV);

        assertThatThrownBy(() -> serie.variacaoMensal(Indice.IPCA, JAN)).isInstanceOf(IndiceIndisponivelException.class);
    }
}
