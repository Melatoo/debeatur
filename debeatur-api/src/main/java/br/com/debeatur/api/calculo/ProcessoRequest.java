package br.com.debeatur.api.calculo;

import jakarta.validation.constraints.Size;

/**
 * Identificação do processo no cabeçalho do demonstrativo em PDF. Nenhum campo entra no
 * cálculo, e todos são opcionais.
 *
 * @param numero    número do processo, de preferência no padrão CNJ
 * @param juizo     vara ou juízo onde corre a execução
 * @param exequente quem promove a execução, como deve constar no cabeçalho
 * @param executado contra quem corre a execução
 */
public record ProcessoRequest(
        @Size(max = 50) String numero,
        @Size(max = 200) String juizo,
        @Size(max = 200) String exequente,
        @Size(max = 200) String executado) {
}
