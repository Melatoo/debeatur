package br.com.debeatur.core.alimentos;

import br.com.debeatur.core.Dinheiro;
import br.com.debeatur.core.indice.SalarioMinimo;

import java.math.BigDecimal;
import java.time.YearMonth;
import java.util.Collections;
import java.util.Map;
import java.util.NavigableMap;
import java.util.Objects;
import java.util.TreeMap;

/**
 * Como o título fixou o valor mensal da pensão. A base dá o valor de um mês comum; o 13º
 * salário é tratado em {@link Pensao}.
 */
public sealed interface BasePensao {

    /**
     * Base de cálculo do mês, para a memória: o salário mínimo vigente, os rendimentos
     * informados ou o valor fixo do período.
     *
     * @param salarioMinimo usado só pela base em salário mínimo; pode ser nulo nas outras
     */
    Dinheiro base(YearMonth mes, SalarioMinimo salarioMinimo);

    /** Valor da parcela de um mês comum, arredondado como {@link Dinheiro}. */
    Dinheiro valor(YearMonth mes, SalarioMinimo salarioMinimo);

    /** Se as parcelas dependem do salário mínimo de cada mês. */
    default boolean usaSalarioMinimo() {
        return false;
    }

    /**
     * Percentual do salário mínimo vigente no mês do vencimento (Súmula 490 do STF).
     *
     * @param percentual em pontos (ex.: {@code 30} para 30%)
     */
    record PercentualDoSalarioMinimo(BigDecimal percentual) implements BasePensao {

        public PercentualDoSalarioMinimo {
            exigirPositivo(percentual, "O percentual do salário mínimo deve ser positivo");
        }

        @Override
        public Dinheiro base(YearMonth mes, SalarioMinimo salarioMinimo) {
            Objects.requireNonNull(salarioMinimo, "salarioMinimo");
            return salarioMinimo.vigente(mes);
        }

        @Override
        public Dinheiro valor(YearMonth mes, SalarioMinimo salarioMinimo) {
            return base(mes, salarioMinimo).percentual(percentual);
        }

        @Override
        public boolean usaSalarioMinimo() {
            return true;
        }
    }

    /**
     * Percentual de um valor de rendimentos informado, o mesmo em todos os meses, como na
     * planilha de alimentos da DPMG ("percentual dos rendimentos líquidos"). Não há reajuste:
     * se os rendimentos mudaram, quem calcula informa outro cálculo ou usa {@link ValorFixo}.
     *
     * @param percentual  em pontos (ex.: {@code 30} para 30%)
     * @param rendimentos rendimentos mensais sobre os quais incide o percentual
     */
    record PercentualDosRendimentos(BigDecimal percentual, Dinheiro rendimentos) implements BasePensao {

        public PercentualDosRendimentos {
            exigirPositivo(percentual, "O percentual dos rendimentos deve ser positivo");
            Objects.requireNonNull(rendimentos, "rendimentos");
            if (rendimentos.compareTo(Dinheiro.ZERO) <= 0) {
                throw new IllegalArgumentException("Os rendimentos devem ser positivos");
            }
        }

        @Override
        public Dinheiro base(YearMonth mes, SalarioMinimo salarioMinimo) {
            return rendimentos;
        }

        @Override
        public Dinheiro valor(YearMonth mes, SalarioMinimo salarioMinimo) {
            return rendimentos.percentual(percentual);
        }
    }

    /**
     * Valor fixo em reais, por períodos, sem reajuste automático. Cada valor vale do mês
     * informado até o mês anterior ao próximo valor; o último vale até o fim da pensão.
     *
     * @param valoresAPartirDe valor mensal a partir de cada mês; o primeiro mês deve cobrir o
     *                         início da pensão, o que {@link Pensao} confere
     */
    record ValorFixo(NavigableMap<YearMonth, Dinheiro> valoresAPartirDe) implements BasePensao {

        public ValorFixo {
            Objects.requireNonNull(valoresAPartirDe, "valoresAPartirDe");
            if (valoresAPartirDe.isEmpty()) {
                throw new IllegalArgumentException("Informe ao menos um valor fixo");
            }
            for (Map.Entry<YearMonth, Dinheiro> e : valoresAPartirDe.entrySet()) {
                Objects.requireNonNull(e.getKey(), "mes");
                Objects.requireNonNull(e.getValue(), "valor");
                if (e.getValue().compareTo(Dinheiro.ZERO) <= 0) {
                    throw new IllegalArgumentException("O valor fixo a partir de " + e.getKey() + " deve ser positivo");
                }
            }
            valoresAPartirDe = Collections.unmodifiableNavigableMap(new TreeMap<>(valoresAPartirDe));
        }

        /** Primeiro mês com valor. */
        public YearMonth inicio() {
            return valoresAPartirDe.firstKey();
        }

        @Override
        public Dinheiro base(YearMonth mes, SalarioMinimo salarioMinimo) {
            Map.Entry<YearMonth, Dinheiro> vigente = valoresAPartirDe.floorEntry(mes);
            if (vigente == null) {
                throw new IllegalArgumentException("Sem valor fixo para " + mes);
            }
            return vigente.getValue();
        }

        @Override
        public Dinheiro valor(YearMonth mes, SalarioMinimo salarioMinimo) {
            return base(mes, salarioMinimo);
        }
    }

    private static void exigirPositivo(BigDecimal percentual, String mensagem) {
        Objects.requireNonNull(percentual, "percentual");
        if (percentual.signum() <= 0) {
            throw new IllegalArgumentException(mensagem);
        }
    }
}
