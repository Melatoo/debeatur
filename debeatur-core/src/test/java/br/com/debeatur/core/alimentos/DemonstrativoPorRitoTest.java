package br.com.debeatur.core.alimentos;

import br.com.debeatur.core.Dinheiro;
import br.com.debeatur.core.calculo.Demonstrativo;
import br.com.debeatur.core.calculo.LinhaDemonstrativo;
import br.com.debeatur.core.calculo.Parcela;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Só a classificação por vencimento e a soma das linhas: os valores das linhas são fictícios
 * e não passam pelo motor.
 */
class DemonstrativoPorRitoTest {

    private static final LocalDate AJUIZAMENTO = LocalDate.of(2024, 6, 15);

    @Test
    void prisaoFicaComAsTresUltimasAnterioresEAsQueVencemNoCurso() {
        Demonstrativo demonstrativo = demonstrativo(
                linha("01/2024", LocalDate.of(2024, 1, 10), "100.00", "10.00"),
                linha("02/2024", LocalDate.of(2024, 2, 10), "100.00", "9.00"),
                linha("03/2024", LocalDate.of(2024, 3, 10), "100.00", "8.00"),
                linha("04/2024", LocalDate.of(2024, 4, 10), "100.00", "7.00"),
                linha("05/2024", LocalDate.of(2024, 5, 10), "100.00", "6.00"),
                linha("06/2024", LocalDate.of(2024, 6, 10), "100.00", "5.00"),
                linha("07/2024", LocalDate.of(2024, 7, 10), "100.00", "4.00"),
                linha("08/2024", LocalDate.of(2024, 8, 10), "100.00", "3.00"));

        DemonstrativoPorRito porRito = DemonstrativoPorRito.separar(demonstrativo, AJUIZAMENTO, false);

        assertThat(descricoes(porRito.subtotal(Rito.PRISAO)))
                .containsExactly("04/2024", "05/2024", "06/2024", "07/2024", "08/2024");
        assertThat(descricoes(porRito.subtotal(Rito.PENHORA)))
                .containsExactly("01/2024", "02/2024", "03/2024");
        assertThat(porRito.prisao().totalCorrigido()).isEqualTo(Dinheiro.de("500.00"));
        assertThat(porRito.prisao().totalJuros()).isEqualTo(Dinheiro.de("25.00"));
        assertThat(porRito.prisao().debitoAtualizado()).isEqualTo(Dinheiro.de("525.00"));
        assertThat(porRito.penhora().debitoAtualizado()).isEqualTo(Dinheiro.de("327.00"));
    }

    @Test
    void osSubtotaisSomamODebitoAtualizado() {
        Demonstrativo demonstrativo = demonstrativo(
                linha("01/2024", LocalDate.of(2024, 1, 10), "423.61", "50.83"),
                linha("02/2024", LocalDate.of(2024, 2, 10), "423.15", "46.55"),
                linha("03/2024", LocalDate.of(2024, 3, 10), "422.97", "42.30"),
                linha("04/2024", LocalDate.of(2024, 4, 10), "422.49", "38.02"),
                linha("05/2024", LocalDate.of(2024, 5, 10), "422.07", "33.77"));

        DemonstrativoPorRito porRito = DemonstrativoPorRito.separar(demonstrativo, AJUIZAMENTO, false);

        assertThat(porRito.prisao().debitoAtualizado().mais(porRito.penhora().debitoAtualizado()))
                .isEqualTo(demonstrativo.debitoAtualizado());
        assertThat(porRito.prisao().totalCorrigido().mais(porRito.penhora().totalCorrigido()))
                .isEqualTo(demonstrativo.totalCorrigido());
        assertThat(porRito.prisao().totalJuros().mais(porRito.penhora().totalJuros()))
                .isEqualTo(demonstrativo.totalJuros());
    }

    @Test
    void parcelaQueVenceNoDiaDoAjuizamentoContaComoAnterior() {
        Demonstrativo demonstrativo = demonstrativo(
                linha("03/2024", LocalDate.of(2024, 3, 15), "100.00", "0.00"),
                linha("04/2024", LocalDate.of(2024, 4, 15), "100.00", "0.00"),
                linha("05/2024", LocalDate.of(2024, 5, 15), "100.00", "0.00"),
                linha("06/2024", LocalDate.of(2024, 6, 15), "100.00", "0.00"));

        DemonstrativoPorRito porRito = DemonstrativoPorRito.separar(demonstrativo, AJUIZAMENTO, false);

        assertThat(descricoes(porRito.prisao())).containsExactly("04/2024", "05/2024", "06/2024");
        assertThat(descricoes(porRito.penhora())).containsExactly("03/2024");
    }

    @Test
    void parcelaQuitadaOcupaSeuLugarEntreAsTresUltimas() {
        Demonstrativo demonstrativo = demonstrativo(
                linha("02/2024", LocalDate.of(2024, 2, 10), "100.00", "0.00"),
                linha("03/2024", LocalDate.of(2024, 3, 10), "100.00", "0.00"),
                linha("04/2024 (quitada)", LocalDate.of(2024, 4, 10), "0.00", "0.00"),
                linha("05/2024", LocalDate.of(2024, 5, 10), "100.00", "0.00"));

        DemonstrativoPorRito porRito = DemonstrativoPorRito.separar(demonstrativo, AJUIZAMENTO, false);

        assertThat(descricoes(porRito.prisao())).containsExactly("03/2024", "04/2024 (quitada)", "05/2024");
        assertThat(descricoes(porRito.penhora())).containsExactly("02/2024");
    }

    @Test
    void comAteTresParcelasTudoVaiParaAPrisao() {
        Demonstrativo demonstrativo = demonstrativo(
                linha("04/2024", LocalDate.of(2024, 4, 10), "100.00", "0.00"),
                linha("05/2024", LocalDate.of(2024, 5, 10), "100.00", "0.00"));

        DemonstrativoPorRito porRito = DemonstrativoPorRito.separar(demonstrativo, AJUIZAMENTO, false);

        assertThat(porRito.prisao().linhas()).hasSize(2);
        assertThat(porRito.penhora().linhas()).isEmpty();
        assertThat(porRito.penhora().debitoAtualizado()).isEqualTo(Dinheiro.ZERO);
    }

    @Test
    void parcelasIguaisContamSeparadamente() {
        LinhaDemonstrativo igual = linha("Alimentos", LocalDate.of(2024, 5, 10), "100.00", "0.00");
        Demonstrativo demonstrativo = demonstrativo(
                linha("Alimentos", LocalDate.of(2024, 5, 10), "100.00", "0.00"), igual, igual, igual);

        DemonstrativoPorRito porRito = DemonstrativoPorRito.separar(demonstrativo, AJUIZAMENTO, false);

        assertThat(porRito.prisao().linhas()).hasSize(3);
        assertThat(porRito.penhora().linhas()).hasSize(1);
    }

    @Test
    void mantemAOrdemDoDemonstrativoEmCadaRito() {
        Demonstrativo demonstrativo = demonstrativo(
                linha("05/2024", LocalDate.of(2024, 5, 10), "100.00", "0.00"),
                linha("01/2024", LocalDate.of(2024, 1, 10), "100.00", "0.00"),
                linha("04/2024", LocalDate.of(2024, 4, 10), "100.00", "0.00"),
                linha("03/2024", LocalDate.of(2024, 3, 10), "100.00", "0.00"));

        DemonstrativoPorRito porRito = DemonstrativoPorRito.separar(demonstrativo, AJUIZAMENTO, false);

        assertThat(descricoes(porRito.prisao())).containsExactly("05/2024", "04/2024", "03/2024");
        assertThat(descricoes(porRito.penhora())).containsExactly("01/2024");
        assertThat(porRito.ritoDasLinhas()).containsExactly(Rito.PRISAO, Rito.PENHORA, Rito.PRISAO, Rito.PRISAO);
    }

    @Test
    void multaEHonorariosDoArt523IncidemSoSobreAPenhora() {
        Demonstrativo demonstrativo = demonstrativo(
                linha("01/2024", LocalDate.of(2024, 1, 10), "423.61", "50.83"),
                linha("02/2024", LocalDate.of(2024, 2, 10), "423.15", "46.55"),
                linha("03/2024", LocalDate.of(2024, 3, 10), "422.97", "42.30"),
                linha("04/2024", LocalDate.of(2024, 4, 10), "422.49", "38.02"),
                linha("05/2024", LocalDate.of(2024, 5, 10), "422.07", "33.77"));

        DemonstrativoPorRito porRito = DemonstrativoPorRito.separar(demonstrativo, AJUIZAMENTO, true);

        // penhora: 423.61 + 50.83 + 423.15 + 46.55 = 944.14; 10% = 94.414 → 94.41
        assertThat(porRito.penhora().debitoAtualizado()).isEqualTo(Dinheiro.de("944.14"));
        assertThat(porRito.multaArt523()).isEqualTo(Dinheiro.de("94.41"));
        assertThat(porRito.honorariosArt523()).isEqualTo(Dinheiro.de("94.41"));
        assertThat(porRito.total()).isEqualTo(demonstrativo.debitoAtualizado().mais(Dinheiro.de("188.82")));
        // 944.14 + 94.41 + 94.41
        assertThat(porRito.totalPenhora()).isEqualTo(Dinheiro.de("1132.96"));
        assertThat(porRito.prisao().debitoAtualizado().mais(porRito.totalPenhora())).isEqualTo(porRito.total());
    }

    @Test
    void semArt523OTotalEhODebitoAtualizado() {
        Demonstrativo demonstrativo = demonstrativo(
                linha("01/2024", LocalDate.of(2024, 1, 10), "100.00", "10.00"),
                linha("05/2024", LocalDate.of(2024, 5, 10), "100.00", "5.00"));

        DemonstrativoPorRito porRito = DemonstrativoPorRito.separar(demonstrativo, AJUIZAMENTO, false);

        assertThat(porRito.multaArt523()).isEqualTo(Dinheiro.ZERO);
        assertThat(porRito.honorariosArt523()).isEqualTo(Dinheiro.ZERO);
        assertThat(porRito.total()).isEqualTo(demonstrativo.debitoAtualizado());
    }

    @Test
    void rejeitaDemonstrativoQueJaAplicaOArt523() {
        Demonstrativo semArt523 = demonstrativo(linha("01/2024", LocalDate.of(2024, 1, 10), "100.00", "0.00"));
        Demonstrativo comArt523 = new Demonstrativo(semArt523.dataBase(), semArt523.regime(), semArt523.linhas(),
                semArt523.totalCorrigido(), semArt523.totalJuros(), semArt523.debitoAtualizado(),
                Dinheiro.de("10.00"), Dinheiro.de("10.00"), Dinheiro.de("120.00"));

        assertThatThrownBy(() -> DemonstrativoPorRito.separar(comArt523, AJUIZAMENTO, true))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("art. 523");
    }

    private static LinhaDemonstrativo linha(String descricao, LocalDate vencimento, String corrigido, String juros) {
        Dinheiro valorCorrigido = Dinheiro.de(corrigido);
        Dinheiro valorJuros = Dinheiro.de(juros);
        return new LinhaDemonstrativo(new Parcela(descricao, valorCorrigido, vencimento, vencimento),
                BigDecimal.ONE, valorCorrigido, BigDecimal.ZERO, valorJuros, valorCorrigido.mais(valorJuros));
    }

    private static Demonstrativo demonstrativo(LinhaDemonstrativo... linhas) {
        List<LinhaDemonstrativo> lista = new ArrayList<>(List.of(linhas));
        Dinheiro corrigido = Dinheiro.ZERO;
        Dinheiro juros = Dinheiro.ZERO;
        for (LinhaDemonstrativo l : lista) {
            corrigido = corrigido.mais(l.valorCorrigido());
            juros = juros.mais(l.valorJuros());
        }
        Dinheiro debito = corrigido.mais(juros);
        return new Demonstrativo(LocalDate.of(2024, 9, 1), "teste", lista, corrigido, juros, debito,
                Dinheiro.ZERO, Dinheiro.ZERO, debito);
    }

    private static List<String> descricoes(DemonstrativoPorRito.Subtotal subtotal) {
        return subtotal.linhas().stream().map(l -> l.parcela().descricao()).toList();
    }
}
