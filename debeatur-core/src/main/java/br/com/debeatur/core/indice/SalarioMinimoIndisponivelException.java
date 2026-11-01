package br.com.debeatur.core.indice;

import java.time.YearMonth;

public class SalarioMinimoIndisponivelException extends RuntimeException {

    public SalarioMinimoIndisponivelException(YearMonth mes) {
        super("Salário mínimo indisponível para " + mes);
    }
}
