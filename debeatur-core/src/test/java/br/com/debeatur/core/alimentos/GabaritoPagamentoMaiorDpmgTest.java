package br.com.debeatur.core.alimentos;

import br.com.debeatur.core.Dinheiro;
import br.com.debeatur.core.calculo.Demonstrativo;
import br.com.debeatur.core.calculo.LinhaDemonstrativo;
import br.com.debeatur.core.calculo.MotorCalculo;
import br.com.debeatur.core.calculo.ParametrosCalculo;
import br.com.debeatur.core.calculo.Parcela;
import br.com.debeatur.core.gabarito.SeriesPublicadas;
import br.com.debeatur.core.regime.RegimeAtualizacao;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.YearMonth;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Gabarito: planilha de alimentos da DPMG gerada no iadefensor em 23/11/2026 (modelo "Taxa
 * legal (Bacen)", data-base 11/2026), com 30% do salário mínimo de 01/2025 a 04/2025 e
 * R$ 1.000,00 pagos em 02/2025, contra a parcela de R$ 455,40 (#112).
 * <p>
 * O .xlsx traz o devido, o pago, o "ÍNDICE CORREÇÃO" e o "% JUROS" de cada mês gravados como
 * números, e o Excel aplica: diferença = pago − devido, corrigido = diferença × índice, juros =
 * corrigido × % juros, total = corrigido + juros, subtotal = soma. A planilha mostra o débito com
 * sinal negativo; aqui ele vai com o sinal do Debeatur. Em 02/2025 a diferença é positiva
 * (R$ 544,60): o excedente vira crédito corrigido e com juros desde o próprio mês, que abate o
 * subtotal, sem ir para outras parcelas.
 */
class GabaritoPagamentoMaiorDpmgTest {

    @Test
    void excedenteViraCreditoNoProprioMesComoNaPlanilha() {
        Pensao pensao = Pensao.emSalarioMinimo(new BigDecimal("30"), 1,
                YearMonth.of(2025, 1), YearMonth.of(2025, 4), InicioJuros.VENCIMENTO, null);
        List<Parcela> saldos = new PagamentosParciais(ExcedentePagamento.CREDITO_NO_MES)
                .com(YearMonth.of(2025, 2), Dinheiro.de("1000.00"))
                .abater(pensao.parcelas(SeriesPublicadas.salarioMinimo()));

        Demonstrativo d = new MotorCalculo(SeriesPublicadas.carregar()).calcular(new ParametrosCalculo(
                saldos, LocalDate.of(2026, 11, 1), RegimeAtualizacao.civelPadrao(), false));

        // 01/2025: 455,40 × 1,085359387472387 = 494,27; × 16,219767% = 80,17
        assertLinha(d.linhas().get(0), "455.40", "494.27", "80.17");
        // 02/2025: 455,40 − 1.000,00 = −544,60; × 1,083625586533933 = −590,14; × 15,63034% = −92,24
        assertLinha(d.linhas().get(1), "-544.60", "-590.14", "-92.24");
        // 03/2025: × 1,069613647748429 = 487,10; × 14,728131% = 71,74
        assertLinha(d.linhas().get(2), "455.40", "487.10", "71.74");
        // 04/2025: × 1,063657167609814 = 484,39; × 14,728131% = 71,34
        assertLinha(d.linhas().get(3), "455.40", "484.39", "71.34");

        // Subtotal da planilha: corrigido 875,62, juros 131,01, total 1.006,63
        assertThat(d.totalCorrigido()).isEqualTo(Dinheiro.de("875.62"));
        assertThat(d.totalJuros()).isEqualTo(Dinheiro.de("131.01"));
        assertThat(d.debitoAtualizado()).isEqualTo(Dinheiro.de("1006.63"));
    }

    private static void assertLinha(LinhaDemonstrativo linha, String valor, String corrigido, String juros) {
        assertThat(linha.parcela().valor()).isEqualTo(Dinheiro.de(valor));
        assertThat(linha.valorCorrigido()).isEqualTo(Dinheiro.de(corrigido));
        assertThat(linha.valorJuros()).isEqualTo(Dinheiro.de(juros));
    }
}
