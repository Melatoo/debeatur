package br.com.debeatur.core.indice;

import br.com.debeatur.core.Dinheiro;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.YearMonth;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class SalarioMinimoEmMemoriaTest {

    @Test
    void devolveOValorDoMes() {
        SalarioMinimo salario = new SalarioMinimoEmMemoria()
                .com(YearMonth.of(2023, 4), "1302.00")
                .comTodos(Map.of(YearMonth.of(2023, 5), new BigDecimal("1320")));

        assertThat(salario.vigente(YearMonth.of(2023, 4))).isEqualTo(Dinheiro.de("1302.00"));
        assertThat(salario.vigente(YearMonth.of(2023, 5))).isEqualTo(Dinheiro.de("1320.00"));
    }

    @Test
    void mesSemValorEIndisponivelMesmoEntreMesesInformados() {
        SalarioMinimo salario = new SalarioMinimoEmMemoria()
                .com(YearMonth.of(2024, 1), "1412.00")
                .com(YearMonth.of(2024, 3), "1412.00");

        assertThatThrownBy(() -> salario.vigente(YearMonth.of(2024, 2)))
                .isInstanceOf(SalarioMinimoIndisponivelException.class)
                .hasMessageContaining("2024-02");
    }
}
