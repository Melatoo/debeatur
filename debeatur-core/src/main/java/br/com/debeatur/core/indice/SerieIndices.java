package br.com.debeatur.core.indice;

import java.math.BigDecimal;
import java.time.YearMonth;

/** Fonte das variações mensais dos índices. */
@FunctionalInterface
public interface SerieIndices {

    /**
     * Variação do índice no mês, em pontos percentuais (ex.: {@code 0.56} para 0,56%).
     *
     * @throws IndiceIndisponivelException se o valor do mês não estiver disponível
     */
    BigDecimal variacaoMensal(Indice indice, YearMonth mes);
}
