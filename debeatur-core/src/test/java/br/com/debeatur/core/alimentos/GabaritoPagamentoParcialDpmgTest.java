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
 * Gabarito: fórmulas da planilha modelo de alimentos da DPMG
 * ({@code PLANILHA_MODELO_OUTUBRO_2026-TAXA-LEGAL.xls}) para o saldo após um pagamento parcial.
 */
class GabaritoPagamentoParcialDpmgTest {

    /**
     * Fórmulas da planilha de alimentos da DPMG (data-base 10/2026): o saldo do mês é
     * corrigido pelo "ÍNDICE CORREÇÃO" da linha e recebe o "% JUROS" sobre o corrigido. Em
     * 02/2025, 30% de R$ 1.518,00 = R$ 455,40; pago R$ 200,00; saldo R$ 255,40;
     * × 1,082110631649624 = R$ 276,37; × 15,251320% = R$ 42,15.
     */
    @Test
    void saldoAposPagamentoReproduzAPlanilha() {
        PensaoEmSalarioMinimo pensao = new PensaoEmSalarioMinimo(new BigDecimal("30"), 1,
                YearMonth.of(2025, 1), YearMonth.of(2025, 2), InicioJuros.VENCIMENTO, null);
        List<Parcela> saldos = new PagamentosParciais()
                .com(YearMonth.of(2025, 2), Dinheiro.de("200.00"))
                .abater(pensao.parcelas(SeriesPublicadas.salarioMinimo()));

        Demonstrativo d = new MotorCalculo(SeriesPublicadas.carregar()).calcular(new ParametrosCalculo(
                saldos, LocalDate.of(2026, 10, 1), RegimeAtualizacao.civelPadrao(), false));

        LinhaDemonstrativo janeiro = d.linhas().get(0);
        assertThat(janeiro.parcela().valor()).isEqualTo(Dinheiro.de("455.40"));
        assertThat(janeiro.valorCorrigido()).isEqualTo(Dinheiro.de("493.58"));
        assertThat(janeiro.valorJuros()).isEqualTo(Dinheiro.de("78.19"));

        LinhaDemonstrativo fevereiro = d.linhas().get(1);
        assertThat(fevereiro.parcela().valor()).isEqualTo(Dinheiro.de("255.40"));
        assertThat(fevereiro.valorCorrigido()).isEqualTo(Dinheiro.de("276.37"));
        assertThat(fevereiro.valorJuros()).isEqualTo(Dinheiro.de("42.15"));
    }
}
