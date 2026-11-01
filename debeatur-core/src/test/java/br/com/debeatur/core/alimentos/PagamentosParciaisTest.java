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
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class PagamentosParciaisTest {

    private static final LocalDate DIA_10_JAN = LocalDate.of(2024, 1, 10);
    private static final LocalDate DIA_10_FEV = LocalDate.of(2024, 2, 10);

    private final List<Parcela> parcelas = List.of(
            new Parcela("Alimentos 01/2024", Dinheiro.de("423.60"), DIA_10_JAN, DIA_10_JAN),
            new Parcela("Alimentos 02/2024", Dinheiro.de("423.60"), DIA_10_FEV, DIA_10_FEV));

    @Test
    void abateOPagamentoDaParcelaDoMesMantendoAsDatas() {
        List<Parcela> saldos = new PagamentosParciais()
                .com(YearMonth.of(2024, 2), Dinheiro.de("200.00"))
                .abater(parcelas);

        assertThat(saldos.getFirst()).isEqualTo(parcelas.getFirst());
        assertThat(saldos.get(1)).isEqualTo(new Parcela("Alimentos 02/2024 (saldo após pagamento parcial)",
                Dinheiro.de("223.60"), DIA_10_FEV, DIA_10_FEV));
    }

    @Test
    void parcelaPagaPorInteiroFicaComSaldoZero() {
        List<Parcela> saldos = new PagamentosParciais()
                .com(YearMonth.of(2024, 1), Dinheiro.de("423.60"))
                .abater(parcelas);

        assertThat(saldos.getFirst().descricao()).isEqualTo("Alimentos 01/2024 (quitada)");
        assertThat(saldos.getFirst().valor()).isEqualTo(Dinheiro.ZERO);
    }

    @Test
    void semPagamentosAsParcelasNaoMudam() {
        assertThat(new PagamentosParciais().abater(parcelas)).isEqualTo(parcelas);
    }

    @Test
    void rejeitaPagamentoMaiorQueAParcela() {
        PagamentosParciais pagamentos = new PagamentosParciais().com(YearMonth.of(2024, 1), Dinheiro.de("423.61"));

        assertThatThrownBy(() -> pagamentos.abater(parcelas))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("maior que a parcela");
    }

    @Test
    void rejeitaPagamentoEmMesSemParcela() {
        PagamentosParciais pagamentos = new PagamentosParciais().com(YearMonth.of(2024, 3), Dinheiro.de("100.00"));

        assertThatThrownBy(() -> pagamentos.abater(parcelas))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("sem parcela no mês: [2024-03]");
    }

    @Test
    void rejeitaPagamentoAmbiguoEntreDuasParcelasDoMesmoMes() {
        List<Parcela> duasNoMes = List.of(parcelas.getFirst(),
                new Parcela("Diferença 01/2024", Dinheiro.de("50.00"), DIA_10_JAN, DIA_10_JAN));
        PagamentosParciais pagamentos = new PagamentosParciais().com(YearMonth.of(2024, 1), Dinheiro.de("100.00"));

        assertThatThrownBy(() -> pagamentos.abater(duasNoMes))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("Mais de uma parcela");
    }

    @Test
    void rejeitaPagamentoNaoPositivoOuRepetido() {
        YearMonth jan = YearMonth.of(2024, 1);
        assertThatThrownBy(() -> new PagamentosParciais().com(jan, Dinheiro.ZERO))
                .isInstanceOf(IllegalArgumentException.class).hasMessageContaining("positivo");
        assertThatThrownBy(() -> new PagamentosParciais().com(jan, Dinheiro.de("1.00")).com(jan, Dinheiro.de("2.00")))
                .isInstanceOf(IllegalArgumentException.class).hasMessageContaining("mais de uma vez");
    }

    /**
     * Fórmulas da planilha de alimentos da DPMG (data-base 10/2026): o saldo do mês é
     * corrigido pelo "ÍNDICE CORREÇÃO" da linha e recebe o "% JUROS" sobre o corrigido. Em
     * 02/2025, 30% de R$ 1.518,00 = R$ 455,40; pago R$ 200,00; saldo R$ 255,40;
     * × 1,082110631649624 = R$ 276,37; × 15,251320% = R$ 42,15.
     */
    @Test
    void saldoAposPagamentoReproduzAsFormulasDaPlanilhaDaDpmg() {
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
