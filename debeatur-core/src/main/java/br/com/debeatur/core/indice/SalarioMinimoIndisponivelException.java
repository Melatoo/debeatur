package br.com.debeatur.core.indice;

import java.time.YearMonth;

public class SalarioMinimoIndisponivelException extends RuntimeException {

    private final YearMonth mes;

    public SalarioMinimoIndisponivelException(YearMonth mes) {
        super("Salário mínimo indisponível para " + mes);
        this.mes = mes;
    }

    public YearMonth mes() {
        return mes;
    }
}
