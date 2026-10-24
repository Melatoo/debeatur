package br.com.debeatur.core.gabarito;

import br.com.debeatur.core.Dinheiro;
import br.com.debeatur.core.calculo.Demonstrativo;
import br.com.debeatur.core.calculo.MotorCalculo;
import br.com.debeatur.core.calculo.Parcela;
import br.com.debeatur.core.indice.Indice;
import br.com.debeatur.core.indice.SerieIndicesEmMemoria;
import br.com.debeatur.core.regime.Juros;
import br.com.debeatur.core.regime.RegimeAtualizacao;
import br.com.debeatur.core.regime.RegraPeriodo;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.YearMonth;
import java.util.List;

import static br.com.debeatur.core.gabarito.CasoGabarito.dinheiro;
import static br.com.debeatur.core.gabarito.CasoGabarito.numero;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatIllegalArgumentException;

/**
 * Testa a comparação em si, não o motor: usa o exemplo calculado à mão do
 * {@code MotorCalculoTest}, que não é um gabarito homologado.
 */
class ComparacaoGabaritoTest {

    private static final RegimeAtualizacao INPC_UM_PORCENTO = new RegimeAtualizacao("INPC + 1%", List.of(
            new RegraPeriodo(YearMonth.of(2000, 1), null, Indice.INPC, new Juros.TaxaFixa(BigDecimal.ONE))));

    private static final SerieIndicesEmMemoria SERIE = new SerieIndicesEmMemoria()
            .com(Indice.INPC, YearMonth.of(2024, 1), "0.50")
            .com(Indice.INPC, YearMonth.of(2024, 2), "1.00")
            .com(Indice.INPC, YearMonth.of(2024, 3), "0.20");

    private static final Parcela PARCELA = new Parcela("Indenização", Dinheiro.de("1000.00"),
            LocalDate.of(2024, 1, 10), LocalDate.of(2024, 2, 15));

    @Test
    void naoApontaDivergenciaQuandoOsValoresBatem() {
        CasoGabarito caso = exemplo("1.01708", "1017.08", "20.34", "1244.90").criar();

        assertThat(ComparacaoGabarito.divergencias(caso, calcular(caso))).isEmpty();
    }

    @Test
    void comparaOFatorNaPrecisaoImpressa() {
        // 1,0170801 impresso com quatro casas
        CasoGabarito caso = exemplo("1.0171", null, null, "1244.90").criar();

        assertThat(ComparacaoGabarito.divergencias(caso, calcular(caso))).isEmpty();
    }

    @Test
    void descreveCadaDivergencia() {
        CasoGabarito caso = exemplo("1.0172", "1017.09", "20.34", "1244.91").criar();

        assertThat(ComparacaoGabarito.divergencias(caso, calcular(caso))).containsExactly(
                "parcela 1 (Indenização), fator de correção: esperado 1.0172, motor 1.0171 (1.0170801 sem arredondar)",
                "parcela 1 (Indenização), valor corrigido: esperado R$ 1017.09, motor R$ 1017.08 (diferença R$ -0.01)",
                "total: esperado R$ 1244.91, motor R$ 1244.90 (diferença R$ -0.01)");
    }

    @Test
    void aceitaDiferencaDentroDaToleranciaJustificada() {
        CasoGabarito caso = exemplo(null, "1017.09", "20.34", "1244.91")
                .tolerancia("0.01", "Contadoria arredonda o fator em quatro casas")
                .criar();

        assertThat(ComparacaoGabarito.divergencias(caso, calcular(caso))).isEmpty();
    }

    @Test
    void recusaToleranciaSemJustificativa() {
        assertThatIllegalArgumentException()
                .isThrownBy(() -> exemplo(null, "1017.08", "20.34", "1244.90").tolerancia("0.01", " ").criar())
                .withMessageContaining("justificativa");
    }

    private static CasoGabarito.Construtor exemplo(String fator, String corrigido, String juros, String total) {
        return CasoGabarito.de("Exemplo", "MotorCalculoTest (cálculo à mão)")
                .dataBase("2024-04-20")
                .regime(INPC_UM_PORCENTO)
                .comArt523()
                .serie(SERIE)
                .parcela(PARCELA, new CasoGabarito.LinhaEsperada(
                        numero(fator), dinheiro(corrigido), numero("2"), dinheiro(juros), null))
                .totais("1037.42", "103.74", "103.74", total);
    }

    private static Demonstrativo calcular(CasoGabarito caso) {
        return new MotorCalculo(caso.serie()).calcular(caso.parametros());
    }
}
