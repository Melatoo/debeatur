package br.com.debeatur.core.indice;

import java.time.YearMonth;

public class IndiceIndisponivelException extends RuntimeException {

    private final Indice indice;
    private final YearMonth mes;

    public IndiceIndisponivelException(Indice indice, YearMonth mes) {
        super("Variação de " + indice + " indisponível para " + mes);
        this.indice = indice;
        this.mes = mes;
    }

    public Indice indice() {
        return indice;
    }

    /** O primeiro mês sem valor que o cálculo pediu. */
    public YearMonth mes() {
        return mes;
    }
}
