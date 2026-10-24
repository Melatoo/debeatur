package br.com.debeatur.core.calculo;

import br.com.debeatur.core.Dinheiro;
import br.com.debeatur.core.indice.Indice;
import br.com.debeatur.core.indice.IndiceIndisponivelException;
import br.com.debeatur.core.indice.SerieIndicesEmMemoria;
import br.com.debeatur.core.regime.Juros;
import br.com.debeatur.core.regime.RegimeAtualizacao;
import br.com.debeatur.core.regime.RegraPeriodo;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.YearMonth;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class MotorCalculoTest {

    private static final RegimeAtualizacao INPC_UM_PORCENTO = new RegimeAtualizacao("INPC + 1%", List.of(
            new RegraPeriodo(YearMonth.of(2000, 1), null, Indice.INPC, new Juros.TaxaFixa(BigDecimal.ONE))));

    private final SerieIndicesEmMemoria serie = new SerieIndicesEmMemoria()
            .com(Indice.INPC, YearMonth.of(2024, 1), "0.50")
            .com(Indice.INPC, YearMonth.of(2024, 2), "1.00")
            .com(Indice.INPC, YearMonth.of(2024, 3), "0.20")
            .com(Indice.INPC, YearMonth.of(2024, 8), "-0.14")
            .com(Indice.IPCA, YearMonth.of(2024, 9), "0.44")
            .com(Indice.IPCA, YearMonth.of(2024, 10), "0.56")
            // taxa legal divulgada pelo BCB (SGS 29543)
            .com(Indice.TAXA_LEGAL, YearMonth.of(2024, 9), "0.676227")
            .com(Indice.TAXA_LEGAL, YearMonth.of(2024, 10), "0.704241");

    private final MotorCalculo motor = new MotorCalculo(serie);

    @Test
    void corrigeAplicaJurosEAcrescimosDoArt523() {
        Parcela parcela = new Parcela("Indenização", Dinheiro.de("1000.00"),
                LocalDate.of(2024, 1, 10), LocalDate.of(2024, 2, 15));

        Demonstrativo d = motor.calcular(new ParametrosCalculo(
                List.of(parcela), LocalDate.of(2024, 4, 20), INPC_UM_PORCENTO, true));

        LinhaDemonstrativo linha = d.linhas().getFirst();
        // 1,005 × 1,010 × 1,002 (jan a mar)
        assertThat(linha.fatorCorrecao()).isEqualByComparingTo("1.0170801");
        assertThat(linha.valorCorrigido()).isEqualTo(Dinheiro.de("1017.08"));
        // fev e mar a 1% a.m.
        assertThat(linha.percentualJuros()).isEqualByComparingTo("2");
        assertThat(linha.valorJuros()).isEqualTo(Dinheiro.de("20.34"));
        assertThat(d.debitoAtualizado()).isEqualTo(Dinheiro.de("1037.42"));
        assertThat(d.multaArt523()).isEqualTo(Dinheiro.de("103.74"));
        assertThat(d.honorariosArt523()).isEqualTo(Dinheiro.de("103.74"));
        assertThat(d.total()).isEqualTo(Dinheiro.de("1244.90"));
    }

    @Test
    void semArt523OTotalEhODebitoAtualizado() {
        Parcela parcela = new Parcela("Danos", Dinheiro.de("1000.00"), LocalDate.of(2024, 1, 1), null);

        Demonstrativo d = motor.calcular(new ParametrosCalculo(
                List.of(parcela), LocalDate.of(2024, 4, 1), INPC_UM_PORCENTO, false));

        assertThat(d.totalJuros()).isEqualTo(Dinheiro.ZERO);
        assertThat(d.multaArt523()).isEqualTo(Dinheiro.ZERO);
        assertThat(d.total()).isEqualTo(d.debitoAtualizado());
    }

    @Test
    void regimeCivelPadraoMudaDeCriterioComALei14905() {
        Parcela parcela = new Parcela("Danos", Dinheiro.de("1000.00"),
                LocalDate.of(2024, 8, 1), LocalDate.of(2024, 8, 1));

        Demonstrativo d = motor.calcular(new ParametrosCalculo(
                List.of(parcela), LocalDate.of(2024, 11, 5), RegimeAtualizacao.civelPadrao(), false));

        LinhaDemonstrativo linha = d.linhas().getFirst();
        // ago: INPC; set e out: IPCA
        assertThat(linha.fatorCorrecao()).isEqualByComparingTo("1.008610605504");
        assertThat(linha.valorCorrigido()).isEqualTo(Dinheiro.de("1008.61"));
        // ago: 1%; set e out: taxa legal divulgada pelo BCB
        assertThat(linha.percentualJuros()).isEqualByComparingTo("2.380468");
        assertThat(linha.valorJuros()).isEqualTo(Dinheiro.de("24.01"));
        assertThat(d.total()).isEqualTo(Dinheiro.de("1032.62"));
    }

    @Test
    void taxaLegalUsaASerieDoBcbSemRecalcular() {
        // o motor não deriva a taxa da SELIC nem do IPCA: lê o valor publicado no SGS 29543
        assertThat(new Juros.TaxaLegal().percentualNoMes(YearMonth.of(2024, 9), serie))
                .isEqualByComparingTo("0.676227");
    }

    @Test
    void falhaQuandoFaltaIndiceDoPeriodo() {
        Parcela parcela = new Parcela("Danos", Dinheiro.de("1000.00"), LocalDate.of(2023, 12, 1), null);

        assertThatThrownBy(() -> motor.calcular(new ParametrosCalculo(
                List.of(parcela), LocalDate.of(2024, 2, 1), INPC_UM_PORCENTO, false)))
                .isInstanceOf(IndiceIndisponivelException.class)
                .hasMessageContaining("2023-12");
    }

    @Test
    void rejeitaTermoInicialPosteriorADataBase() {
        Parcela parcela = new Parcela("Danos", Dinheiro.de("1000.00"), LocalDate.of(2024, 5, 1), null);

        assertThatThrownBy(() -> new ParametrosCalculo(
                List.of(parcela), LocalDate.of(2024, 4, 1), INPC_UM_PORCENTO, false))
                .isInstanceOf(IllegalArgumentException.class);
    }
}
