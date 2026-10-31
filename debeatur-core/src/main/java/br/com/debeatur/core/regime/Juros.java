package br.com.debeatur.core.regime;

import br.com.debeatur.core.indice.Indice;
import br.com.debeatur.core.indice.SerieIndices;

import java.math.BigDecimal;
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
     *       em 30 e 31/08/2024), arredondada a seis casas (HALF_UP), a precisão em que o BCB
     *       publica a taxa.</li>
     * </ul>
     * Com corte em 29/08/2024 e 1% a.m.: 29/30 × 1% = 0,9667% e 2/31 × 0,605306% = 0,039052%,
     * total de 1,005752%. Se o corte é o último dia do mês, vale a taxa fixa inteira.
     * <p>
     * As duas partes têm bases diferentes de propósito, como na planilha: o mês comercial é a
     * convenção da taxa fixa ao mês, e os dias corridos são a regra da Res. CMN 5.171/2024. Por
     * isso a soma das frações passa de um mês nos meses de 31 dias (já em 29/08/2024) e, com
     * corte no dia 30, a taxa fixa sai inteira e o dia 31 ainda leva a taxa legal.
     * <p>
     * O corte não pode ser anterior a 29/08/2024: a taxa legal só vale a partir de 30/08/2024,
     * início da vigência da Lei 14.905/2024, e o SGS 29543 começa em agosto/2024.
     */
    record TransicaoParaTaxaLegal(LocalDate ultimoDiaTaxaFixa, BigDecimal percentualMensalAnterior)
            implements Juros {

        /** Véspera da vigência da Lei 14.905/2024: último dia possível do critério anterior. */
        public static final LocalDate PRIMEIRO_CORTE_POSSIVEL = LocalDate.of(2024, 8, 29);

        private static final BigDecimal DIAS_MES_COMERCIAL = BigDecimal.valueOf(30);
        private static final int CASAS_TAXA_FIXA = 4;
        private static final int CASAS_TAXA_LEGAL = 6;
        private static final DateTimeFormatter DATA = DateTimeFormatter.ofPattern("dd/MM/yyyy");

        public TransicaoParaTaxaLegal {
            Objects.requireNonNull(ultimoDiaTaxaFixa, "ultimoDiaTaxaFixa");
            Objects.requireNonNull(percentualMensalAnterior, "percentualMensalAnterior");
            if (ultimoDiaTaxaFixa.isBefore(PRIMEIRO_CORTE_POSSIVEL)) {
                throw new IllegalArgumentException("A taxa legal só vale a partir de 30/08/2024 (Lei 14.905/2024); "
                        + "o corte em " + ultimoDiaTaxaFixa.format(DATA) + " é anterior à véspera da vigência");
            }
        }

        @Override
        public BigDecimal percentualNoMes(YearMonth mes, SerieIndices serie) {
            if (!YearMonth.from(ultimoDiaTaxaFixa).equals(mes)) {
                throw new IllegalArgumentException(
                        "Transição em " + ultimoDiaTaxaFixa + " não se aplica a " + mes);
            }
            if (corteNoUltimoDiaDoMes()) {
                return percentualMensalAnterior;
            }
            int diasTaxaFixa = ultimoDiaTaxaFixa.getDayOfMonth();
            int diasNoMes = mes.lengthOfMonth();
            BigDecimal taxaFixa = percentualMensalAnterior.multiply(BigDecimal.valueOf(diasTaxaFixa))
                    .divide(DIAS_MES_COMERCIAL, CASAS_TAXA_FIXA, RoundingMode.HALF_UP);
            BigDecimal taxaLegal = serie.variacaoMensal(Indice.TAXA_LEGAL, mes)
                    .multiply(BigDecimal.valueOf(diasNoMes - diasTaxaFixa))
                    .divide(BigDecimal.valueOf(diasNoMes), CASAS_TAXA_LEGAL, RoundingMode.HALF_UP);
            return taxaFixa.add(taxaLegal);
        }

        @Override
        public String descricao() {
            String taxaFixa = percentualMensalAnterior.stripTrailingZeros().toPlainString() + "% a.m. até "
                    + ultimoDiaTaxaFixa.format(DATA);
            return corteNoUltimoDiaDoMes() ? taxaFixa : taxaFixa + " e taxa legal pro rata nos dias seguintes";
        }

        private boolean corteNoUltimoDiaDoMes() {
            return ultimoDiaTaxaFixa.getDayOfMonth() == ultimoDiaTaxaFixa.lengthOfMonth();
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
