package br.com.debeatur.core.calculo;

import br.com.debeatur.core.Dinheiro;

import java.math.BigDecimal;
import java.util.Objects;

/**
 * Multa e honorários de 10% do art. 523, §1º, do CPC sobre um saldo, cada um arredondado a
 * duas casas.
 */
public record AcrescimosArt523(Dinheiro multa, Dinheiro honorarios) {

    public static final AcrescimosArt523 NENHUM = new AcrescimosArt523(Dinheiro.ZERO, Dinheiro.ZERO);

    private static final BigDecimal DEZ_PORCENTO = BigDecimal.TEN;

    public AcrescimosArt523 {
        Objects.requireNonNull(multa, "multa");
        Objects.requireNonNull(honorarios, "honorarios");
    }

    /** Multa de 10% do saldo e honorários de 10% da base escolhida. */
    public static AcrescimosArt523 sobre(Dinheiro saldo, BaseHonorarios base) {
        Objects.requireNonNull(saldo, "saldo");
        Objects.requireNonNull(base, "base");
        Dinheiro multa = saldo.percentual(DEZ_PORCENTO);
        Dinheiro baseHonorarios = switch (base) {
            case DEBITO -> saldo;
            case DEBITO_COM_MULTA -> saldo.mais(multa);
        };
        return new AcrescimosArt523(multa, baseHonorarios.percentual(DEZ_PORCENTO));
    }

    /** {@link #sobre} se {@code aplicar}; senão, {@link #NENHUM}. */
    public static AcrescimosArt523 se(boolean aplicar, Dinheiro saldo, BaseHonorarios base) {
        return aplicar ? sobre(saldo, base) : NENHUM;
    }

    public Dinheiro total() {
        return multa.mais(honorarios);
    }
}
