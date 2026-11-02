package br.com.debeatur.api.serie;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;

import java.time.Duration;

/**
 * @param reconsulta   intervalo mínimo entre duas consultas ao SGS em busca de um mês ainda não
 *                     publicado; sem ele, todo cálculo com a data-base no mês corrente iria ao BCB
 * @param mesesRevisao quantos meses para trás a atualização periódica relê, para pegar as
 *                     revisões do BCB
 * @param atualizacao  expressão cron da atualização periódica, no fuso de São Paulo; {@code -}
 *                     desliga a atualização
 */
@ConfigurationProperties("debeatur.series")
public record PropriedadesSeries(
        @DefaultValue("PT1H") Duration reconsulta,
        @DefaultValue("24") int mesesRevisao,
        @DefaultValue("0 0 6 * * *") String atualizacao) {
}
