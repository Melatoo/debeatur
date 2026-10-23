package br.com.debeatur.indices;

import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.YearMonth;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

class SgsClientTest {

    @Test
    void interpretaRespostaDoSgs() {
        String corpo = """
                [{"data":"01/09/2024","valor":"0.44"},{"data":"01/10/2024","valor":"0.56"}]
                """;

        Map<YearMonth, BigDecimal> serie = new SgsClient().interpretar(corpo);

        assertThat(serie).containsOnlyKeys(YearMonth.of(2024, 9), YearMonth.of(2024, 10));
        assertThat(serie.get(YearMonth.of(2024, 10))).isEqualByComparingTo("0.56");
    }
}
