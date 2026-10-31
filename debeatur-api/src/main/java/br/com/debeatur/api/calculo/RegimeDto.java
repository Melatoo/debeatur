package br.com.debeatur.api.calculo;

import br.com.debeatur.core.indice.Indice;
import br.com.debeatur.core.regime.Juros;
import br.com.debeatur.core.regime.RegimeAtualizacao;
import br.com.debeatur.core.regime.RegraPeriodo;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;

import java.math.BigDecimal;
import java.time.YearMonth;
import java.util.List;

/**
 * Regime de atualização como o usuário o informa: os períodos, o índice de correção e os
 * juros fixados no título. Também descreve, na resposta, o regime que produziu o cálculo.
 *
 * @param nome opcional na entrada; sem ele, o demonstrativo usa {@link #NOME_PADRAO}
 */
public record RegimeDto(String nome, @NotEmpty List<@Valid RegraDto> regras) {

    static final String NOME_PADRAO = "Critérios fixados no título";

    public enum TipoJuros { TAXA_FIXA, TAXA_LEGAL, SEM_JUROS }

    /**
     * @param fim                   último mês da regra, ou {@code null} se ela vale até a data-base
     * @param percentualJurosMensal só para {@link TipoJuros#TAXA_FIXA}, em pontos (ex.: {@code 1} para 1%)
     * @param descricaoJuros        só na resposta, ignorada na entrada
     */
    public record RegraDto(
            @NotNull YearMonth inicio,
            YearMonth fim,
            @NotNull Indice correcao,
            @NotNull TipoJuros juros,
            BigDecimal percentualJurosMensal,
            String descricaoJuros) {

        static RegraDto de(RegraPeriodo regra) {
            return switch (regra.juros()) {
                case Juros.TaxaFixa(BigDecimal percentual) ->
                        new RegraDto(regra.inicio(), regra.fim(), regra.correcao(), TipoJuros.TAXA_FIXA, percentual, regra.juros().descricao());
                case Juros.TaxaLegal() ->
                        new RegraDto(regra.inicio(), regra.fim(), regra.correcao(), TipoJuros.TAXA_LEGAL, null, regra.juros().descricao());
                case Juros.SemJuros() ->
                        new RegraDto(regra.inicio(), regra.fim(), regra.correcao(), TipoJuros.SEM_JUROS, null, regra.juros().descricao());
            };
        }

        RegraPeriodo paraDominio() {
            return new RegraPeriodo(inicio, fim, correcao, jurosDominio());
        }

        private Juros jurosDominio() {
            return switch (juros) {
                case TAXA_FIXA -> {
                    if (percentualJurosMensal == null || percentualJurosMensal.signum() <= 0) {
                        throw new IllegalArgumentException("Informe o percentual mensal dos juros da regra que começa em "
                                + inicio + " (para uma regra sem juros, use SEM_JUROS)");
                    }
                    yield new Juros.TaxaFixa(percentualJurosMensal);
                }
                case TAXA_LEGAL -> new Juros.TaxaLegal();
                case SEM_JUROS -> new Juros.SemJuros();
            };
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
