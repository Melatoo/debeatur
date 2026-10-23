package br.com.debeatur.api.calculo;

import br.com.debeatur.core.Dinheiro;
import br.com.debeatur.core.calculo.Parcela;
import br.com.debeatur.core.calculo.ParametrosCalculo;
import br.com.debeatur.core.regime.RegimeAtualizacao;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

public record CalculoRequest(
        @NotNull LocalDate dataBase,
        boolean aplicarArt523,
        @NotEmpty List<@Valid ParcelaRequest> parcelas) {

    public record ParcelaRequest(
            @NotBlank String descricao,
            @NotNull @Positive BigDecimal valor,
            @NotNull LocalDate inicioCorrecao,
            LocalDate inicioJuros) {

        Parcela paraDominio() {
            return new Parcela(descricao, new Dinheiro(valor), inicioCorrecao, inicioJuros);
        }
    }

    ParametrosCalculo paraDominio() {
        return new ParametrosCalculo(
                parcelas.stream().map(ParcelaRequest::paraDominio).toList(),
                dataBase, RegimeAtualizacao.civelPadrao(), aplicarArt523);
    }
}
