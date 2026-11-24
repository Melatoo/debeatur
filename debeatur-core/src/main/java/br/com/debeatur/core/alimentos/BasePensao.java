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

    /** A base que dá o valor do mês: ela mesma, ou a do período em {@link PorPeriodos}. */
    default BasePensao vigente(YearMonth mes) {
        return this;
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
     * Percentual de um valor de rendimentos informado, como na planilha de alimentos da DPMG
     * ("percentual dos rendimentos líquidos"). Sem mudanças, os rendimentos são os mesmos em
     * todos os meses. Se mudaram (reajuste, novo emprego), cada mudança vale do mês informado
     * até a próxima; não há reajuste automático.
     *
     * @param percentual  em pontos (ex.: {@code 30} para 30%)
     * @param rendimentos rendimentos mensais do início da pensão até a primeira mudança
     * @param mudancas    rendimentos a partir de cada mês; pode ser vazio
     */
    record PercentualDosRendimentos(BigDecimal percentual, Dinheiro rendimentos,
                                    NavigableMap<YearMonth, Dinheiro> mudancas) implements BasePensao {

        public PercentualDosRendimentos {
            exigirPositivo(percentual, "O percentual dos rendimentos deve ser positivo");
            Objects.requireNonNull(rendimentos, "rendimentos");
            if (rendimentos.compareTo(Dinheiro.ZERO) <= 0) {
                throw new IllegalArgumentException("Os rendimentos devem ser positivos");
            }
            Objects.requireNonNull(mudancas, "mudancas");
            for (Map.Entry<YearMonth, Dinheiro> e : mudancas.entrySet()) {
                Objects.requireNonNull(e.getKey(), "mes");
                Objects.requireNonNull(e.getValue(), "valor");
                if (e.getValue().compareTo(Dinheiro.ZERO) <= 0) {
                    throw new IllegalArgumentException("Os rendimentos a partir de " + e.getKey()
                            + " devem ser positivos");
                }
            }
            mudancas = Collections.unmodifiableNavigableMap(new TreeMap<>(mudancas));
        }

        /** Rendimentos iguais em todos os meses. */
        public PercentualDosRendimentos(BigDecimal percentual, Dinheiro rendimentos) {
            this(percentual, rendimentos, new TreeMap<>());
        }

        @Override
        public Dinheiro base(YearMonth mes, SalarioMinimo salarioMinimo) {
            Map.Entry<YearMonth, Dinheiro> mudanca = mudancas.floorEntry(mes);
            return mudanca == null ? rendimentos : mudanca.getValue();
        }

        @Override
        public Dinheiro valor(YearMonth mes, SalarioMinimo salarioMinimo) {
            return base(mes, salarioMinimo).percentual(percentual);
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

    /**
     * Base que muda no meio da pensão: revisional que muda o percentual, acordo que troca a base,
     * provisórios seguidos dos definitivos. A base inicial vale do início da pensão até a primeira
     * mudança, e cada mudança vale do mês informado até a próxima; o último período vai até o fim
     * da pensão. Cada mês usa a conta da base do seu período, como dois cálculos separados.
     * <p>
     * As mudanças internas de cada base (rendimentos que mudam, valores fixos por período) ficam
     * dentro do período dela, e um valor fixo de uma mudança começa até o mês da mudança.
     *
     * @param inicial  base do início da pensão até a primeira mudança
     * @param mudancas base a partir de cada mês; ao menos uma, nenhuma delas por períodos
     */
    record PorPeriodos(BasePensao inicial, NavigableMap<YearMonth, BasePensao> mudancas) implements BasePensao {

        public PorPeriodos {
            Objects.requireNonNull(inicial, "inicial");
            Objects.requireNonNull(mudancas, "mudancas");
            if (mudancas.isEmpty()) {
                throw new IllegalArgumentException("Informe ao menos uma mudança da base");
            }
            mudancas = Collections.unmodifiableNavigableMap(new TreeMap<>(mudancas));
            if (inicial instanceof PorPeriodos) {
                throw new IllegalArgumentException("A base inicial não pode ser por períodos");
            }
            exigirMudancasInternasAntes(inicial, mudancas.firstKey());
            for (Map.Entry<YearMonth, BasePensao> e : mudancas.entrySet()) {
                YearMonth inicio = Objects.requireNonNull(e.getKey(), "mes");
                BasePensao base = Objects.requireNonNull(e.getValue(), "base");
                if (base instanceof PorPeriodos) {
                    throw new IllegalArgumentException("A base a partir de " + inicio + " não pode ser por períodos");
                }
                if (base instanceof ValorFixo fixo && fixo.inicio().isAfter(inicio)) {
                    throw new IllegalArgumentException("O valor fixo da base a partir de " + inicio
                            + " começa depois dela, em " + fixo.inicio());
                }
                if (base instanceof PercentualDosRendimentos r && !r.mudancas().isEmpty()
                        && !r.mudancas().firstKey().isAfter(inicio)) {
                    throw new IllegalArgumentException("As mudanças dos rendimentos da base a partir de " + inicio
                            + " devem ficar depois dela");
                }
                YearMonth proxima = mudancas.higherKey(inicio);
                if (proxima != null) {
                    exigirMudancasInternasAntes(base, proxima);
                }
            }
        }

        /** A base do período do mês. */
        @Override
        public BasePensao vigente(YearMonth mes) {
            Map.Entry<YearMonth, BasePensao> mudanca = mudancas.floorEntry(mes);
            return mudanca == null ? inicial : mudanca.getValue();
        }

        @Override
        public Dinheiro base(YearMonth mes, SalarioMinimo salarioMinimo) {
            return vigente(mes).base(mes, salarioMinimo);
        }

        @Override
        public Dinheiro valor(YearMonth mes, SalarioMinimo salarioMinimo) {
            return vigente(mes).valor(mes, salarioMinimo);
        }

        @Override
        public boolean usaSalarioMinimo() {
            return inicial.usaSalarioMinimo() || mudancas.values().stream().anyMatch(BasePensao::usaSalarioMinimo);
        }

        /** Mudanças internas da base (rendimentos, valores fixos) só antes do fim do período dela. */
        private static void exigirMudancasInternasAntes(BasePensao base, YearMonth fimExclusivo) {
            if (base instanceof PercentualDosRendimentos r && !r.mudancas().isEmpty()
                    && !r.mudancas().lastKey().isBefore(fimExclusivo)) {
                throw new IllegalArgumentException("A mudança dos rendimentos a partir de " + r.mudancas().lastKey()
                        + " cai no período da base a partir de " + fimExclusivo + "; informe-a como mudança da base");
            }
            if (base instanceof ValorFixo f && !f.valoresAPartirDe().lastKey().isBefore(fimExclusivo)) {
                throw new IllegalArgumentException("O valor fixo a partir de " + f.valoresAPartirDe().lastKey()
                        + " cai no período da base a partir de " + fimExclusivo + "; informe-o como mudança da base");
            }
        }
    }

    private static void exigirPositivo(BigDecimal percentual, String mensagem) {
        Objects.requireNonNull(percentual, "percentual");
        if (percentual.signum() <= 0) {
            throw new IllegalArgumentException(mensagem);
        }
    }
}
