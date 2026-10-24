package br.com.debeatur.core.regime;

import br.com.debeatur.core.indice.Indice;
import org.junit.jupiter.api.Test;

import java.time.YearMonth;

import static org.assertj.core.api.Assertions.assertThatIllegalArgumentException;

class RegraPeriodoTest {

    @Test
    void recusaTaxaLegalComoIndiceDeCorrecao() {
        assertThatIllegalArgumentException()
                .isThrownBy(() -> new RegraPeriodo(YearMonth.of(2024, 9), null, Indice.TAXA_LEGAL, new Juros.TaxaLegal()))
                .withMessageContaining("TAXA_LEGAL");
    }
}
