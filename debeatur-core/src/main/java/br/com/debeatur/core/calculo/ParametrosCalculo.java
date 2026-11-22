package br.com.debeatur.core.calculo;

import br.com.debeatur.core.regime.RegimeAtualizacao;

import java.time.LocalDate;
import java.time.YearMonth;
import java.util.List;
import java.util.Objects;
import java.util.stream.Stream;

/**
 * @param dataBase     data até a qual o débito é atualizado
 * @param aplicarArt523  inclui multa de 10% e honorários de 10% (art. 523, §1º, CPC)
 * @param baseHonorarios sobre o que incidem os honorários do art. 523
 */
public record ParametrosCalculo(List<Parcela> parcelas, LocalDate dataBase,
                                RegimeAtualizacao regime, boolean aplicarArt523,
                                BaseHonorarios baseHonorarios) {

    /** Com os honorários sobre o débito, o padrão. */
    public ParametrosCalculo(List<Parcela> parcelas, LocalDate dataBase, RegimeAtualizacao regime,
                             boolean aplicarArt523) {
        this(parcelas, dataBase, regime, aplicarArt523, BaseHonorarios.DEBITO);
    }

    public ParametrosCalculo {
        parcelas = List.copyOf(parcelas);
        Objects.requireNonNull(dataBase, "dataBase");
        Objects.requireNonNull(regime, "regime");
        Objects.requireNonNull(baseHonorarios, "baseHonorarios");
        if (parcelas.isEmpty()) {
            throw new IllegalArgumentException("Informe ao menos uma parcela");
        }
        for (Parcela p : parcelas) {
            if (p.inicioCorrecao().isAfter(dataBase)
                    || (p.inicioJuros() != null && p.inicioJuros().isAfter(dataBase))) {
                throw new IllegalArgumentException("Parcela '" + p.descricao() + "' tem termo inicial após a data-base");
            }
        }
        YearMonth primeiroMes = parcelas.stream()
                .flatMap(p -> Stream.of(p.inicioCorrecao(), p.inicioJuros()))
                .filter(Objects::nonNull)
                .map(YearMonth::from)
                .min(YearMonth::compareTo)
                .orElseThrow();
        YearMonth ultimoMes = YearMonth.from(dataBase).minusMonths(1);
        if (!primeiroMes.isAfter(ultimoMes) && !regime.cobre(primeiroMes, ultimoMes)) {
            throw new IllegalArgumentException("O regime '" + regime.nome() + "' não cobre todo o período do cálculo ("
                    + primeiroMes + " a " + ultimoMes + ")");
        }
    }
}
