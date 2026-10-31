package br.com.debeatur.core.regime;

import br.com.debeatur.core.indice.Indice;
import br.com.debeatur.core.indice.SerieIndices;

import java.math.BigDecimal;
import java.math.MathContext;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.time.YearMonth;
import java.time.format.DateTimeFormatter;
import java.util.Objects;

/** Critério de juros de mora aplicado em um mês. Os percentuais são somados (juros simples). */
public sealed interface Juros {

    /** Percentual de juros do mês, em pontos (ex.: {@code 1} para 1%). */
    BigDecimal percentualNoMes(YearMonth mes, SerieIndices serie);

    String descricao();

    /** Se o critério lê a taxa legal da série de índices (SGS 29543). */
    default boolean usaTaxaLegal() {
        return false;
    }

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
     * Taxa legal do art. 406, §1º, do CC (Lei 14.905/2024), na forma divulgada pelo BCB.
     * <p>
     * Pela Res. CMN 5.171/2024, a taxa do mês m é {@code max(Fator Selic / Fator IPCA-15 − 1; 0)},
     * com a SELIC e o IPCA-15 do mês anterior, em seis casas. O motor usa a taxa publicada
     * em vez de recalculá-la.
     */
    record TaxaLegal() implements Juros {

        @Override
        public BigDecimal percentualNoMes(YearMonth mes, SerieIndices serie) {
            return serie.variacaoMensal(Indice.TAXA_LEGAL, mes);
        }

        @Override
        public String descricao() {
            return "Taxa legal (Res. CMN 5.171/2024)";
        }

        @Override
        public boolean usaTaxaLegal() {
            return true;
        }
    }

    /**
     * Mês da virada para a taxa legal: taxa fixa até {@code ultimoDiaTaxaFixa}, inclusive, e
     * taxa legal do mês nos dias seguintes, como na planilha de alimentos da DPMG.
     * <ul>
     *   <li><b>Taxa fixa:</b> proporcional aos dias sobre o mês comercial de 30 dias,
     *       arredondada a quatro casas (HALF_UP). É convenção da planilha, não norma.</li>
     *   <li><b>Taxa legal:</b> taxa do mês ÷ dias corridos do mês × dias restantes (Res. CMN
     *       5.171/2024, art. 6º; o art. 8º, parágrafo único, manda usar a taxa de agosto/2024
     *       em 30 e 31/08/2024).</li>
     * </ul>
     * Com corte em 29/08/2024 e 1% a.m.: 29/30 × 1% = 0,9667% e 2/31 × 0,605306% = 0,039052%,
     * total de 1,005752%. Se o corte é o último dia do mês, vale a taxa fixa inteira.
     */
    record TransicaoParaTaxaLegal(LocalDate ultimoDiaTaxaFixa, BigDecimal percentualMensalAnterior)
            implements Juros {

        private static final BigDecimal DIAS_MES_COMERCIAL = BigDecimal.valueOf(30);
        private static final int CASAS_TAXA_FIXA = 4;
        private static final DateTimeFormatter DATA = DateTimeFormatter.ofPattern("dd/MM/yyyy");

        public TransicaoParaTaxaLegal {
            Objects.requireNonNull(ultimoDiaTaxaFixa, "ultimoDiaTaxaFixa");
            Objects.requireNonNull(percentualMensalAnterior, "percentualMensalAnterior");
        }

        @Override
        public BigDecimal percentualNoMes(YearMonth mes, SerieIndices serie) {
            if (!YearMonth.from(ultimoDiaTaxaFixa).equals(mes)) {
                throw new IllegalArgumentException(
                        "Transição em " + ultimoDiaTaxaFixa + " não se aplica a " + mes);
            }
            int diasTaxaFixa = ultimoDiaTaxaFixa.getDayOfMonth();
            int diasNoMes = mes.lengthOfMonth();
            if (diasTaxaFixa == diasNoMes) {
                return percentualMensalAnterior;
            }
            BigDecimal taxaFixa = percentualMensalAnterior.multiply(BigDecimal.valueOf(diasTaxaFixa))
                    .divide(DIAS_MES_COMERCIAL, CASAS_TAXA_FIXA, RoundingMode.HALF_UP);
            BigDecimal taxaLegal = serie.variacaoMensal(Indice.TAXA_LEGAL, mes)
                    .multiply(BigDecimal.valueOf(diasNoMes - diasTaxaFixa))
                    .divide(BigDecimal.valueOf(diasNoMes), MathContext.DECIMAL128);
            return taxaFixa.add(taxaLegal);
        }

        @Override
        public String descricao() {
            return percentualMensalAnterior.stripTrailingZeros().toPlainString() + "% a.m. até "
                    + ultimoDiaTaxaFixa.format(DATA)
                    + " e taxa legal pro rata nos dias seguintes";
        }

        @Override
        public boolean usaTaxaLegal() {
            return true;
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
