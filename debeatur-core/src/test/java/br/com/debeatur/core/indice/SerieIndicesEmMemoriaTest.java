package br.com.debeatur.core.indice;

import org.junit.jupiter.api.Test;

import java.time.YearMonth;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.catchThrowableOfType;

class SerieIndicesEmMemoriaTest {

    @Test
    void mesSemValorDizQualIndiceEQualMesFaltam() {
        SerieIndices serie = new SerieIndicesEmMemoria().com(Indice.INPC, YearMonth.of(2024, 1), "0.57");

        IndiceIndisponivelException e = catchThrowableOfType(IndiceIndisponivelException.class,
                () -> serie.variacaoMensal(Indice.INPC, YearMonth.of(2024, 2)));

        assertThat(e.indice()).isEqualTo(Indice.INPC);
        assertThat(e.mes()).isEqualTo(YearMonth.of(2024, 2));
    }
}
