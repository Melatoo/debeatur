package br.com.debeatur.core.regime;

import br.com.debeatur.core.indice.Indice;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.YearMonth;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatIllegalArgumentException;

class RegimeAtualizacaoTest {

    private static final Juros UM_POR_CENTO = new Juros.TaxaFixa(BigDecimal.ONE);

    private static RegraPeriodo regra(String inicio, String fim) {
        return new RegraPeriodo(YearMonth.parse(inicio), fim == null ? null : YearMonth.parse(fim),
                Indice.INPC, UM_POR_CENTO);
    }

    @Test
    void aceitaRegrasContiguas() {
        RegimeAtualizacao regime = new RegimeAtualizacao("Título", List.of(
                regra("2020-01", "2021-12"), regra("2022-01", "2022-01"), regra("2022-02", null)));

        assertThat(regime.regraPara(YearMonth.of(2022, 1)).inicio()).isEqualTo(YearMonth.of(2022, 1));
    }

    @Test
    void oRegimePadraoEhValido() {
        assertThat(RegimeAtualizacao.civelPadrao().regras()).hasSize(3);
    }

    @Test
    void recusaRegrasSobrepostas() {
        assertThatIllegalArgumentException()
                .isThrownBy(() -> new RegimeAtualizacao("Título", List.of(regra("2020-01", "2021-12"), regra("2021-06", null))))
                .withMessageContaining("2021-06")
                .withMessageContaining("2022-01");
    }

    @Test
    void recusaLacunaEntreRegras() {
        assertThatIllegalArgumentException()
                .isThrownBy(() -> new RegimeAtualizacao("Título", List.of(regra("2020-01", "2021-12"), regra("2022-03", null))))
                .withMessageContaining("2022-03");
    }

    @Test
    void recusaRegrasForaDeOrdem() {
        assertThatIllegalArgumentException()
                .isThrownBy(() -> new RegimeAtualizacao("Título", List.of(regra("2022-01", null), regra("2020-01", "2021-12"))))
                .withMessageContaining("sem fim");
    }

    @Test
    void recusaRegraSemFimAntesDaUltima() {
        assertThatIllegalArgumentException()
                .isThrownBy(() -> new RegimeAtualizacao("Título", List.of(regra("2020-01", null), regra("2022-01", null))))
                .withMessageContaining("Só a última regra");
    }

    @Test
    void recusaRegimeSemRegras() {
        assertThatIllegalArgumentException().isThrownBy(() -> new RegimeAtualizacao("Título", List.of()));
    }

    @Test
    void cobreOPeriodoEntreAPrimeiraEAUltimaRegra() {
        RegimeAtualizacao fechado = new RegimeAtualizacao("Título", List.of(regra("2020-01", "2021-12"), regra("2022-01", "2022-06")));

        assertThat(fechado.cobre(YearMonth.of(2020, 1), YearMonth.of(2022, 6))).isTrue();
        assertThat(fechado.cobre(YearMonth.of(2019, 12), YearMonth.of(2022, 6))).isFalse();
        assertThat(fechado.cobre(YearMonth.of(2020, 1), YearMonth.of(2022, 7))).isFalse();
    }

    @Test
    void regraSemFimCobreQualquerMesPosterior() {
        RegimeAtualizacao aberto = new RegimeAtualizacao("Título", List.of(regra("2020-01", null)));

        assertThat(aberto.cobre(YearMonth.of(2020, 1), YearMonth.of(2100, 12))).isTrue();
    }
}
