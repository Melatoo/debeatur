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
import java.time.YearMonth;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;
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
        List<Parcela> todas = Stream.concat(parcelasDaPensao.stream(), avulsas).toList();
        if (separaPorRito()) {
            exigirUmaParcelaPorMes(todas);
        }
        return new ParametrosCalculo(todas,
                dataBase, regime == null ? RegimeAtualizacao.civelPadrao() : regime.paraDominio(),
                aplicarArt523 && !separaPorRito());
    }

    /**
     * A separação dos ritos conta as três parcelas anteriores ao ajuizamento por linha; com duas
     * linhas no mesmo mês, a prisão cobriria menos de três meses.
     */
    private static void exigirUmaParcelaPorMes(List<Parcela> parcelas) {
        List<YearMonth> repetidos = parcelas.stream()
                .collect(Collectors.groupingBy(p -> YearMonth.from(p.inicioCorrecao()), Collectors.counting()))
                .entrySet().stream()
                .filter(e -> e.getValue() > 1)
                .map(Map.Entry::getKey)
                .sorted()
                .toList();
        if (!repetidos.isEmpty()) {
            throw new IllegalArgumentException("Para separar os ritos, informe no máximo uma parcela por mês de vencimento;"
                    + " há mais de uma em " + repetidos);
        }
    }

    /** Nos alimentos com ajuizamento, o art. 523 sai do motor e incide só sobre a penhora. */
    boolean separaPorRito() {
        return pensao != null && pensao.ajuizamento() != null;
    }
}
