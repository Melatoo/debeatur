package br.com.debeatur.api.serie;

import br.com.debeatur.core.indice.Indice;
import br.com.debeatur.core.indice.SalarioMinimoEmMemoria;
import br.com.debeatur.core.indice.SerieIndicesEmMemoria;

import java.time.YearMonth;
import java.util.Set;

/** De onde o cálculo tira os índices e o salário mínimo. */
public interface FonteSeries {

    /** Variações mensais dos índices entre os meses informados (inclusive). */
    SerieIndicesEmMemoria carregar(Set<Indice> indices, YearMonth de, YearMonth ate);

    /** Salário mínimo vigente em cada mês entre os meses informados (inclusive). */
    SalarioMinimoEmMemoria carregarSalarioMinimo(YearMonth de, YearMonth ate);
}
