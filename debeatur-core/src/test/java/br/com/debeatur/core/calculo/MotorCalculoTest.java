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
            .com(Indice.INPC, YearMonth.of(2024, 7), "0.26")
            // fica na série de propósito: se o regime voltar a corrigir agosto/2024 pelo INPC,
            // o fator muda e o teste falha
            .com(Indice.INPC, YearMonth.of(2024, 8), "-0.14")
            .com(Indice.IPCA, YearMonth.of(2024, 8), "-0.02")
            .com(Indice.IPCA, YearMonth.of(2024, 9), "0.44")
            .com(Indice.IPCA, YearMonth.of(2024, 10), "0.56")
            // taxa legal divulgada pelo BCB (SGS 29543)
            .com(Indice.TAXA_LEGAL, YearMonth.of(2024, 8), "0.605306")
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
                LocalDate.of(2024, 7, 1), LocalDate.of(2024, 7, 1));

        Demonstrativo d = motor.calcular(new ParametrosCalculo(
                List.of(parcela), LocalDate.of(2024, 11, 5), RegimeAtualizacao.civelPadrao(), false));

        LinhaDemonstrativo linha = d.linhas().getFirst();
        // jul: INPC; ago, set e out: IPCA (a tabela da CGJ de agosto/2024 só tem INPC até julho)
        // 1,0026 × 0,9998 × 1,0044 × 1,0056
        assertThat(linha.fatorCorrecao()).isEqualByComparingTo("1.0124481739231872");
        assertThat(linha.valorCorrigido()).isEqualTo(Dinheiro.de("1012.45"));
        // jul: 1%; ago: 1,005752% (29/30 de 1% + 2/31 da taxa legal de agosto, como a DPMG);
        // set e out: taxa legal divulgada pelo BCB
        assertThat(linha.percentualJuros()).isEqualByComparingTo("3.386220");
        assertThat(linha.valorJuros()).isEqualTo(Dinheiro.de("34.28"));
        assertThat(d.total()).isEqualTo(Dinheiro.de("1046.73"));
    }

    @Test
    void regimeCivelPadraoTrocaCorrecaoEJurosNosMesesCertos() {
        RegimeAtualizacao regime = RegimeAtualizacao.civelPadrao();

        assertThat(regime.regraPara(YearMonth.of(2024, 7)))
                .extracting(RegraPeriodo::correcao, RegraPeriodo::juros)
                .containsExactly(Indice.INPC, new Juros.TaxaFixa(BigDecimal.ONE));
        assertThat(regime.regraPara(YearMonth.of(2024, 8)))
                .extracting(RegraPeriodo::correcao, RegraPeriodo::juros)
                .containsExactly(Indice.IPCA,
                        new Juros.TransicaoParaTaxaLegal(LocalDate.of(2024, 8, 29), BigDecimal.ONE));
        assertThat(regime.regraPara(YearMonth.of(2024, 9)))
                .extracting(RegraPeriodo::correcao, RegraPeriodo::juros)
                .containsExactly(Indice.IPCA, new Juros.TaxaLegal());
    }

    @Test
    void transicaoDaTabelaCgjAcompanhaADataDeCorte() {
        // a edição de setembro/2024 da tabela só tem INPC até agosto
        RegimeAtualizacao regime = RegimeAtualizacao.transicaoTabelaCgjParaLei14905(
                "corte em 30/09/2024", LocalDate.of(2024, 9, 30));

        assertThat(regime.regraPara(YearMonth.of(2024, 8)).correcao()).isEqualTo(Indice.INPC);
        assertThat(regime.regraPara(YearMonth.of(2024, 9)))
                .extracting(RegraPeriodo::correcao, RegraPeriodo::juros)
                .containsExactly(Indice.IPCA,
                        new Juros.TransicaoParaTaxaLegal(LocalDate.of(2024, 9, 30), BigDecimal.ONE));
        assertThat(regime.regraPara(YearMonth.of(2024, 10)).juros()).isEqualTo(new Juros.TaxaLegal());
    }

    @Test
    void transicaoDivideOMesDoCorteComoAPlanilhaDaDpmg() {
        // planilha da DPMG, agosto/2024: 29/30 de 1% (0,9667%) + 2/31 de 0,605306% (0,039052%)
        Juros transicao = new Juros.TransicaoParaTaxaLegal(LocalDate.of(2024, 8, 29), BigDecimal.ONE);

        assertThat(transicao.percentualNoMes(YearMonth.of(2024, 8), serie))
                .isEqualByComparingTo("1.005752");
    }

    @Test
    void transicaoNoUltimoDiaDoMesMantemATaxaFixaInteira() {
        Juros transicao = new Juros.TransicaoParaTaxaLegal(LocalDate.of(2024, 9, 30), BigDecimal.ONE);

        assertThat(transicao.percentualNoMes(YearMonth.of(2024, 9), serie)).isEqualByComparingTo("1");
    }

    @Test
    void transicaoSoSeAplicaAoMesDoCorte() {
        Juros transicao = new Juros.TransicaoParaTaxaLegal(LocalDate.of(2024, 8, 29), BigDecimal.ONE);

        assertThatThrownBy(() -> transicao.percentualNoMes(YearMonth.of(2024, 9), serie))
                .isInstanceOf(IllegalArgumentException.class);
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
