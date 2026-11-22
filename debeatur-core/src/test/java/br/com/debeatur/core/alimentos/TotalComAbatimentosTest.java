package br.com.debeatur.core.alimentos;

import br.com.debeatur.core.Dinheiro;
import br.com.debeatur.core.calculo.BaseHonorarios;
import br.com.debeatur.core.calculo.Demonstrativo;
import org.junit.jupiter.api.Test;

import java.time.LocalDate;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class TotalComAbatimentosTest {

    private static final LocalDate DATA = LocalDate.of(2026, 11, 10);

    @Test
    void abateONominalESoDepoisAplicaOArt523() {
        TotalComAbatimentos total = TotalComAbatimentos.de(Dinheiro.de("1000.00"), List.of(
                new Abatimento(DATA, Dinheiro.de("150.00"), "Bloqueio"),
                new Abatimento(DATA.plusDays(5), Dinheiro.de("50.05"), "Depósito")), true);

        assertThat(total.totalAbatido()).isEqualTo(Dinheiro.de("200.05"));
        assertThat(total.saldo()).isEqualTo(Dinheiro.de("799.95"));
        // 10% de 799,95 = 79,995 → 80,00
        assertThat(total.multaArt523()).isEqualTo(Dinheiro.de("80.00"));
        assertThat(total.honorariosArt523()).isEqualTo(Dinheiro.de("80.00"));
        assertThat(total.total()).isEqualTo(Dinheiro.de("959.95"));
    }

    @Test
    void honorariosSobreAMultaIncidemSobreOSaldoMaisAMulta() {
        TotalComAbatimentos total = TotalComAbatimentos.de(Dinheiro.de("1000.00"),
                List.of(new Abatimento(DATA, Dinheiro.de("200.05"), "Bloqueio")), true,
                BaseHonorarios.DEBITO_COM_MULTA);

        assertThat(total.multaArt523()).isEqualTo(Dinheiro.de("80.00"));
        // 10% de 799,95 + 80,00 = 87,995 → 88,00
        assertThat(total.honorariosArt523()).isEqualTo(Dinheiro.de("88.00"));
        assertThat(total.total()).isEqualTo(Dinheiro.de("967.95"));
    }

    @Test
    void semArt523OTotalEhOSaldo() {
        TotalComAbatimentos total = TotalComAbatimentos.de(Dinheiro.de("1000.00"),
                List.of(new Abatimento(DATA, Dinheiro.de("1000.00"), null)), false);

        assertThat(total.saldo()).isEqualTo(Dinheiro.ZERO);
        assertThat(total.total()).isEqualTo(Dinheiro.ZERO);
        assertThat(total.abatimentos().getFirst().origem()).isEmpty();
    }

    @Test
    void rejeitaAbatimentoMaiorQueODebito() {
        assertThatThrownBy(() -> TotalComAbatimentos.de(Dinheiro.de("100.00"),
                List.of(new Abatimento(DATA, Dinheiro.de("100.01"), "")), true))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("superam");
    }

    @Test
    void rejeitaAbatimentoNaoPositivo() {
        assertThatThrownBy(() -> new Abatimento(DATA, Dinheiro.ZERO, ""))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("positivo");
    }

    @Test
    void rejeitaDemonstrativoQueJaAplicaOArt523() {
        Demonstrativo comArt523 = new Demonstrativo(DATA, "teste", List.of(), Dinheiro.de("100.00"),
                Dinheiro.ZERO, Dinheiro.de("100.00"), Dinheiro.de("10.00"), Dinheiro.de("10.00"),
                Dinheiro.de("120.00"));

        assertThatThrownBy(() -> TotalComAbatimentos.de(comArt523, List.of(), true))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("art. 523");
    }
}
