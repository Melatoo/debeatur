package br.com.debeatur.core.indice;

import java.math.BigDecimal;
import java.time.YearMonth;
import java.util.EnumMap;
import java.util.HashMap;
import java.util.Map;

/** Implementação em memória, útil para testes e para snapshots de cálculos já realizados. */
public final class SerieIndicesEmMemoria implements SerieIndices {

    private final Map<Indice, Map<YearMonth, BigDecimal>> valores = new EnumMap<>(Indice.class);

    public SerieIndicesEmMemoria com(Indice indice, YearMonth mes, String variacao) {
        return com(indice, mes, new BigDecimal(variacao));
    }

    public SerieIndicesEmMemoria com(Indice indice, YearMonth mes, BigDecimal variacao) {
        valores.computeIfAbsent(indice, i -> new HashMap<>()).put(mes, variacao);
        return this;
    }

    public SerieIndicesEmMemoria comTodos(Indice indice, Map<YearMonth, BigDecimal> serie) {
        valores.computeIfAbsent(indice, i -> new HashMap<>()).putAll(serie);
        return this;
    }

    @Override
    public BigDecimal variacaoMensal(Indice indice, YearMonth mes) {
        BigDecimal valor = valores.getOrDefault(indice, Map.of()).get(mes);
        if (valor == null) {
            throw new IndiceIndisponivelException(indice, mes);
        }
        return valor;
    }
}
