package br.com.debeatur.core.calculo;

import br.com.debeatur.core.Dinheiro;
import br.com.debeatur.core.indice.Indice;
import br.com.debeatur.core.regime.Juros;
import br.com.debeatur.core.regime.RegimeAtualizacao;
import br.com.debeatur.core.regime.RegraPeriodo;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.YearMonth;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatIllegalArgumentException;

class ParametrosCalculoTest {

    /** Regime de 01/2023 a 06/2024. */
    private static final RegimeAtualizacao REGIME_FECHADO = new RegimeAtualizacao("Título", List.of(
            new RegraPeriodo(YearMonth.of(2023, 1), YearMonth.of(2024, 6), Indice.INPC, new Juros.TaxaFixa(BigDecimal.ONE))));

    private static Parcela parcela(LocalDate inicioCorrecao, LocalDate inicioJuros) {
        return new Parcela("Aluguel", new Dinheiro(new BigDecimal("1000.00")), inicioCorrecao, inicioJuros);
    }

    @Test
    void aceitaRegimeQueCobreDoPrimeiroTermoAoMesAnteriorADataBase() {
        assertThatCode(() -> new ParametrosCalculo(
                List.of(parcela(LocalDate.of(2023, 1, 10), LocalDate.of(2023, 3, 1))),
                LocalDate.of(2024, 7, 15), REGIME_FECHADO, false))
                .doesNotThrowAnyException();
    }

    @Test
    void recusaRegimeQueComecaDepoisDoTermoInicial() {
        assertThatIllegalArgumentException()
                .isThrownBy(() -> new ParametrosCalculo(
                        List.of(parcela(LocalDate.of(2022, 12, 31), null)),
                        LocalDate.of(2024, 7, 1), REGIME_FECHADO, false))
                .withMessageContaining("não cobre")
                .withMessageContaining("2022-12 a 2024-06");
    }

    @Test
    void consideraOTermoInicialDosJuros() {
        assertThatIllegalArgumentException()
                .isThrownBy(() -> new ParametrosCalculo(
                        List.of(parcela(LocalDate.of(2023, 5, 1), LocalDate.of(2022, 11, 1))),
                        LocalDate.of(2024, 7, 1), REGIME_FECHADO, false))
                .withMessageContaining("2022-11");
    }

    @Test
    void recusaRegimeQueTerminaAntesDaDataBase() {
        assertThatIllegalArgumentException()
                .isThrownBy(() -> new ParametrosCalculo(
                        List.of(parcela(LocalDate.of(2023, 1, 1), null)),
                        LocalDate.of(2024, 8, 1), REGIME_FECHADO, false))
                .withMessageContaining("2023-01 a 2024-07");
    }

    @Test
    void naoExigeCoberturaQuandoNenhumMesEntraNoCalculo() {
        assertThatCode(() -> new ParametrosCalculo(
                List.of(parcela(LocalDate.of(2030, 3, 1), null)),
                LocalDate.of(2030, 3, 20), REGIME_FECHADO, false))
                .doesNotThrowAnyException();
    }
}
