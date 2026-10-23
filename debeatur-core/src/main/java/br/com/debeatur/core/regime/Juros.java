package br.com.debeatur.core.regime;

import br.com.debeatur.core.indice.Indice;
import br.com.debeatur.core.indice.SerieIndices;

import java.math.BigDecimal;
import java.time.YearMonth;
import java.util.Objects;

/** Critério de juros de mora aplicado em um mês. Os percentuais são somados (juros simples). */
public sealed interface Juros {

    /** Percentual de juros do mês, em pontos (ex.: {@code 1} para 1%). */
    BigDecimal percentualNoMes(YearMonth mes, SerieIndices serie);

    String descricao();

    /** Taxa fixa mensal, ex.: 1% a.m. (art. 406 CC na redação anterior à Lei 14.905/2024). */
    record TaxaFixa(BigDecimal percentualMensal) implements Juros {

        public TaxaFixa {
            Objects.requireNonNull(percentualMensal, "percentualMensal");
        }

        @Override
        public BigDecimal percentualNoMes(YearMonth mes, SerieIndices serie) {
            return percentualMensal;
        }

        @Override
        public String descricao() {
            return percentualMensal.stripTrailingZeros().toPlainString() + "% a.m.";
        }
    }

    /**
     * Taxa legal do art. 406, §1º, do CC (Lei 14.905/2024): SELIC deduzido o IPCA,
     * considerada zero quando negativa (§3º).
     * <p>
     * Simplificação: subtração direta das variações mensais. A metodologia oficial
     * (Res. CMN 5.171/2024) deve ser validada contra a taxa divulgada pelo BCB.
     */
    record SelicMenosIpca() implements Juros {

        @Override
        public BigDecimal percentualNoMes(YearMonth mes, SerieIndices serie) {
            BigDecimal taxa = serie.variacaoMensal(Indice.SELIC, mes)
                    .subtract(serie.variacaoMensal(Indice.IPCA, mes));
            return taxa.max(BigDecimal.ZERO);
        }

        @Override
        public String descricao() {
            return "Taxa legal (SELIC − IPCA)";
        }
    }

    record SemJuros() implements Juros {

        @Override
        public BigDecimal percentualNoMes(YearMonth mes, SerieIndices serie) {
            return BigDecimal.ZERO;
        }

        @Override
        public String descricao() {
            return "Sem juros";
        }
    }
}
