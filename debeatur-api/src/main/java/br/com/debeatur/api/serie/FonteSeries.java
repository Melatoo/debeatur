package br.com.debeatur.api.serie;

import br.com.debeatur.core.indice.Indice;
import br.com.debeatur.core.indice.SalarioMinimoEmMemoria;
import br.com.debeatur.core.indice.SerieIndicesEmMemoria;

import java.time.Instant;
import java.time.YearMonth;
import java.util.Optional;
import java.util.Set;

/** De onde o cálculo tira os índices e o salário mínimo. */
public interface FonteSeries {

    /** Variações mensais dos índices entre os meses informados (inclusive). */
    SerieIndicesEmMemoria carregar(Set<Indice> indices, YearMonth de, YearMonth ate);

    /** Salário mínimo vigente em cada mês entre os meses informados (inclusive). */
    SalarioMinimoEmMemoria carregarSalarioMinimo(YearMonth de, YearMonth ate);

    /**
     * Quando os valores da série foram tirados do SGS, para o demonstrativo dizer de que dia são.
     *
     * @param serie o nome do {@link Indice} ou {@link CacheSeries#SALARIO_MINIMO}
     * @return vazio se a fonte não sabe
     */
    default Optional<Captura> captura(String serie) {
        return Optional.empty();
    }

    /**
     * @param em       a última consulta ao SGS, no cache; o salvamento do cálculo, no snapshot
     * @param snapshot se os valores são os guardados com um cálculo salvo, e não os do cache
     */
    record Captura(Instant em, boolean snapshot) {
    }
}
