package br.com.debeatur.core.indice;

import br.com.debeatur.core.Dinheiro;

import java.time.YearMonth;

/** Fonte do salário mínimo nacional vigente em cada mês, em valor nominal. */
@FunctionalInterface
public interface SalarioMinimo {

    /**
     * Salário mínimo vigente no mês (ex.: R$ 1.412,00 em 2024).
     *
     * @throws SalarioMinimoIndisponivelException se o valor do mês não estiver disponível
     */
    Dinheiro vigente(YearMonth mes);
}
