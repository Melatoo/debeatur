package br.com.debeatur.core.calculo;

import br.com.debeatur.core.Dinheiro;

import java.time.LocalDate;
import java.util.List;

/** Demonstrativo discriminado e atualizado do crédito (art. 524 CPC). */
public record Demonstrativo(
        LocalDate dataBase,
        String regime,
        List<LinhaDemonstrativo> linhas,
        Dinheiro totalCorrigido,
        Dinheiro totalJuros,
        Dinheiro debitoAtualizado,
        Dinheiro multaArt523,
        Dinheiro honorariosArt523,
        Dinheiro total) {

    public Demonstrativo {
        linhas = List.copyOf(linhas);
    }
}
