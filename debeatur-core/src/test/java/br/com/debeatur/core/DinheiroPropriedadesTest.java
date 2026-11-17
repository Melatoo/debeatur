package br.com.debeatur.core;

import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.SplittableRandom;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Propriedades do {@link Dinheiro} conferidas em valores sorteados, com semente fixa. Os
 * valores têm até seis casas, para cair também nos meios-centavos que o HALF_UP decide.
 */
class DinheiroPropriedadesTest {

    private static final int CASOS = 2_000;
    private static final SplittableRandom ALEATORIO = new SplittableRandom(523L);

    @Test
    void ficaSempreEmDuasCasas() {
        for (int n = 0; n < CASOS; n++) {
            assertThat(new Dinheiro(qualquer()).valor().scale()).as("caso %d", n).isEqualTo(2);
        }
    }

    @Test
    void arredondaComoOHalfUpDoBigDecimal() {
        for (int n = 0; n < CASOS; n++) {
            BigDecimal valor = qualquer();
            assertThat(new Dinheiro(valor).valor()).as("caso %d: %s", n, valor)
                    .isEqualTo(valor.setScale(2, RoundingMode.HALF_UP));
        }
    }

    @Test
    void arredondaIgualParaPositivoENegativo() {
        for (int n = 0; n < CASOS; n++) {
            BigDecimal valor = qualquer();
            assertThat(new Dinheiro(valor.negate()).valor()).as("caso %d: %s", n, valor)
                    .isEqualTo(new Dinheiro(valor).valor().negate());
        }
    }

    @Test
    void somarESubtrairODesfaz() {
        for (int n = 0; n < CASOS; n++) {
            Dinheiro a = new Dinheiro(qualquer());
            Dinheiro b = new Dinheiro(qualquer());
            assertThat(a.mais(b).menos(b)).as("caso %d", n).isEqualTo(a);
            assertThat(a.mais(b)).as("caso %d: ordem da soma", n).isEqualTo(b.mais(a));
        }
    }

    @Test
    void percentualEMultiplicacaoPeloFatorDaoOMesmo() {
        for (int n = 0; n < CASOS; n++) {
            Dinheiro a = new Dinheiro(qualquer());
            BigDecimal pontos = BigDecimal.valueOf(ALEATORIO.nextInt(0, 100_001), 3);
            assertThat(a.percentual(pontos)).as("caso %d: %s%% de %s", n, pontos, a)
                    .isEqualTo(a.vezes(pontos.movePointLeft(2)));
        }
    }

    /** Valor entre −R$ 1.000.000,00 e R$ 1.000.000,00, com seis casas. */
    private static BigDecimal qualquer() {
        return BigDecimal.valueOf(ALEATORIO.nextLong(-1_000_000_000_000L, 1_000_000_000_001L), 6);
    }
}
