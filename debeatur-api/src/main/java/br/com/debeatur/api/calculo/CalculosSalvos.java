package br.com.debeatur.api.calculo;

import br.com.debeatur.api.serie.FonteSeries;
import br.com.debeatur.api.serie.SnapshotSeries;
import org.springframework.stereotype.Service;
import tools.jackson.databind.json.JsonMapper;

import java.time.Instant;
import java.time.InstantSource;
import java.time.format.DateTimeFormatter;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.UUID;

/**
 * Cálculos salvos. Cada um guarda o pedido, com o regime usado, e o snapshot das séries que
 * usou; reabrir refaz o demonstrativo com esse snapshot, e não com o cache, para que ele saia
 * igual mesmo depois de o BCB revisar uma série.
 */
@Service
public class CalculosSalvos {

    /** Até a autenticação (#7), todo cálculo é deste escritório, criado pela migração V2. */
    static final UUID ESCRITORIO_PADRAO = UUID.fromString("00000000-0000-0000-0000-000000000001");

    private static final DateTimeFormatter DATA = DateTimeFormatter.ofPattern("dd/MM/yyyy");

    private final CalculoService calculos;
    private final FonteSeries series;
    private final RepositorioCalculos repositorio;
    private final JsonMapper json;
    private final InstantSource relogio;

    public CalculosSalvos(CalculoService calculos, FonteSeries series, RepositorioCalculos repositorio,
                          JsonMapper json, InstantSource relogio) {
        this.calculos = calculos;
        this.series = series;
        this.repositorio = repositorio;
        this.json = json;
        this.relogio = relogio;
    }

    /** Calcula com o cache das séries e salva o pedido com o snapshot do que foi usado. */
    CalculoSalvoDtos.CalculoSalvo salvar(CalculoSalvoDtos.SalvarRequest request) {
        SnapshotSeries.Gravacao gravacao = SnapshotSeries.gravando(series);
        DemonstrativoResponse demonstrativo = calculos.calcular(request.calculo(), gravacao);
        CalculoRequest pedido = request.calculo().comRegime(demonstrativo.regime());
        RepositorioCalculos.Resumo resumo = new RepositorioCalculos.Resumo(UUID.randomUUID(), titulo(request),
                pedido.dataBase(), demonstrativo.regime().nome(), demonstrativo.total(), agora());
        SnapshotSeries snapshot = gravacao.snapshot();
        repositorio.inserir(ESCRITORIO_PADRAO, resumo, json.writeValueAsString(pedido), snapshot);
        // O mesmo demonstrativo que a reabertura dará: os números são os de agora, e as séries passam
        // a dizer que são as guardadas com o cálculo.
        DemonstrativoResponse guardado = calculos.calcular(pedido,
                new SnapshotSeries(snapshot.valores(), resumo.criadoEm()));
        return new CalculoSalvoDtos.CalculoSalvo(resumo.id(), resumo.titulo(), resumo.criadoEm(), pedido, guardado);
    }

    List<CalculoSalvoDtos.Resumo> listar() {
        return repositorio.listar(ESCRITORIO_PADRAO).stream().map(CalculoSalvoDtos.Resumo::de).toList();
    }

    /** @throws CalculoNaoEncontradoException se o cálculo não existir no escritório */
    CalculoSalvoDtos.CalculoSalvo reabrir(UUID id) {
        RepositorioCalculos.Salvo salvo = repositorio.buscar(ESCRITORIO_PADRAO, id)
                .orElseThrow(() -> new CalculoNaoEncontradoException(id));
        CalculoRequest pedido = json.readValue(salvo.pedidoJson(), CalculoRequest.class);
        DemonstrativoResponse demonstrativo = calculos.calcular(pedido, salvo.snapshot());
        RepositorioCalculos.Resumo resumo = salvo.resumo();
        return new CalculoSalvoDtos.CalculoSalvo(resumo.id(), resumo.titulo(), resumo.criadoEm(), pedido, demonstrativo);
    }

    private static String titulo(CalculoSalvoDtos.SalvarRequest request) {
        if (request.titulo() != null && !request.titulo().isBlank()) {
            return request.titulo().strip();
        }
        ProcessoRequest processo = request.calculo().processo();
        if (processo != null && processo.numero() != null && !processo.numero().isBlank()) {
            return "Processo " + processo.numero().strip();
        }
        return "Cálculo com data-base em " + request.calculo().dataBase().format(DATA);
    }

    /** Truncado ao microssegundo, a precisão do TIMESTAMPTZ, para o salvo e o reaberto baterem. */
    private Instant agora() {
        return relogio.instant().truncatedTo(ChronoUnit.MICROS);
    }

    static class CalculoNaoEncontradoException extends RuntimeException {

        CalculoNaoEncontradoException(UUID id) {
            super("Cálculo " + id + " não encontrado");
        }
    }
}
