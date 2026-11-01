package br.com.debeatur.core.indice;

import br.com.debeatur.core.Dinheiro;

import java.math.BigDecimal;
import java.time.YearMonth;
import java.util.HashMap;
import java.util.Map;

/**
 * Implementação em memória, útil para testes e para snapshots de cálculos já realizados.
 * <p>
 * Guarda um valor por mês; um mês sem valor é indisponível, mesmo entre dois meses
 * informados, para que uma série incompleta nunca repita o valor anterior em silêncio.
 */
public final class SalarioMinimoEmMemoria implements SalarioMinimo {

    private final Map<YearMonth, Dinheiro> valores = new HashMap<>();

    public SalarioMinimoEmMemoria com(YearMonth mes, String valor) {
        valores.put(mes, Dinheiro.de(valor));
        return this;
    }

    public SalarioMinimoEmMemoria comTodos(Map<YearMonth, BigDecimal> serie) {
        serie.forEach((mes, valor) -> valores.put(mes, new Dinheiro(valor)));
        return this;
    }

    @Override
    public Dinheiro vigente(YearMonth mes) {
        Dinheiro valor = valores.get(mes);
        if (valor == null) {
            throw new SalarioMinimoIndisponivelException(mes);
        }
        return valor;
    }
}
