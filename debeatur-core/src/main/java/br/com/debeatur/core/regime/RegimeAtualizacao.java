package br.com.debeatur.core.regime;

import br.com.debeatur.core.indice.Indice;

import java.math.BigDecimal;
import java.time.YearMonth;
import java.util.List;

/**
 * Conjunto de regras que define como um débito é atualizado ao longo do tempo.
 * <p>
 * A sentença pode fixar critérios próprios (a coisa julgada prevalece), por isso o
 * regime é um dado do cálculo e não uma regra fixa do sistema.
 */
public record RegimeAtualizacao(String nome, List<RegraPeriodo> regras) {

    public RegimeAtualizacao {
        regras = List.copyOf(regras);
        if (regras.isEmpty()) {
            throw new IllegalArgumentException("O regime precisa de ao menos uma regra");
        }
    }

    public RegraPeriodo regraPara(YearMonth mes) {
        return regras.stream()
                .filter(r -> r.abrange(mes))
                .findFirst()
                .orElseThrow(() -> new IllegalStateException("Regime '" + nome + "' não cobre " + mes));
    }

    /**
     * Regime cível padrão: INPC + 1% a.m. até a vigência da Lei 14.905/2024 e, depois,
     * IPCA + taxa legal (Res. CMN 5.171/2024).
     * <p>
     * A lei vigora desde 30/08/2024; com granularidade mensal, a nova regra é aplicada a
     * partir de setembro/2024, e a taxa legal de agosto/2024, que vale para 30 e 31/08
     * (Res. CMN 5.171/2024, art. 8º, parágrafo único), fica de fora até o pro rata die (#3).
     * O índice anterior varia conforme o tribunal (ex.: tabela
     * prática do TJSP) e deve ser configurável por cálculo.
     */
    public static RegimeAtualizacao civelPadrao() {
        return new RegimeAtualizacao("Cível padrão (Lei 14.905/2024)", List.of(
                new RegraPeriodo(YearMonth.of(1994, 7), YearMonth.of(2024, 8),
                        Indice.INPC, new Juros.TaxaFixa(BigDecimal.ONE)),
                new RegraPeriodo(YearMonth.of(2024, 9), null,
                        Indice.IPCA, new Juros.TaxaLegal())));
    }
}
