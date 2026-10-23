package br.com.debeatur.core.indice;

import java.time.YearMonth;

public class IndiceIndisponivelException extends RuntimeException {

    public IndiceIndisponivelException(Indice indice, YearMonth mes) {
        super("Variação de " + indice + " indisponível para " + mes);
    }
}
