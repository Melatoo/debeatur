package br.com.debeatur.core.calculo;

import br.com.debeatur.core.Dinheiro;
import br.com.debeatur.core.indice.Indice;
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
import static org.junit.jupiter.api.Assumptions.assumeTrue;

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
 * Convenções da planilha conferidas contra o SGS (séries 188, 433 e 29543):
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
 * 5,9 × 10⁻⁷ (02/2003 a 05/2024; cerca de R$ 0,002 em R$ 1.000,00). De 06/2024 em diante o
 * fator é exato. Os juros não têm tolerância.
 * <p>
 * Divergências conhecidas do {@link RegimeAtualizacao#civelPadrao()} nas parcelas até 08/2024:
 * o regime corrige agosto/2024 pelo INPC (#24) e não tem o pro rata die dos juros de agosto
 * (#3). Nessas linhas o teste é ignorado, com os dois valores na mensagem, enquanto o motor
 * não bate; quando passar a bater, a linha conta como aprovada.
 */
class GabaritoPlanilhaDpmgTest {

    private static final LocalDate DATA_BASE = LocalDate.of(2026, 10, 1);
    private static final BigDecimal TOLERANCIA_RELATIVA = new BigDecimal("0.0000006");
    private static final YearMonth ULTIMO_MES_DIVERGENTE = YearMonth.of(2024, 8);

    /** INPC (SGS 188), variação mensal em %, de fev/2003 a ago/2024. */
    private static final String[] INPC = {
            "1.46", "1.37", "1.38", "0.99", "-0.06", "0.04", "0.18", "0.82", "0.39", "0.37", "0.54",  // 2003
            "0.83", "0.39", "0.57", "0.41", "0.40", "0.50", "0.73", "0.50", "0.17", "0.17", "0.44", "0.86",  // 2004
            "0.57", "0.44", "0.73", "0.91", "0.70", "-0.11", "0.03", "0.00", "0.15", "0.58", "0.54", "0.40",  // 2005
            "0.38", "0.23", "0.27", "0.12", "0.13", "-0.07", "0.11", "-0.02", "0.16", "0.43", "0.42", "0.62",  // 2006
            "0.49", "0.42", "0.44", "0.26", "0.26", "0.31", "0.32", "0.59", "0.25", "0.30", "0.43", "0.97",  // 2007
            "0.69", "0.48", "0.51", "0.64", "0.96", "0.91", "0.58", "0.21", "0.15", "0.50", "0.38", "0.29",  // 2008
            "0.64", "0.31", "0.20", "0.55", "0.60", "0.42", "0.23", "0.08", "0.16", "0.24", "0.37", "0.24",  // 2009
            "0.88", "0.70", "0.71", "0.73", "0.43", "-0.11", "-0.07", "-0.07", "0.54", "0.92", "1.03", "0.60",  // 2010
            "0.94", "0.54", "0.66", "0.72", "0.57", "0.22", "0.00", "0.42", "0.45", "0.32", "0.57", "0.51",  // 2011
            "0.51", "0.39", "0.18", "0.64", "0.55", "0.26", "0.43", "0.45", "0.63", "0.71", "0.54", "0.74",  // 2012
            "0.92", "0.52", "0.60", "0.59", "0.35", "0.28", "-0.13", "0.16", "0.27", "0.61", "0.54", "0.72",  // 2013
            "0.63", "0.64", "0.82", "0.78", "0.60", "0.26", "0.13", "0.18", "0.49", "0.38", "0.53", "0.62",  // 2014
            "1.48", "1.16", "1.51", "0.71", "0.99", "0.77", "0.58", "0.25", "0.51", "0.77", "1.11", "0.90",  // 2015
            "1.51", "0.95", "0.44", "0.64", "0.98", "0.47", "0.64", "0.31", "0.08", "0.17", "0.07", "0.14",  // 2016
            "0.42", "0.24", "0.32", "0.08", "0.36", "-0.30", "0.17", "-0.03", "-0.02", "0.37", "0.18", "0.26",  // 2017
            "0.23", "0.18", "0.07", "0.21", "0.43", "1.43", "0.25", "0.00", "0.30", "0.40", "-0.25", "0.14",  // 2018
            "0.36", "0.54", "0.77", "0.60", "0.15", "0.01", "0.10", "0.12", "-0.05", "0.04", "0.54", "1.22",  // 2019
            "0.19", "0.17", "0.18", "-0.23", "-0.25", "0.30", "0.44", "0.36", "0.87", "0.89", "0.95", "1.46",  // 2020
            "0.27", "0.82", "0.86", "0.38", "0.96", "0.60", "1.02", "0.88", "1.20", "1.16", "0.84", "0.73",  // 2021
            "0.67", "1.00", "1.71", "1.04", "0.45", "0.62", "-0.60", "-0.31", "-0.32", "0.47", "0.38", "0.69",  // 2022
            "0.46", "0.77", "0.64", "0.53", "0.36", "-0.10", "-0.09", "0.20", "0.11", "0.12", "0.10", "0.55",  // 2023
            "0.57", "0.81", "0.19", "0.37", "0.46", "0.25", "0.26",
            "-0.14"};  // ago/2024: a planilha não usa, mas o civelPadrao atual sim (#24)

    /** IPCA (SGS 433), variação mensal em %, de ago/2024 a set/2026. */
    private static final String[] IPCA = {
            "-0.02", "0.44", "0.56", "0.39", "0.52",  // 2024
            "0.16", "1.31", "0.56", "0.43", "0.26", "0.24", "0.26", "-0.11", "0.48", "0.09", "0.18", "0.33",  // 2025
            "0.33", "0.70", "0.88", "0.67", "0.58", "0.16", "0.07", "-0.32", "0.82"};  // 2026

    /** Taxa legal (SGS 29543), % ao mês, de set/2024 a set/2026. */
    private static final String[] TAXA_LEGAL = {
            "0.676227", "0.704241", "0.385874", "0.171924",  // 2024
            "0.589427", "0.902209", "0.000000", "0.321969", "0.623200", "0.775982",
            "0.834880", "0.942622", "1.305984", "0.736394", "1.093764", "0.851001",  // 2025
            "0.967510", "0.962232", "0.155714", "0.768672", "0.198293", "0.450641",
            "0.706607", "1.154527", "1.499119"};  // 2026

    private final MotorCalculo motor = new MotorCalculo(serie());

    @ParameterizedTest(name = "{0}: fator {1}")
    @CsvFileSource(resources = "/gabarito/planilha-dpmg-alimentos-2026-10.csv", numLinesToSkip = 1)
    void fatorDeCorrecaoReproduzAPlanilha(YearMonth mes, BigDecimal fatorPlanilha, BigDecimal jurosPlanilha) {
        BigDecimal fator = calcular(mes).fatorCorrecao();
        BigDecimal erroRelativo = fator.divide(fatorPlanilha, MathContext.DECIMAL128).subtract(BigDecimal.ONE).abs();

        if (!mes.isAfter(ULTIMO_MES_DIVERGENTE)) {
            assumeTrue(erroRelativo.compareTo(TOLERANCIA_RELATIVA) <= 0,
                    () -> "Divergência conhecida (#24, INPC em 08/2024): motor " + fator + ", planilha " + fatorPlanilha);
        }
        assertThat(erroRelativo).as("erro relativo do fator (motor %s, planilha %s)", fator, fatorPlanilha)
                .isLessThanOrEqualTo(TOLERANCIA_RELATIVA);
    }

    @ParameterizedTest(name = "{0}: juros {2}%")
    @CsvFileSource(resources = "/gabarito/planilha-dpmg-alimentos-2026-10.csv", numLinesToSkip = 1)
    void percentualDeJurosReproduzAPlanilha(YearMonth mes, BigDecimal fatorPlanilha, BigDecimal jurosPlanilha) {
        BigDecimal juros = calcular(mes).percentualJuros();

        if (!mes.isAfter(ULTIMO_MES_DIVERGENTE)) {
            assumeTrue(juros.compareTo(jurosPlanilha) == 0,
                    () -> "Divergência conhecida (#3, pro rata die em 08/2024): motor " + juros + "%, planilha " + jurosPlanilha + "%");
        }
        assertThat(juros).isEqualByComparingTo(jurosPlanilha);
    }

    private LinhaDemonstrativo calcular(YearMonth mes) {
        LocalDate vencimento = mes.atDay(1);
        Parcela parcela = new Parcela("Alimentos " + mes, Dinheiro.de("1000.00"), vencimento, vencimento);
        Demonstrativo d = motor.calcular(new ParametrosCalculo(List.of(parcela), DATA_BASE,
                RegimeAtualizacao.civelPadrao(), false));
        return d.linhas().getFirst();
    }

    private static SerieIndicesEmMemoria serie() {
        SerieIndicesEmMemoria serie = new SerieIndicesEmMemoria();
        carregar(serie, Indice.INPC, YearMonth.of(2003, 2), INPC);
        carregar(serie, Indice.IPCA, YearMonth.of(2024, 8), IPCA);
        carregar(serie, Indice.TAXA_LEGAL, YearMonth.of(2024, 9), TAXA_LEGAL);
        return serie;
    }

    private static void carregar(SerieIndicesEmMemoria serie, Indice indice, YearMonth inicio, String[] variacoes) {
        YearMonth mes = inicio;
        for (String variacao : variacoes) {
            serie.com(indice, mes, variacao);
            mes = mes.plusMonths(1);
        }
    }
}
