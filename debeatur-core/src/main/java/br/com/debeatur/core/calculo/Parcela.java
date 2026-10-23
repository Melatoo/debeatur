package br.com.debeatur.core.calculo;

import br.com.debeatur.core.Dinheiro;

import java.time.LocalDate;
import java.util.Objects;

/**
 * Uma verba da condenação.
 *
 * @param inicioCorrecao termo inicial da correção monetária (ex.: vencimento, Súmula 43 STJ)
 * @param inicioJuros    termo inicial dos juros de mora (ex.: citação, evento danoso),
 *                       ou {@code null} se a verba não tem juros
 */
public record Parcela(String descricao, Dinheiro valor, LocalDate inicioCorrecao, LocalDate inicioJuros) {

    public Parcela {
        Objects.requireNonNull(descricao, "descricao");
        Objects.requireNonNull(valor, "valor");
        Objects.requireNonNull(inicioCorrecao, "inicioCorrecao");
    }
}
