package br.com.debeatur.core.calculo;

import br.com.debeatur.core.Dinheiro;

import java.math.BigDecimal;

/** Memória de cálculo de uma parcela. */
public record LinhaDemonstrativo(
        Parcela parcela,
        BigDecimal fatorCorrecao,
        Dinheiro valorCorrigido,
        BigDecimal percentualJuros,
        Dinheiro valorJuros,
        Dinheiro subtotal) {
}
