package br.com.debeatur.core.regime;

import br.com.debeatur.core.indice.Indice;

import java.time.YearMonth;
import java.util.Objects;

/**
 * Critérios de correção e juros válidos entre dois meses (inclusive).
 *
 * @param fim último mês de vigência, ou {@code null} se a regra continua vigente
 */
public record RegraPeriodo(YearMonth inicio, YearMonth fim, Indice correcao, Juros juros) {

    public RegraPeriodo {
        Objects.requireNonNull(inicio, "inicio");
        Objects.requireNonNull(correcao, "correcao");
        Objects.requireNonNull(juros, "juros");
        if (fim != null && fim.isBefore(inicio)) {
            throw new IllegalArgumentException("fim (" + fim + ") anterior ao início (" + inicio + ")");
        }
    }

    public boolean abrange(YearMonth mes) {
        return !mes.isBefore(inicio) && (fim == null || !mes.isAfter(fim));
    }
}
