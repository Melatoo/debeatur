package br.com.debeatur.core.alimentos;

import br.com.debeatur.core.Dinheiro;
import br.com.debeatur.core.calculo.Demonstrativo;
import br.com.debeatur.core.calculo.LinhaDemonstrativo;
import br.com.debeatur.core.calculo.MotorCalculo;
import br.com.debeatur.core.calculo.ParametrosCalculo;
import br.com.debeatur.core.gabarito.SeriesPublicadas;
import br.com.debeatur.core.regime.RegimeAtualizacao;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.YearMonth;
import java.util.TreeMap;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Gabarito: pensão cuja base muda duas vezes (#108), conferida contra a planilha modelo de
 * alimentos da DPMG ({@code PLANILHA_MODELO_OUTUBRO_2026-TAXA-LEGAL.xls}, data-base 10/2026).
 * <p>
 * O gerador da DPMG não tem troca de base: quem calcula faz uma planilha por período. Cada linha
 * da planilha só depende do mês e do valor devido (corrigido = devido × "ÍNDICE CORREÇÃO", juros =
 * corrigido × "% JUROS"), então o cálculo com a base por períodos tem que dar as mesmas linhas das
 * três planilhas separadas. Os índices e os juros saem de {@code planilha-dpmg-alimentos-2026-10.csv};
 * o valor da parcela em salário mínimo, da aba "EVOLUÇÃO SALÁRIO MÍNIMO"
 * ({@code pensao-salario-minimo-dpmg-2026-10.csv}: 30% em 2025 = 455,40; 20% em 2026 = 324,20).
 * <p>
 * Caso: 30% do salário mínimo de 10/2025 a 12/2025, com 13º (dezembro em dobro, 910,80); revisional
 * para 20% do salário mínimo a partir de 01/2026; acordo de R$ 500,00 fixos a partir de 04/2026,
 * até 09/2026.
 */
class GabaritoBasePorPeriodoDpmgTest {

    private static final LocalDate DATA_BASE = LocalDate.of(2026, 10, 1);

    @Test
    void cadaPeriodoDaAsLinhasDaPlanilhaDaSuaBase() {
        TreeMap<YearMonth, BasePensao> mudancas = new TreeMap<>();
        mudancas.put(YearMonth.of(2026, 1), new BasePensao.PercentualDoSalarioMinimo(new BigDecimal("20")));
        TreeMap<YearMonth, Dinheiro> fixo = new TreeMap<>();
        fixo.put(YearMonth.of(2026, 4), Dinheiro.de("500.00"));
        mudancas.put(YearMonth.of(2026, 4), new BasePensao.ValorFixo(fixo));
        BasePensao base = new BasePensao.PorPeriodos(
                new BasePensao.PercentualDoSalarioMinimo(new BigDecimal("30")), mudancas);
        Pensao pensao = new Pensao(base, 1, YearMonth.of(2025, 10), YearMonth.of(2026, 9),
                InicioJuros.VENCIMENTO, null, true);

        Demonstrativo d = new MotorCalculo(SeriesPublicadas.carregar()).calcular(new ParametrosCalculo(
                pensao.parcelas(SeriesPublicadas.salarioMinimo()), DATA_BASE, RegimeAtualizacao.civelPadrao(), false));

        assertThat(d.linhas()).hasSize(12);
        // 30% do salário mínimo
        // 10/2025: 455,40 × 1,0457611967507188 = 476,24; × 9,544474% = 45,45
        assertLinha(d.linhas().get(0), "455.40", "476.24", "45.45");
        // 11/2025: × 1,0448208579785383 = 475,81; × 8,80808% = 41,91
        assertLinha(d.linhas().get(1), "455.40", "475.81", "41.91");
        // 12/2025 em dobro: 910,80 × 1,04294355957131 = 949,91; × 7,714316% = 73,28
        assertLinha(d.linhas().get(2), "910.80", "949.91", "73.28");
        // 20% do salário mínimo
        // 01/2026: 324,20 × 1,0395131661231036 = 337,01; × 6,863315% = 23,13
        assertLinha(d.linhas().get(3), "324.20", "337.01", "23.13");
        // 02/2026: × 1,0360940557391642 = 335,90; × 5,895805% = 19,80
        assertLinha(d.linhas().get(4), "324.20", "335.90", "19.80");
        // 03/2026: × 1,0288918130478295 = 333,57; × 4,933573% = 16,46
        assertLinha(d.linhas().get(5), "324.20", "333.57", "16.46");
        // R$ 500,00 fixos
        // 04/2026: × 1,0199165474304417 = 509,96; × 4,777859% = 24,37
        assertLinha(d.linhas().get(6), "500.00", "509.96", "24.37");
        // 05/2026: × 1,013128585904879 = 506,56; × 4,009187% = 20,31
        assertLinha(d.linhas().get(7), "500.00", "506.56", "20.31");
        // 06/2026: × 1,007286325218611 = 503,64; × 3,810894% = 19,19
        assertLinha(d.linhas().get(8), "500.00", "503.64", "19.19");
        // 07/2026: × 1,005677241632 = 502,84; × 3,360253% = 16,90
        assertLinha(d.linhas().get(9), "500.00", "502.84", "16.90");
        // 08/2026: × 1,00497376 = 502,49; × 2,653646% = 13,33
        assertLinha(d.linhas().get(10), "500.00", "502.49", "13.33");
        // 09/2026: × 1,0082 = 504,10; × 1,499119% = 7,56
        assertLinha(d.linhas().get(11), "500.00", "504.10", "7.56");

        // Soma das três planilhas: corrigido 5.938,03, juros 321,69, total 6.259,72
        assertThat(d.totalCorrigido()).isEqualTo(Dinheiro.de("5938.03"));
        assertThat(d.totalJuros()).isEqualTo(Dinheiro.de("321.69"));
        assertThat(d.debitoAtualizado()).isEqualTo(Dinheiro.de("6259.72"));
    }

    private static void assertLinha(LinhaDemonstrativo linha, String valor, String corrigido, String juros) {
        assertThat(linha.parcela().valor()).isEqualTo(Dinheiro.de(valor));
        assertThat(linha.valorCorrigido()).isEqualTo(Dinheiro.de(corrigido));
        assertThat(linha.valorJuros()).isEqualTo(Dinheiro.de(juros));
    }
}
