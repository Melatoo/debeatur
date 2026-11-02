package br.com.debeatur.api.calculo;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;

/** Entrada e saída de {@code /api/calculos/salvos}. */
final class CalculoSalvoDtos {

    private CalculoSalvoDtos() {
    }

    /**
     * @param titulo  como o cálculo aparece na lista; sem ele, o número do processo ou a data-base
     * @param calculo o mesmo pedido de {@code POST /api/calculos}
     */
    record SalvarRequest(@Size(max = 200) String titulo, @NotNull @Valid CalculoRequest calculo) {
    }

    record Resumo(UUID id, String titulo, LocalDate dataBase, String regime, BigDecimal total, Instant criadoEm) {

        static Resumo de(RepositorioCalculos.Resumo r) {
            return new Resumo(r.id(), r.titulo(), r.dataBase(), r.regime(), r.total(), r.criadoEm());
        }
    }

    /**
     * @param calculo       o pedido como foi salvo, com o regime usado; pode ser reenviado ao
     *                      editor ou a {@code POST /api/calculos} para atualizar o cálculo
     * @param demonstrativo refeito com o snapshot das séries, igual ao do dia em que foi salvo
     */
    record CalculoSalvo(UUID id, String titulo, Instant criadoEm, CalculoRequest calculo,
                        DemonstrativoResponse demonstrativo) {
    }
}
