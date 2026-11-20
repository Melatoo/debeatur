package br.com.debeatur.indices;

import br.com.debeatur.core.indice.Indice;
import br.com.debeatur.core.indice.SalarioMinimoEmMemoria;
import br.com.debeatur.core.indice.SerieIndicesEmMemoria;
import tools.jackson.core.type.TypeReference;
import tools.jackson.databind.json.JsonMapper;

import java.io.IOException;
import java.math.BigDecimal;
import java.math.MathContext;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.time.LocalDate;
import java.time.YearMonth;
import java.time.format.DateTimeFormatter;
import java.util.Collection;
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
            Indice.SELIC, 11,
            Indice.TAXA_LEGAL, 29543);
    /** Série 1619: salário mínimo nacional, valor vigente em cada mês. */
    public static final int CODIGO_SALARIO_MINIMO = 1619;
    /**
     * Série 4390: SELIC acumulada no mês, com duas casas. Foi a fonte da SELIC até 11/2026; hoje só
     * aparece na citação dos cálculos salvos antes disso (ver {@link #fonte(Indice, Collection)}).
     */
    static final int CODIGO_SELIC_MENSAL = 4390;
    /** O SGS aceita no máximo 10 anos por consulta a uma série diária. */
    private static final int ANOS_POR_CONSULTA_DIARIA = 5;

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

    /** Código da série do SGS de onde vêm as variações mensais do índice. */
    public static int codigo(Indice indice) {
        return CODIGOS.get(indice);
    }

    /**
     * Código da série do SGS de onde vieram os valores de um cálculo. A SELIC gravada por um cálculo
     * salvo antes da SGS 11 vem da 4390, que publica duas casas; a acumulada da SGS 11 nunca fica
     * com duas casas ou menos, então são as casas que mostram a origem.
     */
    public static int codigo(Indice indice, Collection<BigDecimal> valores) {
        boolean daSerieMensal = !valores.isEmpty()
                && valores.stream().allMatch(v -> v.stripTrailingZeros().scale() <= 2);
        return indice == Indice.SELIC && daSerieMensal ? CODIGO_SELIC_MENSAL : codigo(indice);
    }

    /** A série do SGS de onde vieram os valores de um cálculo, como o demonstrativo a cita. */
    public static String fonte(Indice indice, Collection<BigDecimal> valores) {
        int codigo = codigo(indice, valores);
        if (codigo == codigo(Indice.SELIC)) {
            return "série 11 do SGS/BCB (SELIC diária, acumulada nos dias úteis de cada mês)";
        }
        return "série " + codigo + " do SGS/BCB";
    }

    /** Variações mensais do índice entre os meses informados (inclusive). */
    public Map<YearMonth, BigDecimal> buscar(Indice indice, YearMonth de, YearMonth ate) {
        if (indice == Indice.SELIC) {
            return buscarSelic(de, ate);
        }
        return consultar(CODIGOS.get(indice), indice.name(), de, ate);
    }

    /**
     * SELIC de cada mês pelo produto da SELIC diária (série 11) nos dias úteis do mês, como o CADEJ
     * do TJMG e a planilha "SELIC Acumulada" da DPMG (divergência 6 do docs/gabarito.md).
     * <p>
     * Um mês só entra quando a série já tem um dia do mês seguinte: a SELIC diária sai em ordem,
     * então aí o mês está completo. O mês corrente, com parte dos dias, fica de fora, e o motor o
     * acusa como índice indisponível. A série mensal 4390 não serve para isso, porque já publica
     * o mês corrente pela metade.
     */
    private Map<YearMonth, BigDecimal> buscarSelic(YearMonth de, YearMonth ate) {
        TreeMap<YearMonth, BigDecimal> diaria = new TreeMap<>();
        YearMonth limite = ate.plusMonths(1);
        for (YearMonth inicio = de; !inicio.isAfter(limite); inicio = inicio.plusYears(ANOS_POR_CONSULTA_DIARIA)) {
            YearMonth fim = inicio.plusYears(ANOS_POR_CONSULTA_DIARIA).minusMonths(1);
            diaria.putAll(acumularPorMes(consultarCorpo(codigo(Indice.SELIC), "SELIC diária", inicio,
                    fim.isAfter(limite) ? limite : fim)));
        }
        if (diaria.isEmpty()) {
            return diaria;
        }
        // o último mês com valor ainda pode estar aberto; os anteriores, não
        YearMonth ultimoFechado = diaria.lastKey().minusMonths(1);
        if (ultimoFechado.isBefore(de)) {
            // a consulta começa no mês aberto, como a reconsulta do cache: nenhum mês fechou ainda
            return new TreeMap<>();
        }
        return new TreeMap<>(diaria.subMap(de, true, ultimoFechado.isBefore(ate) ? ultimoFechado : ate, true));
    }

    public SerieIndicesEmMemoria carregar(Set<Indice> indices, YearMonth de, YearMonth ate) {
        SerieIndicesEmMemoria serie = new SerieIndicesEmMemoria();
        indices.forEach(indice -> serie.comTodos(indice, buscar(indice, de, ate)));
        return serie;
    }

    /** Salário mínimo vigente em cada mês entre os meses informados (inclusive), em reais. */
    public Map<YearMonth, BigDecimal> buscarSalarioMinimo(YearMonth de, YearMonth ate) {
        return consultar(CODIGO_SALARIO_MINIMO, "salário mínimo", de, ate);
    }

    public SalarioMinimoEmMemoria carregarSalarioMinimo(YearMonth de, YearMonth ate) {
        return new SalarioMinimoEmMemoria().comTodos(buscarSalarioMinimo(de, ate));
    }

    private Map<YearMonth, BigDecimal> consultar(int codigo, String serie, YearMonth de, YearMonth ate) {
        String corpo = consultarCorpo(codigo, serie, de, ate);
        if (corpo == null) {
            return new TreeMap<>();
        }
        try {
            return interpretar(corpo);
        } catch (RuntimeException e) {
            // uma página de manutenção com 200, por exemplo: é falha do SGS, e não do pedido
            throw new SgsException("Resposta inválida do SGS para " + serie, e);
        }
    }

    /** O corpo da resposta do SGS, ou {@code null} se o período não tem valor publicado. */
    private String consultarCorpo(int codigo, String serie, YearMonth de, YearMonth ate) {
        URI uri = baseUrl.resolve("bcdata.sgs.%d/dados?formato=json&dataInicial=%s&dataFinal=%s".formatted(
                codigo, de.atDay(1).format(DATA_BR), ate.atEndOfMonth().format(DATA_BR)));
        HttpRequest request = HttpRequest.newBuilder(uri).timeout(Duration.ofSeconds(30)).GET().build();
        try {
            HttpResponse<String> response = http.send(request, HttpResponse.BodyHandlers.ofString());
            if (semValores(response)) {
                return null;
            }
            if (response.statusCode() != 200) {
                throw new SgsException("SGS respondeu " + response.statusCode() + " para " + serie);
            }
            return response.body();
        } catch (IOException e) {
            throw new SgsException("Falha ao consultar o SGS para " + serie, e);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new SgsException("Consulta ao SGS interrompida", e);
        }
    }

    /**
     * O SGS responde 404 com "Value(s) not found" quando não há valor no período (um mês ainda
     * não publicado, ou a taxa legal antes de 08/2024). Qualquer outro 404 continua sendo falha.
     */
    private static boolean semValores(HttpResponse<String> response) {
        return response.statusCode() == 404 && response.body() != null
                && response.body().contains("Value(s) not found");
    }

    Map<YearMonth, BigDecimal> interpretar(String corpo) {
        Map<YearMonth, BigDecimal> serie = new TreeMap<>();
        for (PontoSgs ponto : pontos(corpo)) {
            serie.put(YearMonth.from(LocalDate.parse(ponto.data(), DATA_BR)), new BigDecimal(ponto.valor()));
        }
        return serie;
    }

    /**
     * Taxa de cada mês, em pontos percentuais, a partir de uma série diária em % ao dia: o produto
     * de (1 + taxa do dia) nos dias publicados do mês, menos 1. Sem arredondamento, como os
     * fatores do motor.
     */
    Map<YearMonth, BigDecimal> acumularPorMes(String corpo) {
        Map<YearMonth, BigDecimal> fatores = new TreeMap<>();
        if (corpo == null) {
            return fatores;
        }
        try {
            for (PontoSgs ponto : pontos(corpo)) {
                BigDecimal fatorDia = BigDecimal.ONE.add(new BigDecimal(ponto.valor()).movePointLeft(2));
                fatores.merge(YearMonth.from(LocalDate.parse(ponto.data(), DATA_BR)), fatorDia,
                        (a, b) -> a.multiply(b, MathContext.DECIMAL128));
            }
        } catch (RuntimeException e) {
            throw new SgsException("Resposta inválida do SGS para a SELIC diária", e);
        }
        fatores.replaceAll((mes, fator) -> fator.subtract(BigDecimal.ONE).movePointRight(2));
        return fatores;
    }

    private List<PontoSgs> pontos(String corpo) {
        return json.readValue(corpo, new TypeReference<List<PontoSgs>>() {
        });
    }

    record PontoSgs(String data, String valor) {
    }
}
