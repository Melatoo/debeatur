package br.com.debeatur.core.calculo;

import br.com.debeatur.core.regime.RegimeAtualizacao;

import java.time.LocalDate;
import java.util.List;
import java.util.Objects;

/**
 * @param dataBase     data até a qual o débito é atualizado
 * @param aplicarArt523 inclui multa de 10% e honorários de 10% (art. 523, §1º, CPC)
 */
public record ParametrosCalculo(List<Parcela> parcelas, LocalDate dataBase,
                                RegimeAtualizacao regime, boolean aplicarArt523) {

    public ParametrosCalculo {
        parcelas = List.copyOf(parcelas);
        Objects.requireNonNull(dataBase, "dataBase");
        Objects.requireNonNull(regime, "regime");
        if (parcelas.isEmpty()) {
            throw new IllegalArgumentException("Informe ao menos uma parcela");
        }
        for (Parcela p : parcelas) {
            if (p.inicioCorrecao().isAfter(dataBase)
                    || (p.inicioJuros() != null && p.inicioJuros().isAfter(dataBase))) {
                throw new IllegalArgumentException("Parcela '" + p.descricao() + "' tem termo inicial após a data-base");
            }
        }
    }
}
