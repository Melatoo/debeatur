package br.com.debeatur.core.alimentos;

/**
 * Quais parcelas vão para o rito da prisão. As fontes divergem (docs/gabarito.md, divergência 7),
 * então a escolha é do cálculo.
 */
public enum RegraRitos {

    /**
     * As três parcelas de vencimento mais recente do cálculo, como a planilha da DPMG; as demais
     * vão para a penhora. Coincide com a lei quando o cálculo termina no ajuizamento.
     */
    ULTIMOS_MESES_DO_CALCULO,

    /**
     * As três parcelas de vencimento mais recente até o ajuizamento e todas as que vencem depois
     * dele (art. 528, §7º, do CPC; Súmula 309 do STJ).
     */
    LEI
}
