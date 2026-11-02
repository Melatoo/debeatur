package br.com.debeatur.api.serie;

import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/** Atualização periódica do cache das séries; ver {@link PropriedadesSeries#atualizacao()}. */
@Component
class AtualizacaoSeries {

    private final CacheSeries cache;

    AtualizacaoSeries(CacheSeries cache) {
        this.cache = cache;
    }

    @Scheduled(cron = "${debeatur.series.atualizacao:0 0 6 * * *}", zone = "America/Sao_Paulo")
    void atualizar() {
        cache.atualizarTodas();
    }
}
