package br.com.debeatur.core.alimentos;

import br.com.debeatur.core.Dinheiro;
import br.com.debeatur.core.calculo.Demonstrativo;

import java.math.BigDecimal;
import java.util.List;
import java.util.Objects;

/**
 * Fechamento de um débito de alimentos com abatimentos, sem separação de ritos.
 * <p>
 * Convenção da planilha de alimentos da DPMG: os abatimentos saem do débito atualizado pelo
 * valor nominal, e a multa e os honorários de 10% do art. 523, §1º, do CPC incidem, cada um,
 * sobre o saldo já abatido (os honorários não incidem sobre a multa).
 *
 * @param debito           débito atualizado (corrigido mais juros)
 * @param abatimentos      valores recebidos, na ordem informada
 * @param totalAbatido     soma nominal dos abatimentos
 * @param saldo            débito menos o abatido
 * @param multaArt523      10% do saldo, ou zero
 * @param honorariosArt523 10% do saldo, ou zero
 * @param total            saldo mais multa e honorários
 */
public record TotalComAbatimentos(Dinheiro debito, List<Abatimento> abatimentos, Dinheiro totalAbatido,
                                  Dinheiro saldo, Dinheiro multaArt523, Dinheiro honorariosArt523,
                                  Dinheiro total) {

    private static final BigDecimal DEZ_PORCENTO = BigDecimal.TEN;

    public TotalComAbatimentos {
        Objects.requireNonNull(debito, "debito");
        abatimentos = List.copyOf(abatimentos);
        Objects.requireNonNull(totalAbatido, "totalAbatido");
        Objects.requireNonNull(saldo, "saldo");
        Objects.requireNonNull(multaArt523, "multaArt523");
        Objects.requireNonNull(honorariosArt523, "honorariosArt523");
        Objects.requireNonNull(total, "total");
    }

    /**
     * @param aplicarArt523 inclui multa e honorários de 10% sobre o saldo
     * @throws IllegalArgumentException se os abatimentos superarem o débito, ou se o
     *                                  demonstrativo já tiver multa ou honorários do art. 523,
     *                                  que lá incidiriam sobre o débito sem o abatimento
     */
    public static TotalComAbatimentos de(Demonstrativo demonstrativo, List<Abatimento> abatimentos,
                                         boolean aplicarArt523) {
        Objects.requireNonNull(demonstrativo, "demonstrativo");
        if (!demonstrativo.multaArt523().equals(Dinheiro.ZERO)
                || !demonstrativo.honorariosArt523().equals(Dinheiro.ZERO)) {
            throw new IllegalArgumentException(
                    "Com abatimentos, o art. 523 incide sobre o saldo; calcule o demonstrativo sem ele");
        }
        return de(demonstrativo.debitoAtualizado(), abatimentos, aplicarArt523);
    }

    static TotalComAbatimentos de(Dinheiro debito, List<Abatimento> abatimentos, boolean aplicarArt523) {
        Dinheiro abatido = Abatimento.total(abatimentos);
        if (abatido.compareTo(debito) > 0) {
            throw new IllegalArgumentException("Os abatimentos (" + abatido + ") superam o débito atualizado ("
                    + debito + ")");
        }
        Dinheiro saldo = debito.menos(abatido);
        Dinheiro multa = aplicarArt523 ? saldo.percentual(DEZ_PORCENTO) : Dinheiro.ZERO;
        Dinheiro honorarios = aplicarArt523 ? saldo.percentual(DEZ_PORCENTO) : Dinheiro.ZERO;
        return new TotalComAbatimentos(debito, abatimentos, abatido, saldo, multa, honorarios,
                saldo.mais(multa).mais(honorarios));
    }
}
