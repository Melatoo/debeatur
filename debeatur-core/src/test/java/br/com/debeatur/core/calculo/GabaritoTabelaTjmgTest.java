package br.com.debeatur.core.calculo;

import br.com.debeatur.core.Dinheiro;
import br.com.debeatur.core.indice.Indice;
import br.com.debeatur.core.indice.SerieIndicesEmMemoria;
import br.com.debeatur.core.regime.Juros;
import br.com.debeatur.core.regime.RegimeAtualizacao;
import br.com.debeatur.core.regime.RegraPeriodo;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.YearMonth;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.within;

/**
 * Gabarito: fatores de atualização monetária da Corregedoria-Geral de Justiça do TJMG
 * (DJe/TJMG de 12/05/2026, edição 82/2026, vigentes de 12/05/2026 até a publicação do
 * novo INPC).
 * <p>
 * O fator da tabela para o mês M é o INPC acumulado de M (inclusive) até abril/2026, o
 * último índice publicado. É a mesma convenção do motor com data-base em maio/2026.
 * <p>
 * Divergência registrada: o motor acumula as variações do SGS, que têm duas casas, e a
 * tabela parte do número-índice do IBGE. A diferença em 2024–2026 chega a 2,5 × 10⁻⁷ no
 * fator (cerca de R$ 0,03 em R$ 100.000,00), acima do arredondamento da tabela em sete
 * casas. A tolerância abaixo documenta essa diferença.
 */
class GabaritoTabelaTjmgTest {

    private static final LocalDate DATA_BASE = LocalDate.of(2026, 5, 12);
    private static final BigDecimal TOLERANCIA = new BigDecimal("0.0000003");

    private static final RegimeAtualizacao SO_INPC = new RegimeAtualizacao("INPC sem juros", List.of(
            new RegraPeriodo(YearMonth.of(2024, 1), null, Indice.INPC, new Juros.SemJuros())));

    /** INPC (SGS 188), variação mensal em %, de jan/2024 a abr/2026. */
    private static final String[] INPC = {
            "0.57", "0.81", "0.19", "0.37", "0.46", "0.25", "0.26", "-0.14", "0.48", "0.61", "0.33", "0.48",
            "0.00", "1.48", "0.51", "0.48", "0.35", "0.23", "0.21", "-0.21", "0.52", "0.03", "0.03", "0.21",
            "0.39", "0.56", "0.91", "0.81"};

    private final MotorCalculo motor = new MotorCalculo(serieInpc());

    @ParameterizedTest(name = "{0}: fator {1}")
    @CsvSource({
            "2024-01, 1.1178629",
            "2024-02, 1.1115273",
            "2024-03, 1.1025964",
            "2024-04, 1.1005051",
            "2024-05, 1.0964486",
            "2024-06, 1.0914279",
            "2024-07, 1.0887063",
            "2024-08, 1.0858830",
            "2024-09, 1.0874051",
            "2024-10, 1.0822104",
            "2024-11, 1.0756493",
            "2024-12, 1.0721109",
            "2025-01, 1.0669896",
            "2025-02, 1.0669896",
            "2025-03, 1.0514284",
            "2025-04, 1.0460934",
            "2025-05, 1.0410961",
            "2025-06, 1.0374647",
            "2025-07, 1.0350843",
            "2025-08, 1.0329151",
            "2025-09, 1.0350890",
            "2025-10, 1.0297342",
            "2025-11, 1.0294253",
            "2025-12, 1.0291166",
            "2026-01, 1.0269599",
            "2026-02, 1.0229705",
            "2026-03, 1.0172737",
            "2026-04, 1.0081000"})
    void fatorDeCorrecaoReproduzATabelaDaCorregedoria(String mes, BigDecimal fatorTjmg) {
        Parcela parcela = new Parcela("Gabarito", Dinheiro.de("100000.00"),
                YearMonth.parse(mes).atDay(1), null);

        Demonstrativo d = motor.calcular(new ParametrosCalculo(List.of(parcela), DATA_BASE, SO_INPC, false));

        assertThat(d.linhas().getFirst().fatorCorrecao()).isCloseTo(fatorTjmg, within(TOLERANCIA));
    }

    private static SerieIndicesEmMemoria serieInpc() {
        SerieIndicesEmMemoria serie = new SerieIndicesEmMemoria();
        YearMonth mes = YearMonth.of(2024, 1);
        for (String variacao : INPC) {
            serie.com(Indice.INPC, mes, variacao);
            mes = mes.plusMonths(1);
        }
        return serie;
    }
}
