package br.com.debeatur.core.calculo;

import br.com.debeatur.core.Dinheiro;
import br.com.debeatur.core.gabarito.SeriesPublicadas;
import br.com.debeatur.core.indice.SerieIndicesEmMemoria;
import br.com.debeatur.core.regime.RegimeAtualizacao;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvFileSource;

import java.math.BigDecimal;
import java.math.MathContext;
import java.time.LocalDate;
import java.time.YearMonth;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Gabarito: planilha modelo de alimentos da Defensoria Pública de MG
 * ({@code PLANILHA_MODELO_OUTUBRO_2026-TAXA-LEGAL.xls}, data-base 10/2026), com uma parcela
 * por mês de 02/2003 a 09/2026.
 * <p>
 * O CSV transcreve as colunas "ÍNDICE CORREÇÃO" (como armazenada na planilha) e "% JUROS"
 * (fração convertida em pontos percentuais e arredondada a seis casas, a precisão da taxa
 * legal). Notas da planilha: correção pela tabela da CGJ/MG válida até 29/08/24 e, a partir
 * de 30/08/24, pelo IPCA; juros de 12% a.a. até 29/08/24 e taxa legal (art. 406 do CC) a
 * partir de 30/08/24.
 * <p>
 * Convenções da planilha conferidas contra o SGS (séries fixadas em {@link SeriesPublicadas}):
 * <ul>
 *   <li>O fator do mês M acumula as variações de M até setembro/2026, como no motor.</li>
 *   <li>Correção: INPC até 07/2024 e IPCA a partir de 08/2024. A "tabela válida até
 *       29/08/24" é a edição de agosto, cujo último INPC é o de julho.</li>
 *   <li>Juros: 1% a.m. até 07/2024; em 08/2024, 1,005752% (29/30 de 1%, arredondado a
 *       0,9667%, mais 2/31 da taxa legal de agosto, 0,605306%); a partir de 09/2024, a taxa
 *       legal do BCB.</li>
 * </ul>
 * <p>
 * Tolerância do fator: até 05/2024 a planilha parte do número-índice da tabela da CGJ e o
 * motor acumula as variações do SGS, que têm duas casas. A diferença relativa chega a
 * 5,9 × 10⁻⁷ (cerca de R$ 0,002 em R$ 1.000,00). De 06/2024 em diante o fator é exato, a
 * menos da precisão de ponto flutuante da planilha. Os juros não têm tolerância.
 */
class GabaritoPlanilhaDpmgTest {

    private static final MathContext PRECISAO = MathContext.DECIMAL128;
    private static final LocalDate DATA_BASE = LocalDate.of(2026, 10, 1);

    /** Último mês em que a planilha parte do número-índice da tabela da CGJ. */
    private static final YearMonth ULTIMO_MES_TABELA_CGJ = YearMonth.of(2024, 5);
    private static final BigDecimal TOLERANCIA_TABELA_CGJ = new BigDecimal("0.0000006");
    /** Folga para a precisão de ponto flutuante do fator armazenado na planilha. */
    private static final BigDecimal TOLERANCIA_EXATA = new BigDecimal("0.000000000001");

    private static final SerieIndicesEmMemoria SERIE = SeriesPublicadas.carregar();

    private final MotorCalculo motor = new MotorCalculo(SERIE);

    @ParameterizedTest(name = "{0}: fator {1}")
    @CsvFileSource(resources = "/gabarito/planilha-dpmg-alimentos-2026-10.csv", numLinesToSkip = 1)
    void fatorDeCorrecaoReproduzAPlanilha(YearMonth mes, BigDecimal fatorPlanilha, BigDecimal jurosPlanilha) {
        BigDecimal tolerancia = mes.isAfter(ULTIMO_MES_TABELA_CGJ) ? TOLERANCIA_EXATA : TOLERANCIA_TABELA_CGJ;

        BigDecimal fator = calcular(mes).fatorCorrecao();
        BigDecimal erroRelativo = fator.divide(fatorPlanilha, PRECISAO).subtract(BigDecimal.ONE).abs();

        assertThat(erroRelativo)
                .as("erro relativo do fator (motor %s, planilha %s)", fator, fatorPlanilha)
                .isLessThanOrEqualTo(tolerancia);
    }

    @ParameterizedTest(name = "{0}: juros {2}%")
    @CsvFileSource(resources = "/gabarito/planilha-dpmg-alimentos-2026-10.csv", numLinesToSkip = 1)
    void percentualDeJurosReproduzAPlanilha(YearMonth mes, BigDecimal fatorPlanilha, BigDecimal jurosPlanilha) {
        assertThat(calcular(mes).percentualJuros())
                .as("percentual de juros (planilha %s%%)", jurosPlanilha)
                .isEqualByComparingTo(jurosPlanilha);
    }

    private LinhaDemonstrativo calcular(YearMonth mes) {
        LocalDate vencimento = mes.atDay(1);
        Parcela parcela = new Parcela("Alimentos " + mes, Dinheiro.de("1000.00"), vencimento, vencimento);
        Demonstrativo d = motor.calcular(new ParametrosCalculo(List.of(parcela), DATA_BASE,
                RegimeAtualizacao.civelPadrao(), false));
        return d.linhas().getFirst();
    }
}
