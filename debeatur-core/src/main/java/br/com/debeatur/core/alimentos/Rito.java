package br.com.debeatur.core.alimentos;

/** Rito pelo qual as parcelas de alimentos são executadas (art. 528 do CPC). */
public enum Rito {

    /**
     * Rito da prisão civil (art. 528, §§3º e 7º, do CPC; Súmula 309 do STJ): as três parcelas
     * anteriores ao ajuizamento e as que vencerem no curso do processo.
     */
    PRISAO,

    /** Rito da penhora (art. 528, §8º, do CPC): as demais parcelas. */
    PENHORA
}
