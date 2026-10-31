package br.com.debeatur.core.gabarito;

import java.util.List;

/**
 * Catálogo dos cálculos homologados ou da contadoria do TJMG usados como gabarito (#2).
 * <p>
 * Cada caso é transcrito do documento de origem, com a fonte citada. Os valores esperados
 * nunca saem do motor. O passo a passo para incluir um caso está em {@code docs/gabarito.md}.
 */
final class CasosHomologados {

    private CasosHomologados() {
    }

    static List<CasoGabarito> todos() {
        return List.of();
    }
}
