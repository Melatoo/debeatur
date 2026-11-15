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
import java.util.TreeMap;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class PensaoTest {

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
        Pensao pensao = pensao("30", 10, YearMonth.of(2023, 12), YearMonth.of(2024, 1));

        List<Pensao.ParcelaDoMes> meses = pensao.parcelasPorMes(SALARIO_MINIMO);

        assertThat(meses).extracting(Pensao.ParcelaDoMes::mes)
                .containsExactly(YearMonth.of(2023, 12), YearMonth.of(2024, 1));
        assertThat(meses).extracting(Pensao.ParcelaDoMes::base)
                .containsExactly(Dinheiro.de("1320.00"), Dinheiro.de("1412.00"));
        assertThat(meses).extracting(Pensao.ParcelaDoMes::parcela)
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
        List<Parcela> parcelas = Pensao.emSalarioMinimo(new BigDecimal("30"), 10, YearMonth.of(2024, 1),
                YearMonth.of(2024, 3), InicioJuros.CITACAO, citacao).parcelas(SALARIO_MINIMO);

        assertThat(parcelas).extracting(Parcela::inicioCorrecao)
                .containsExactly(LocalDate.of(2024, 1, 10), LocalDate.of(2024, 2, 10), LocalDate.of(2024, 3, 10));
        assertThat(parcelas).extracting(Parcela::inicioJuros)
                .containsExactly(citacao, LocalDate.of(2024, 2, 10), LocalDate.of(2024, 3, 10));
    }

    @Test
    void salarioMinimoIndisponivelInterrompeAGeracao() {
        Pensao pensao = pensao("30", 10, YearMonth.of(2024, 3), YearMonth.of(2024, 4));

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
        assertThatThrownBy(() -> Pensao.emSalarioMinimo(BigDecimal.TEN, 10, jan, jan, InicioJuros.CITACAO, null))
                .isInstanceOf(IllegalArgumentException.class).hasMessageContaining("citação");
    }

    @Test
    void decimoTerceiroDobraAParcelaDeDezembro() {
        Pensao pensao = new Pensao(new BasePensao.PercentualDoSalarioMinimo(new BigDecimal("33.33")), 10,
                YearMonth.of(2023, 12), YearMonth.of(2024, 1), InicioJuros.VENCIMENTO, null, true);

        List<Pensao.ParcelaDoMes> meses = pensao.parcelasPorMes(SALARIO_MINIMO);

        // 33,33% de R$ 1.320,00 = R$ 439,956 → R$ 439,96, em dobro
        assertThat(meses).extracting(m -> m.parcela().valor())
                .containsExactly(Dinheiro.de("879.92"), Dinheiro.de("470.62"));
        assertThat(meses).extracting(Pensao.ParcelaDoMes::comDecimoTerceiro).containsExactly(true, false);
        assertThat(meses).extracting(m -> m.parcela().descricao())
                .containsExactly("Alimentos 12/2023 e 13º salário", "Alimentos 01/2024");
        assertThat(meses.getFirst().parcela().inicioCorrecao()).isEqualTo(LocalDate.of(2023, 12, 10));
    }

    @Test
    void semDecimoTerceiroDezembroEhUmMesComum() {
        assertThat(pensao("30", 10, YearMonth.of(2023, 12), YearMonth.of(2023, 12)).parcelas(SALARIO_MINIMO))
                .extracting(Parcela::valor).containsExactly(Dinheiro.de("396.00"));
    }

    @Test
    void percentualDosRendimentosEhIgualEmTodosOsMeses() {
        Pensao pensao = new Pensao(new BasePensao.PercentualDosRendimentos(new BigDecimal("30"),
                Dinheiro.de("3000.00")), 5, YearMonth.of(2025, 11), YearMonth.of(2026, 1),
                InicioJuros.VENCIMENTO, null, true);

        List<Pensao.ParcelaDoMes> meses = pensao.parcelasPorMes(null);

        assertThat(meses).extracting(m -> m.parcela().valor())
                .containsExactly(Dinheiro.de("900.00"), Dinheiro.de("1800.00"), Dinheiro.de("900.00"));
        assertThat(meses).extracting(Pensao.ParcelaDoMes::base)
                .containsOnly(Dinheiro.de("3000.00"));
        assertThat(pensao.base().usaSalarioMinimo()).isFalse();
    }

    @Test
    void valorFixoValeAteOProximoPeriodoSemReajuste() {
        TreeMap<YearMonth, Dinheiro> valores = new TreeMap<>();
        valores.put(YearMonth.of(2024, 1), Dinheiro.de("500.00"));
        valores.put(YearMonth.of(2024, 3), Dinheiro.de("650.00"));
        Pensao pensao = new Pensao(new BasePensao.ValorFixo(valores), 10, YearMonth.of(2024, 2),
                YearMonth.of(2024, 4), InicioJuros.VENCIMENTO, null, false);

        assertThat(pensao.parcelas(null)).extracting(Parcela::valor)
                .containsExactly(Dinheiro.de("500.00"), Dinheiro.de("650.00"), Dinheiro.de("650.00"));
    }

    @Test
    void rejeitaValorFixoQueComecaDepoisDaPensao() {
        TreeMap<YearMonth, Dinheiro> valores = new TreeMap<>();
        valores.put(YearMonth.of(2024, 3), Dinheiro.de("500.00"));
        BasePensao fixo = new BasePensao.ValorFixo(valores);

        assertThatThrownBy(() -> new Pensao(fixo, 10, YearMonth.of(2024, 2), YearMonth.of(2024, 4),
                InicioJuros.VENCIMENTO, null, false))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("primeiro valor fixo");
    }

    @Test
    void rejeitaBasesInvalidas() {
        assertThatThrownBy(() -> new BasePensao.PercentualDosRendimentos(BigDecimal.TEN, Dinheiro.ZERO))
                .isInstanceOf(IllegalArgumentException.class).hasMessageContaining("rendimentos");
        assertThatThrownBy(() -> new BasePensao.PercentualDosRendimentos(BigDecimal.ZERO, Dinheiro.de("100.00")))
                .isInstanceOf(IllegalArgumentException.class).hasMessageContaining("percentual");
        assertThatThrownBy(() -> new BasePensao.ValorFixo(new TreeMap<>()))
                .isInstanceOf(IllegalArgumentException.class).hasMessageContaining("valor fixo");
        TreeMap<YearMonth, Dinheiro> negativo = new TreeMap<>();
        negativo.put(YearMonth.of(2024, 1), Dinheiro.de("-1.00"));
        assertThatThrownBy(() -> new BasePensao.ValorFixo(negativo))
                .isInstanceOf(IllegalArgumentException.class).hasMessageContaining("positivo");
    }

    private static Pensao pensao(String percentual, int dia, YearMonth de, YearMonth ate) {
        return Pensao.emSalarioMinimo(new BigDecimal(percentual), dia, de, ate, InicioJuros.VENCIMENTO, null);
    }
}
