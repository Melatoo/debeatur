package br.com.debeatur.core.alimentos;

import br.com.debeatur.core.Dinheiro;
import br.com.debeatur.core.calculo.Parcela;
import br.com.debeatur.core.indice.SalarioMinimo;
import br.com.debeatur.core.indice.SalarioMinimoEmMemoria;
import br.com.debeatur.core.indice.SalarioMinimoIndisponivelException;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.YearMonth;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class PensaoEmSalarioMinimoTest {

    private static final SalarioMinimo SALARIO_MINIMO = new SalarioMinimoEmMemoria()
            .com(YearMonth.of(2023, 12), "1320.00")
            .com(YearMonth.of(2024, 1), "1412.00")
            .com(YearMonth.of(2024, 2), "1412.00")
            .com(YearMonth.of(2024, 3), "1412.00");

    @Test
    void geraUmaParcelaPorMesComOSalarioMinimoDoVencimento() {
        List<Parcela> parcelas = pensao("30", 10, YearMonth.of(2023, 12), YearMonth.of(2024, 2))
                .parcelas(SALARIO_MINIMO);

        assertThat(parcelas).extracting(Parcela::descricao)
                .containsExactly("Alimentos 12/2023", "Alimentos 01/2024", "Alimentos 02/2024");
        assertThat(parcelas).extracting(Parcela::valor)
                .containsExactly(Dinheiro.de("396.00"), Dinheiro.de("423.60"), Dinheiro.de("423.60"));
        assertThat(parcelas).extracting(Parcela::inicioCorrecao)
                .containsExactly(LocalDate.of(2023, 12, 10), LocalDate.of(2024, 1, 10), LocalDate.of(2024, 2, 10));
        assertThat(parcelas).extracting(Parcela::inicioJuros)
                .containsExactly(LocalDate.of(2023, 12, 10), LocalDate.of(2024, 1, 10), LocalDate.of(2024, 2, 10));
    }

    @Test
    void informaOSalarioMinimoDeCadaParcela() {
        PensaoEmSalarioMinimo pensao = pensao("30", 10, YearMonth.of(2023, 12), YearMonth.of(2024, 1));

        List<PensaoEmSalarioMinimo.ParcelaDoMes> meses = pensao.parcelasPorMes(SALARIO_MINIMO);

        assertThat(meses).extracting(PensaoEmSalarioMinimo.ParcelaDoMes::mes)
                .containsExactly(YearMonth.of(2023, 12), YearMonth.of(2024, 1));
        assertThat(meses).extracting(PensaoEmSalarioMinimo.ParcelaDoMes::salarioMinimo)
                .containsExactly(Dinheiro.de("1320.00"), Dinheiro.de("1412.00"));
        assertThat(meses).extracting(PensaoEmSalarioMinimo.ParcelaDoMes::parcela)
                .containsExactlyElementsOf(pensao.parcelas(SALARIO_MINIMO));
    }

    @Test
    void arredondaOValorDaParcelaEmDuasCasas() {
        // 33,33% de R$ 1.412,00 = R$ 470,6196
        List<Parcela> parcelas = pensao("33.33", 1, YearMonth.of(2024, 1), YearMonth.of(2024, 1))
                .parcelas(SALARIO_MINIMO);

        assertThat(parcelas.getFirst().valor()).isEqualTo(Dinheiro.de("470.62"));
    }

    @Test
    void venceNoUltimoDiaDoMesQuandoOMesNaoTemODia() {
        List<Parcela> parcelas = pensao("30", 31, YearMonth.of(2024, 1), YearMonth.of(2024, 3))
                .parcelas(SALARIO_MINIMO);

        assertThat(parcelas).extracting(Parcela::inicioCorrecao)
                .containsExactly(LocalDate.of(2024, 1, 31), LocalDate.of(2024, 2, 29), LocalDate.of(2024, 3, 31));
    }

    @Test
    void ultimoVencimentoSegueODiaDoVencimento() {
        assertThat(pensao("30", 31, YearMonth.of(2024, 1), YearMonth.of(2024, 2)).ultimoVencimento())
                .isEqualTo(LocalDate.of(2024, 2, 29));
        assertThat(pensao("30", 10, YearMonth.of(2024, 1), YearMonth.of(2024, 3)).ultimoVencimento())
                .isEqualTo(LocalDate.of(2024, 3, 10));
    }

    @Test
    void jurosDesdeACitacaoSoAlcancamAsParcelasVencidasAntesDela() {
        LocalDate citacao = LocalDate.of(2024, 2, 5);
        List<Parcela> parcelas = new PensaoEmSalarioMinimo(new BigDecimal("30"), 10, YearMonth.of(2024, 1),
                YearMonth.of(2024, 3), InicioJuros.CITACAO, citacao).parcelas(SALARIO_MINIMO);

        assertThat(parcelas).extracting(Parcela::inicioCorrecao)
                .containsExactly(LocalDate.of(2024, 1, 10), LocalDate.of(2024, 2, 10), LocalDate.of(2024, 3, 10));
        assertThat(parcelas).extracting(Parcela::inicioJuros)
                .containsExactly(citacao, LocalDate.of(2024, 2, 10), LocalDate.of(2024, 3, 10));
    }

    @Test
    void salarioMinimoIndisponivelInterrompeAGeracao() {
        PensaoEmSalarioMinimo pensao = pensao("30", 10, YearMonth.of(2024, 3), YearMonth.of(2024, 4));

        assertThatThrownBy(() -> pensao.parcelas(SALARIO_MINIMO))
                .isInstanceOf(SalarioMinimoIndisponivelException.class)
                .hasMessageContaining("2024-04");
    }

    @Test
    void rejeitaParametrosInvalidos() {
        YearMonth jan = YearMonth.of(2024, 1);
        assertThatThrownBy(() -> pensao("0", 10, jan, jan))
                .isInstanceOf(IllegalArgumentException.class).hasMessageContaining("percentual");
        assertThatThrownBy(() -> pensao("30", 0, jan, jan))
                .isInstanceOf(IllegalArgumentException.class).hasMessageContaining("dia do vencimento");
        assertThatThrownBy(() -> pensao("30", 32, jan, jan))
                .isInstanceOf(IllegalArgumentException.class).hasMessageContaining("dia do vencimento");
        assertThatThrownBy(() -> pensao("30", 10, jan, jan.minusMonths(1)))
                .isInstanceOf(IllegalArgumentException.class).hasMessageContaining("último mês");
        assertThatThrownBy(() -> new PensaoEmSalarioMinimo(BigDecimal.TEN, 10, jan, jan, InicioJuros.CITACAO, null))
                .isInstanceOf(IllegalArgumentException.class).hasMessageContaining("citação");
    }

    private static PensaoEmSalarioMinimo pensao(String percentual, int dia, YearMonth de, YearMonth ate) {
        return new PensaoEmSalarioMinimo(new BigDecimal(percentual), dia, de, ate, InicioJuros.VENCIMENTO, null);
    }
}
