package br.com.debeatur.core;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.Objects;

/**
 * Valor monetário em reais, sempre com duas casas decimais e arredondamento HALF_UP.
 * <p>
 * Fatores e percentuais circulam com precisão total; o arredondamento acontece
 * apenas quando um valor vira {@code Dinheiro}.
 */
public record Dinheiro(BigDecimal valor) implements Comparable<Dinheiro> {

    public static final int ESCALA = 2;
    public static final RoundingMode ARREDONDAMENTO = RoundingMode.HALF_UP;
    public static final Dinheiro ZERO = new Dinheiro(BigDecimal.ZERO);

    public Dinheiro {
        Objects.requireNonNull(valor, "valor");
        valor = valor.setScale(ESCALA, ARREDONDAMENTO);
    }

    public static Dinheiro de(String valor) {
        return new Dinheiro(new BigDecimal(valor));
    }

    public Dinheiro mais(Dinheiro outro) {
        return new Dinheiro(valor.add(outro.valor));
    }

    public Dinheiro vezes(BigDecimal fator) {
        return new Dinheiro(valor.multiply(fator));
    }

    /** Aplica um percentual expresso em pontos (ex.: 10 para 10%). */
    public Dinheiro percentual(BigDecimal pontos) {
        return new Dinheiro(valor.multiply(pontos).movePointLeft(2));
    }

    @Override
    public int compareTo(Dinheiro outro) {
        return valor.compareTo(outro.valor);
    }

    @Override
    public String toString() {
        return "R$ " + valor.toPlainString();
    }
}
