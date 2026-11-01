package br.com.debeatur.core.alimentos;

import br.com.debeatur.core.Dinheiro;
import br.com.debeatur.core.calculo.Parcela;
import org.junit.jupiter.api.Test;

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
}
