package br.com.debeatur.core.indice;

/** Índices econômicos usados em correção monetária e juros. */
public enum Indice {
    IPCA("IPCA (IBGE)"),
    IPCA_E("IPCA-E (IBGE)"),
    INPC("INPC (IBGE)"),
    SELIC("Taxa SELIC acumulada no mês (BCB)");

    private final String descricao;

    Indice(String descricao) {
        this.descricao = descricao;
    }

    public String descricao() {
        return descricao;
    }
}
