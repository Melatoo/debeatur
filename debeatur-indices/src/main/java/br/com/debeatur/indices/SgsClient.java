package br.com.debeatur.indices;

import br.com.debeatur.core.indice.Indice;
import br.com.debeatur.core.indice.SerieIndicesEmMemoria;
import tools.jackson.core.type.TypeReference;
import tools.jackson.databind.json.JsonMapper;

import java.io.IOException;
import java.math.BigDecimal;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.time.LocalDate;
import java.time.YearMonth;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;

/**
 * Cliente do Sistema Gerenciador de Séries Temporais (SGS) do Banco Central.
 *
 * @see <a href="https://dadosabertos.bcb.gov.br/">Dados abertos do BCB</a>
 */
public class SgsClient {

    public static final URI URL_PADRAO = URI.create("https://api.bcb.gov.br/dados/serie/");

    private static final DateTimeFormatter DATA_BR = DateTimeFormatter.ofPattern("dd/MM/yyyy");
    private static final Map<Indice, Integer> CODIGOS = Map.of(
            Indice.IPCA, 433,
            Indice.INPC, 188,
            Indice.IPCA_E, 10764,
            Indice.SELIC, 4390,
            Indice.TAXA_LEGAL, 29543);

    private final HttpClient http;
    private final URI baseUrl;
    private final JsonMapper json = JsonMapper.builder().build();

    public SgsClient() {
        this(HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(10)).build(), URL_PADRAO);
    }

    public SgsClient(HttpClient http, URI baseUrl) {
        this.http = http;
        this.baseUrl = baseUrl;
    }

    /** Variações mensais do índice entre os meses informados (inclusive). */
    public Map<YearMonth, BigDecimal> buscar(Indice indice, YearMonth de, YearMonth ate) {
        URI uri = baseUrl.resolve("bcdata.sgs.%d/dados?formato=json&dataInicial=%s&dataFinal=%s".formatted(
                CODIGOS.get(indice), de.atDay(1).format(DATA_BR), ate.atEndOfMonth().format(DATA_BR)));
        HttpRequest request = HttpRequest.newBuilder(uri).timeout(Duration.ofSeconds(30)).GET().build();
        try {
            HttpResponse<String> response = http.send(request, HttpResponse.BodyHandlers.ofString());
            if (response.statusCode() != 200) {
                throw new SgsException("SGS respondeu " + response.statusCode() + " para " + indice);
            }
            return interpretar(response.body());
        } catch (IOException e) {
            throw new SgsException("Falha ao consultar o SGS para " + indice, e);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new SgsException("Consulta ao SGS interrompida", e);
        }
    }

    public SerieIndicesEmMemoria carregar(Set<Indice> indices, YearMonth de, YearMonth ate) {
        SerieIndicesEmMemoria serie = new SerieIndicesEmMemoria();
        indices.forEach(indice -> serie.comTodos(indice, buscar(indice, de, ate)));
        return serie;
    }

    Map<YearMonth, BigDecimal> interpretar(String corpo) {
        List<PontoSgs> pontos = json.readValue(corpo, new TypeReference<List<PontoSgs>>() {
        });
        Map<YearMonth, BigDecimal> serie = new TreeMap<>();
        for (PontoSgs ponto : pontos) {
            serie.put(YearMonth.from(LocalDate.parse(ponto.data(), DATA_BR)), new BigDecimal(ponto.valor()));
        }
        return serie;
    }

    record PontoSgs(String data, String valor) {
    }
}
