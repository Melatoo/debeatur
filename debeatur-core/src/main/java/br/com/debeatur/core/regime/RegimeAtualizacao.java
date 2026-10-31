package br.com.debeatur.core.regime;

import br.com.debeatur.core.indice.Indice;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.YearMonth;
import java.util.List;

/**
 * Conjunto de regras que define como um débito é atualizado ao longo do tempo.
 * <p>
 * A sentença pode fixar critérios próprios (a coisa julgada prevalece), por isso o
 * regime é um dado do cálculo e não uma regra fixa do sistema.
 */
public record RegimeAtualizacao(String nome, List<RegraPeriodo> regras) {

    /** Início do Plano Real: primeiro mês coberto pelos regimes de fábrica. */
    private static final YearMonth INICIO_REAL = YearMonth.of(1994, 7);

    public RegimeAtualizacao {
        regras = List.copyOf(regras);
        if (regras.isEmpty()) {
            throw new IllegalArgumentException("O regime precisa de ao menos uma regra");
        }
    }

    public RegraPeriodo regraPara(YearMonth mes) {
        return regras.stream()
                .filter(r -> r.abrange(mes))
                .findFirst()
                .orElseThrow(() -> new IllegalStateException("Regime '" + nome + "' não cobre " + mes));
    }

    /**
     * Regime cível padrão do TJMG: tabela da CGJ (INPC) + 1% a.m. até 29/08/2024, véspera da
     * vigência da Lei 14.905/2024, e, depois, IPCA + taxa legal (Res. CMN 5.171/2024).
     * <p>
     * Equivale a {@link #transicaoTabelaCgjParaLei14905} com corte em 29/08/2024, ou seja,
     * INPC até 07/2024, IPCA desde 08/2024 e taxa legal desde 09/2024 (#24). O nome traz o
     * último mês de INPC para que o demonstrativo identifique o critério que o produziu.
     * <p>
     * O critério anterior à lei varia conforme o tribunal e o título (ex.: SELIC até
     * 30/08/2024, tabela prática do TJSP) e deve ser configurável por cálculo (#3).
     */
    public static RegimeAtualizacao civelPadrao() {
        return transicaoTabelaCgjParaLei14905(
                "Cível padrão TJMG (tabela CGJ: INPC até 07/2024; Lei 14.905/2024)",
                LocalDate.of(2024, 8, 29));
    }

    /**
     * Tabela da CGJ-TJMG + 1% a.m. até {@code ultimoDiaCriterioAnterior} e, depois, IPCA + taxa
     * legal (Lei 14.905/2024).
     * <p>
     * Correção e juros têm cortes diferentes, e não por acaso:
     * <ul>
     *   <li><b>Correção.</b> Vale a edição da tabela vigente no último dia do critério anterior.
     *       A edição do mês M só tem INPC até M−1, porque o IBGE divulga o índice no mês
     *       seguinte; o mês M já não está na tabela e é corrigido pelo IPCA.</li>
     *   <li><b>Juros.</b> Seguem a data: 1% a.m. até o mês anterior ao do corte, taxa legal a
     *       partir do mês seguinte e, no mês do corte, 1% até o dia do corte e taxa legal pro
     *       rata nos dias restantes ({@link Juros.TransicaoParaTaxaLegal}, #3).</li>
     * </ul>
     * Assim, no mês do corte a correção já é a da lei nova e os juros se dividem entre as duas.
     */
    public static RegimeAtualizacao transicaoTabelaCgjParaLei14905(String nome, LocalDate ultimoDiaCriterioAnterior) {
        YearMonth mesCorte = YearMonth.from(ultimoDiaCriterioAnterior);
        Juros umPorCento = new Juros.TaxaFixa(BigDecimal.ONE);
        return new RegimeAtualizacao(nome, List.of(
                new RegraPeriodo(INICIO_REAL, mesCorte.minusMonths(1), Indice.INPC, umPorCento),
                new RegraPeriodo(mesCorte, mesCorte, Indice.IPCA,
                        new Juros.TransicaoParaTaxaLegal(ultimoDiaCriterioAnterior, BigDecimal.ONE)),
                new RegraPeriodo(mesCorte.plusMonths(1), null, Indice.IPCA, new Juros.TaxaLegal())));
    }
}
