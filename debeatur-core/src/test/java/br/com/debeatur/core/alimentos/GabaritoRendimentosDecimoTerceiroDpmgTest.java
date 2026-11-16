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
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Gabarito: planilha de alimentos da DPMG gerada no iadefensor em 15/11/2026 (modelo "Taxa
 * legal (Bacen)", data-base 10/2026), de 10/2025 a 09/2026, com 30% de rendimentos líquidos de
 * R$ 3.000,00, 13º salário, multa e honorários de 10% e um bloqueio de R$ 500,00 em 10/11/2026.
 * <p>
 * O .xlsx traz o devido, o "ÍNDICE CORREÇÃO" e o "% JUROS" de cada mês gravados como números, e
 * o Excel só aplica as fórmulas: corrigido = diferença × índice, juros = corrigido × % juros,
 * subtotal = soma, saldo = subtotal − abatimento, multa e honorários = saldo × 10%. Os valores
 * esperados são essas fórmulas sobre os números do arquivo, como o Excel os mostra em duas casas.
 * <p>
 * Convenções conferidas: a parcela é R$ 900,00 em todos os meses e a de dezembro entra em dobro;
 * o abatimento sai do subtotal pelo valor nominal; multa e honorários incidem sobre o saldo.
 */
class GabaritoRendimentosDecimoTerceiroDpmgTest {

    private static final LocalDate DATA_BASE = LocalDate.of(2026, 10, 1);
    private static final Dinheiro TOTAL_DA_PLANILHA = Dinheiro.de("14605.76");

    @Test
    void reproduzAPlanilhaComDecimoTerceiroEAbatimento() {
        Pensao pensao = new Pensao(new BasePensao.PercentualDosRendimentos(new BigDecimal("30"),
                Dinheiro.de("3000.00")), 1, YearMonth.of(2025, 10), YearMonth.of(2026, 9),
                InicioJuros.VENCIMENTO, null, true);

        Demonstrativo d = new MotorCalculo(SeriesPublicadas.carregar()).calcular(new ParametrosCalculo(
                pensao.parcelas(null), DATA_BASE, RegimeAtualizacao.civelPadrao(), false));

        // 10/2025: 900,00 × 1,0457611967507188 = 941,19; × 9,544474% = 89,83
        assertLinha(d.linhas().get(0), "900.00", "941.19", "89.83");
        // 12/2025 em dobro: 1.800,00 × 1,04294355957131 = 1.877,30; × 7,714316% = 144,82
        assertLinha(d.linhas().get(2), "1800.00", "1877.30", "144.82");
        // 09/2026: 900,00 × 1,0082 = 907,38; × 1,499119% = 13,60
        assertLinha(d.linhas().get(11), "900.00", "907.38", "13.60");
        assertThat(d.totalCorrigido()).isEqualTo(Dinheiro.de("12006.14"));
        assertThat(d.totalJuros()).isEqualTo(Dinheiro.de("665.33"));

        TotalComAbatimentos total = TotalComAbatimentos.de(d,
                List.of(new Abatimento(LocalDate.of(2026, 11, 10), Dinheiro.de("500.00"), "Bloqueio")), true);

        // 12.671,47 − 500,00 = 12.171,47; 10% = 1.217,15 de multa e de honorários
        assertThat(total.saldo()).isEqualTo(Dinheiro.de("12171.47"));
        assertThat(total.multaArt523()).isEqualTo(Dinheiro.de("1217.15"));
        assertThat(total.honorariosArt523()).isEqualTo(Dinheiro.de("1217.15"));
        // Divergência registrada (docs/gabarito.md, item 9): a planilha mostra 14.605,76 porque
        // soma multa e honorários sem arredondar (1.217,147 cada); o motor arredonda cada um.
        assertThat(total.total()).isEqualTo(TOTAL_DA_PLANILHA.mais(Dinheiro.de("0.01")));
    }

    private static void assertLinha(LinhaDemonstrativo linha, String valor, String corrigido, String juros) {
        assertThat(linha.parcela().valor()).isEqualTo(Dinheiro.de(valor));
        assertThat(linha.valorCorrigido()).isEqualTo(Dinheiro.de(corrigido));
        assertThat(linha.valorJuros()).isEqualTo(Dinheiro.de(juros));
    }
}
