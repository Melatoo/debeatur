package br.com.debeatur.core.alimentos;

/** Termo inicial dos juros de mora sobre as parcelas de alimentos, conforme o título. */
public enum InicioJuros {

    /** Juros desde o vencimento de cada parcela. */
    VENCIMENTO,

    /**
     * Juros desde a citação para as parcelas vencidas antes dela; as que vencem depois
     * contam juros desde o próprio vencimento.
     */
    CITACAO
}
