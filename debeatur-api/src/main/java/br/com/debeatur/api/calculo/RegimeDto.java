package br.com.debeatur.api.calculo;

import br.com.debeatur.core.indice.Indice;
import br.com.debeatur.core.regime.Juros;
import br.com.debeatur.core.regime.RegimeAtualizacao;
import br.com.debeatur.core.regime.RegraPeriodo;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.YearMonth;
import java.util.List;

/**
 * Regime de atualização como o usuário o informa: os períodos, o índice de correção e os
 * juros fixados no título. Também descreve, na resposta, o regime que produziu o cálculo.
 * <p>
 * Todo critério de juros do core tem representação aqui, para que o regime padrão, devolvido
 * por {@code GET /api/regimes/padrao}, volte ao motor igual quando reenviado sem alteração.
 *
 * @param nome opcional na entrada; sem ele, o demonstrativo usa {@link #NOME_PADRAO}
 */
public record RegimeDto(String nome, @NotEmpty List<@Valid RegraDto> regras) {

    static final String NOME_PADRAO = "Critérios fixados no título";

    /**
     * {@code TRANSICAO_TAXA_LEGAL} é o mês da virada da Lei 14.905/2024: taxa fixa até
     * {@code ultimoDiaTaxaFixa} e taxa legal pro rata nos dias seguintes
     * ({@link Juros.TransicaoParaTaxaLegal}).
     */
    public enum TipoJuros { TAXA_FIXA, TAXA_LEGAL, TRANSICAO_TAXA_LEGAL, SEM_JUROS }

    /**
     * @param fim                   último mês da regra, ou {@code null} se ela vale até a data-base
     * @param percentualJurosMensal taxa fixa de {@link TipoJuros#TAXA_FIXA} ou, em
     *                              {@link TipoJuros#TRANSICAO_TAXA_LEGAL}, a taxa fixa até o corte,
     *                              em pontos (ex.: {@code 1} para 1%)
     * @param ultimoDiaTaxaFixa     só para {@link TipoJuros#TRANSICAO_TAXA_LEGAL}: último dia da taxa
     *                              fixa, dentro do único mês da regra
     * @param descricaoJuros        só na resposta, ignorada na entrada
     */
    public record RegraDto(
            @NotNull YearMonth inicio,
            YearMonth fim,
            @NotNull Indice correcao,
            @NotNull TipoJuros juros,
            BigDecimal percentualJurosMensal,
            LocalDate ultimoDiaTaxaFixa,
            String descricaoJuros) {

        static RegraDto de(RegraPeriodo regra) {
            String descricao = regra.juros().descricao();
            return switch (regra.juros()) {
                case Juros.TaxaFixa(BigDecimal percentual) -> new RegraDto(regra.inicio(), regra.fim(),
                        regra.correcao(), TipoJuros.TAXA_FIXA, percentual, null, descricao);
                case Juros.TaxaLegal() -> new RegraDto(regra.inicio(), regra.fim(),
                        regra.correcao(), TipoJuros.TAXA_LEGAL, null, null, descricao);
                case Juros.TransicaoParaTaxaLegal(LocalDate ultimoDia, BigDecimal percentual) -> new RegraDto(
                        regra.inicio(), regra.fim(), regra.correcao(), TipoJuros.TRANSICAO_TAXA_LEGAL,
                        percentual, ultimoDia, descricao);
                case Juros.SemJuros() -> new RegraDto(regra.inicio(), regra.fim(),
                        regra.correcao(), TipoJuros.SEM_JUROS, null, null, descricao);
            };
        }

        RegraPeriodo paraDominio() {
            return new RegraPeriodo(inicio, fim, correcao, jurosDominio());
        }

        private Juros jurosDominio() {
            return switch (juros) {
                case TAXA_FIXA -> new Juros.TaxaFixa(percentualInformado());
                case TAXA_LEGAL -> new Juros.TaxaLegal();
                case TRANSICAO_TAXA_LEGAL -> transicao();
                case SEM_JUROS -> new Juros.SemJuros();
            };
        }

        private Juros transicao() {
            if (ultimoDiaTaxaFixa == null) {
                throw new IllegalArgumentException("Informe o último dia da taxa fixa na regra de transição que começa em "
                        + inicio);
            }
            YearMonth mesCorte = YearMonth.from(ultimoDiaTaxaFixa);
            if (!mesCorte.equals(inicio) || !mesCorte.equals(fim)) {
                throw new IllegalArgumentException("A regra de transição para a taxa legal vale só para o mês do corte: "
                        + "informe início e fim iguais a " + mesCorte);
            }
            return new Juros.TransicaoParaTaxaLegal(ultimoDiaTaxaFixa, percentualInformado());
        }

        private BigDecimal percentualInformado() {
            if (percentualJurosMensal == null || percentualJurosMensal.signum() <= 0) {
                throw new IllegalArgumentException("Informe o percentual mensal dos juros da regra que começa em "
                        + inicio + " (para uma regra sem juros, use SEM_JUROS)");
            }
            return percentualJurosMensal;
        }
    }

    static RegimeDto de(RegimeAtualizacao regime) {
        return new RegimeDto(regime.nome(), regime.regras().stream().map(RegraDto::de).toList());
    }

    RegimeAtualizacao paraDominio() {
        String nomeRegime = nome == null || nome.isBlank() ? NOME_PADRAO : nome.strip();
        return new RegimeAtualizacao(nomeRegime, regras.stream().map(RegraDto::paraDominio).toList());
    }
}
