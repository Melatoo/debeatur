package br.com.debeatur.api.calculo;

import br.com.debeatur.core.Dinheiro;
import br.com.debeatur.core.calculo.Parcela;
import br.com.debeatur.core.calculo.ParametrosCalculo;
import br.com.debeatur.core.regime.RegimeAtualizacao;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.stream.Stream;

/**
 * @param parcelas parcelas avulsas; opcionais se houver {@code pensao}
 * @param pensao   pensão em percentual do salário mínimo, cujas parcelas vêm antes das avulsas
 * @param regime   critérios fixados no título; sem ele, vale o regime cível padrão do TJMG
 */
public record CalculoRequest(
        @NotNull LocalDate dataBase,
        boolean aplicarArt523,
        List<@Valid ParcelaRequest> parcelas,
        @Valid PensaoRequest pensao,
        @Valid RegimeDto regime) {

    public record ParcelaRequest(
            @NotBlank String descricao,
            @NotNull @Positive BigDecimal valor,
            @NotNull LocalDate inicioCorrecao,
            LocalDate inicioJuros) {

        Parcela paraDominio() {
            return new Parcela(descricao, new Dinheiro(valor), inicioCorrecao, inicioJuros);
        }
    }

    /** @param parcelasDaPensao parcelas já geradas a partir de {@link #pensao()} */
    ParametrosCalculo paraDominio(List<Parcela> parcelasDaPensao) {
        Stream<Parcela> avulsas = parcelas == null ? Stream.empty() : parcelas.stream().map(ParcelaRequest::paraDominio);
        return new ParametrosCalculo(
                Stream.concat(parcelasDaPensao.stream(), avulsas).toList(),
                dataBase, regime == null ? RegimeAtualizacao.civelPadrao() : regime.paraDominio(), aplicarArt523);
    }
}
