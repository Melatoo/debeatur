package br.com.debeatur.core.calculo;

/**
 * Sobre o que incidem os honorários de 10% do art. 523, §1º, do CPC. As fontes divergem
 * (docs/gabarito.md, divergência 5), então a escolha é do cálculo.
 */
public enum BaseHonorarios {

    /**
     * Sobre o débito, sem a multa: a multa e os honorários incidem, cada um, sobre o débito.
     * Planilha da DPMG e STJ (REsp 1.757.033/DF). É o padrão.
     */
    DEBITO,

    /** Sobre o débito acrescido da multa, como o CADEJ do TJMG. */
    DEBITO_COM_MULTA
}
