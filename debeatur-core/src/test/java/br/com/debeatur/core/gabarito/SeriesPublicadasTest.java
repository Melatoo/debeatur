package br.com.debeatur.core.gabarito;

import br.com.debeatur.core.indice.Indice;
import br.com.debeatur.core.indice.SerieIndicesEmMemoria;
import org.junit.jupiter.api.Test;

import java.time.YearMonth;

import static org.assertj.core.api.Assertions.assertThat;

class SeriesPublicadasTest {

    private final SerieIndicesEmMemoria serie = SeriesPublicadas.carregar();

    @Test
    void carregaAsSeriesFixadasDoSgs() {
        assertThat(serie.variacaoMensal(Indice.INPC, YearMonth.of(2015, 1))).isEqualByComparingTo("1.48");
        assertThat(serie.variacaoMensal(Indice.IPCA, YearMonth.of(2024, 9))).isEqualByComparingTo("0.44");
        assertThat(serie.variacaoMensal(Indice.INPC, YearMonth.of(2024, 8))).isEqualByComparingTo("-0.14");
    }

    @Test
    void taxaLegalComecaEmAgostoDe2024() {
        // Res. CMN 5.171/2024, art. 8º, parágrafo único: a taxa de agosto vale para 30 e 31/08
        assertThat(SeriesPublicadas.ler(Indice.TAXA_LEGAL).keySet()).first().isEqualTo(YearMonth.of(2024, 8));
        assertThat(serie.variacaoMensal(Indice.TAXA_LEGAL, YearMonth.of(2024, 9))).isEqualByComparingTo("0.676227");
        assertThat(serie.variacaoMensal(Indice.TAXA_LEGAL, YearMonth.of(2024, 10))).isEqualByComparingTo("0.704241");
    }
}
