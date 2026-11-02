package br.com.debeatur.api.calculo;

import br.com.debeatur.api.PostgresDeTeste;
import br.com.debeatur.api.serie.CacheSeries;
import br.com.debeatur.core.indice.Indice;
import br.com.debeatur.indices.SgsClient;
import br.com.debeatur.indices.SgsException;
import com.jayway.jsonpath.JsonPath;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.openpdf.text.pdf.PdfReader;
import org.openpdf.text.pdf.parser.PdfTextExtractor;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.assertj.MockMvcTester;
import org.springframework.test.web.servlet.assertj.MvcTestResult;
import org.springframework.transaction.annotation.Transactional;
import org.testcontainers.junit.jupiter.Testcontainers;
import tools.jackson.databind.json.JsonMapper;

import java.io.InputStream;
import java.math.BigDecimal;
import java.text.DecimalFormat;
import java.text.DecimalFormatSymbols;
import java.util.Locale;
import java.nio.charset.StandardCharsets;
import java.time.YearMonth;
import java.util.EnumMap;
import java.util.Map;
import java.util.TreeMap;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

/**
 * Cálculos salvos de ponta a ponta: API, cache das séries e PostgreSQL, com o SGS simulado.
 * Os índices são fictícios; o que se testa é a reprodução, não os valores do motor.
 */
@SpringBootTest(properties = "debeatur.series.atualizacao=-")
@AutoConfigureMockMvc
@Import(PostgresDeTeste.class)
@Testcontainers(disabledWithoutDocker = true)
@Transactional
class CalculoSalvoTest {

    /**
     * Pensão de 04 a 09/2024 com o regime padrão: INPC + 1% até julho, a transição de agosto e
     * IPCA + taxa legal em setembro; o ajuizamento separa os ritos e o art. 523 incide na penhora.
     */
    private static final String PEDIDO = """
            {"dataBase": "2024-10-15", "aplicarArt523": true,
             "pensao": {"percentual": 30, "diaVencimento": 10, "primeiroMes": "2024-04", "ultimoMes": "2024-09",
                        "inicioJuros": "VENCIMENTO", "ajuizamento": "2024-09-01",
                        "pagamentos": [{"mes": "2024-05", "valor": 100.00}]},
             "parcelas": [{"descricao": "Despesa escolar", "valor": 350.00, "inicioCorrecao": "2024-03-20"}],
             "processo": {"numero": "5001234-56.2024.8.13.0024"}}
            """;

    @Autowired
    private MockMvcTester mvc;

    @Autowired
    private JdbcClient jdbc;

    @Autowired
    private JsonMapper json;

    @Autowired
    private CacheSeries cache;

    @MockitoBean
    private SgsClient sgs;

    /** O que o SGS simulado publica no momento; o teste muda para simular revisões. */
    private final Map<Indice, Map<YearMonth, BigDecimal>> publicado = new EnumMap<>(Indice.class);
    private final Map<YearMonth, BigDecimal> salarioMinimo = new TreeMap<>();

    @BeforeEach
    void sgsSimulado() {
        publicar(Indice.INPC, "0.37", "0.37", "0.46", "0.25", "0.26", "-0.14", "0.48");
        publicar(Indice.IPCA, "0.16", "0.38", "0.46", "0.21", "0.38", "-0.02", "0.44");
        publicado.get(Indice.TAXA_LEGAL).clear();
        publicado.get(Indice.TAXA_LEGAL).put(YearMonth.of(2024, 8), new BigDecimal("0.605306"));
        publicado.get(Indice.TAXA_LEGAL).put(YearMonth.of(2024, 9), new BigDecimal("0.676227"));
        for (YearMonth mes = YearMonth.of(2024, 3); !mes.isAfter(YearMonth.of(2024, 9)); mes = mes.plusMonths(1)) {
            salarioMinimo.put(mes, new BigDecimal("1412.00"));
        }
        when(sgs.buscar(any(), any(), any())).thenAnswer(chamada -> {
            Indice indice = chamada.getArgument(0);
            return new TreeMap<>(publicado.getOrDefault(indice, Map.of()));
        });
        when(sgs.buscarSalarioMinimo(any(), any())).thenAnswer(chamada -> new TreeMap<>(salarioMinimo));
    }

    /** Variações de 03 a 09/2024. */
    private void publicar(Indice indice, String... variacoes) {
        Map<YearMonth, BigDecimal> meses = publicado.computeIfAbsent(indice, i -> new TreeMap<>());
        publicado.computeIfAbsent(Indice.TAXA_LEGAL, i -> new TreeMap<>());
        for (int i = 0; i < variacoes.length; i++) {
            meses.put(YearMonth.of(2024, 3).plusMonths(i), new BigDecimal(variacoes[i]));
        }
    }

    private String salvar(String titulo, String pedido) throws Exception {
        String corpo = titulo == null
                ? "{\"calculo\": %s}".formatted(pedido)
                : "{\"titulo\": \"%s\", \"calculo\": %s}".formatted(titulo, pedido);
        MvcTestResult resultado = mvc.post().uri("/api/calculos/salvos")
                .contentType(MediaType.APPLICATION_JSON).content(corpo).exchange();
        assertThat(resultado).hasStatus(201);
        return resultado.getResponse().getContentAsString(StandardCharsets.UTF_8);
    }

    private String obter(String uri) throws Exception {
        MvcTestResult resultado = mvc.get().uri(uri).exchange();
        assertThat(resultado).hasStatusOk();
        return resultado.getResponse().getContentAsString(StandardCharsets.UTF_8);
    }

    /** Só o demonstrativo da resposta; lido direto em {@link BigDecimal}, sem passar por double. */
    private record ComDemonstrativo(DemonstrativoResponse demonstrativo) {
    }

    /** O demonstrativo da resposta, para comparar com {@code equals}, que exige até a mesma escala. */
    private DemonstrativoResponse demonstrativo(String corpo) {
        return json.readValue(corpo, ComDemonstrativo.class).demonstrativo();
    }

    @Test
    void reabrirRefazOMesmoDemonstrativoMesmoDepoisDeOBcbRevisarASerie() throws Exception {
        String salvo = salvar(null, PEDIDO);
        String id = JsonPath.read(salvo, "$.id");

        // o BCB revisa o IPCA de setembro e a atualização do cache grava a revisão
        publicado.get(Indice.IPCA).put(YearMonth.of(2024, 9), new BigDecimal("0.54"));
        cache.atualizarTodas();
        // e o SGS sai do ar: reabrir não pode depender dele
        when(sgs.buscar(any(), any(), any())).thenThrow(new SgsException("SGS respondeu 503"));
        when(sgs.buscarSalarioMinimo(any(), any())).thenThrow(new SgsException("SGS respondeu 503"));
        String reaberto = obter("/api/calculos/salvos/" + id);

        assertThat(demonstrativo(reaberto)).isEqualTo(demonstrativo(salvo));

        // o mesmo pedido calculado agora, com o cache revisado, dá outro total
        MvcTestResult atual = mvc.post().uri("/api/calculos").contentType(MediaType.APPLICATION_JSON)
                .content(PEDIDO).exchange();
        assertThat(atual).hasStatusOk();
        BigDecimal totalAtual = new BigDecimal(JsonPath.read(atual.getResponse().getContentAsString(), "$.total").toString());
        assertThat(totalAtual).isNotEqualByComparingTo(demonstrativo(salvo).total());
    }

    @Test
    void oDemonstrativoSalvoEOMesmoDoCalculoSemSalvar() throws Exception {
        MvcTestResult calculado = mvc.post().uri("/api/calculos").contentType(MediaType.APPLICATION_JSON)
                .content(PEDIDO).exchange();
        assertThat(calculado).hasStatusOk();

        String salvo = salvar(null, PEDIDO);

        assertThat(demonstrativo(salvo)).isEqualTo(json.readValue(
                calculado.getResponse().getContentAsString(StandardCharsets.UTF_8), DemonstrativoResponse.class));
    }

    @Test
    void guardaOsValoresUsadosNoSnapshot() throws Exception {
        UUID id = UUID.fromString(JsonPath.read(salvar(null, PEDIDO), "$.id"));

        Map<String, Long> meses = new TreeMap<>();
        jdbc.sql("SELECT serie, count(*) AS meses FROM calculo_serie WHERE calculo_id = ? GROUP BY serie")
                .param(id)
                .query(rs -> {
                    meses.put(rs.getString("serie"), rs.getLong("meses"));
                });

        // índices de 03/2024 (a parcela avulsa) a 09/2024, o mês anterior à data-base; taxa legal só de 08 e 09
        assertThat(meses).containsExactlyInAnyOrderEntriesOf(Map.of(
                "INPC", 7L, "IPCA", 7L, "TAXA_LEGAL", 2L, "SALARIO_MINIMO", 6L));
        assertThat(jdbc.sql("SELECT valor FROM calculo_serie WHERE calculo_id = ? AND serie = 'TAXA_LEGAL' AND mes = '2024-09-01'")
                .param(id).query(BigDecimal.class).single()).isEqualTo(new BigDecimal("0.676227"));
    }

    @Test
    void guardaORegimeUsadoNoPedido() throws Exception {
        String salvo = salvar(null, PEDIDO);
        String id = JsonPath.read(salvo, "$.id");
        String padrao = obter("/api/regimes/padrao");

        String reaberto = obter("/api/calculos/salvos/" + id);

        assertThat(json.readTree(reaberto).get("calculo").get("regime")).isEqualTo(json.readTree(padrao));
        assertThat(json.readTree(reaberto).get("calculo").get("pensao").get("pagamentos").get(0).get("mes").asString())
                .isEqualTo("2024-05");
    }

    @Test
    void listaDoMaisRecenteParaOMaisAntigo() throws Exception {
        salvar("Primeiro", PEDIDO);
        salvar(null, PEDIDO);
        salvar(null, PEDIDO.replaceAll(",\\s*\"processo\": \\{[^}]*}", ""));

        assertThat(mvc.get().uri("/api/calculos/salvos")).hasStatusOk().bodyJson()
                .hasPathSatisfying("$", v -> v.assertThat().asArray().hasSize(3))
                .hasPathSatisfying("$[0].titulo", v -> v.assertThat().isEqualTo("Cálculo com data-base em 15/10/2024"))
                .hasPathSatisfying("$[1].titulo", v -> v.assertThat().isEqualTo("Processo 5001234-56.2024.8.13.0024"))
                .hasPathSatisfying("$[2].titulo", v -> v.assertThat().isEqualTo("Primeiro"))
                .hasPathSatisfying("$[2].dataBase", v -> v.assertThat().isEqualTo("2024-10-15"))
                .hasPathSatisfying("$[2].regime", v -> v.assertThat().asString().startsWith("Cível padrão TJMG"));
    }

    @Test
    void oTotalDaListaEODoDemonstrativo() throws Exception {
        String salvo = salvar(null, PEDIDO);

        BigDecimal total = new BigDecimal(JsonPath.read(obter("/api/calculos/salvos"), "$[0].total").toString());

        assertThat(total).isEqualByComparingTo(demonstrativo(salvo).total());
    }

    /**
     * O {@code PEDIDO} como o formato 1 o grava em {@code calculo.pedido}. Não atualize este
     * arquivo para um teste passar: os cálculos já salvos estão neste formato.
     */
    private static String pedidoV1() throws Exception {
        try (InputStream arquivo = CalculoSalvoTest.class.getResourceAsStream("/calculos-salvos/pedido-v1.json")) {
            return new String(arquivo.readAllBytes(), StandardCharsets.UTF_8);
        }
    }

    /** Compara pela igualdade do {@code jsonb}: os mesmos campos, e os números como {@code numeric}. */
    private boolean mesmoJson(String a, String b) {
        return jdbc.sql("SELECT CAST(? AS JSONB) = CAST(? AS JSONB)").params(a, b).query(Boolean.class).single();
    }

    @Test
    void oPedidoEGravadoNoFormatoCongelado() throws Exception {
        UUID id = UUID.fromString(JsonPath.read(salvar(null, PEDIDO), "$.id"));

        String gravado = jdbc.sql("SELECT pedido::text FROM calculo WHERE id = ?").param(id).query(String.class).single();

        // se o CalculoRequest mudou, os cálculos já salvos precisam de uma migração, e não de um arquivo novo
        assertThat(mesmoJson(gravado, pedidoV1())).as("pedido gravado: %s", gravado).isTrue();
    }

    @Test
    void reabreUmPedidoGravadoNoFormatoCongelado() throws Exception {
        String salvo = salvar(null, PEDIDO);
        String id = JsonPath.read(salvo, "$.id");
        jdbc.sql("UPDATE calculo SET pedido = CAST(? AS JSONB) WHERE id = ?").params(pedidoV1(), UUID.fromString(id)).update();

        String reaberto = obter("/api/calculos/salvos/" + id);

        String calculo = json.writeValueAsString(json.readTree(reaberto).get("calculo"));
        assertThat(mesmoJson(calculo, pedidoV1())).as("nenhum campo perdido na leitura: %s", calculo).isTrue();
        assertThat(demonstrativo(reaberto)).isEqualTo(demonstrativo(salvo));
    }

    private static String textoDoPdf(byte[] pdf) throws Exception {
        PdfReader leitor = new PdfReader(pdf);
        PdfTextExtractor extrator = new PdfTextExtractor(leitor);
        StringBuilder texto = new StringBuilder();
        for (int pagina = 1; pagina <= leitor.getNumberOfPages(); pagina++) {
            texto.append(extrator.getTextFromPage(pagina)).append('\n');
        }
        return texto.toString();
    }

    private static String reais(BigDecimal valor) {
        return new DecimalFormat("#,##0.00", DecimalFormatSymbols.getInstance(Locale.forLanguageTag("pt-BR")))
                .format(valor);
    }

    @Test
    void oPdfDoCalculoSalvoUsaOSnapshotMesmoDepoisDeOBcbRevisarASerie() throws Exception {
        String salvo = salvar(null, PEDIDO);
        String id = JsonPath.read(salvo, "$.id");
        BigDecimal totalSalvo = demonstrativo(salvo).total();

        publicado.get(Indice.IPCA).put(YearMonth.of(2024, 9), new BigDecimal("0.54"));
        cache.atualizarTodas();
        when(sgs.buscar(any(), any(), any())).thenThrow(new SgsException("SGS respondeu 503"));
        when(sgs.buscarSalarioMinimo(any(), any())).thenThrow(new SgsException("SGS respondeu 503"));
        MvcTestResult pdf = mvc.get().uri("/api/calculos/salvos/" + id + "/pdf").accept(MediaType.APPLICATION_PDF).exchange();

        assertThat(pdf).hasStatusOk().hasContentType(MediaType.APPLICATION_PDF);
        assertThat(pdf.getResponse().getHeader("Content-Disposition"))
                .contains("attachment").contains("demonstrativo-5001234-56.2024.8.13.0024-2024-10-15.pdf");
        String texto = textoDoPdf(pdf.getResponse().getContentAsByteArray());
        assertThat(texto).containsIgnoringWhitespaces("Total da execução")
                .containsIgnoringWhitespaces(reais(totalSalvo))
                .containsIgnoringWhitespaces("Calculado em")
                .containsIgnoringWhitespaces("quando o cálculo foi salvo");
    }

    @Test
    void pdfDeCalculoInexistenteDevolve404() {
        assertThat(mvc.get().uri("/api/calculos/salvos/" + UUID.randomUUID() + "/pdf")
                .accept(MediaType.APPLICATION_PDF, MediaType.APPLICATION_PROBLEM_JSON)).hasStatus(404);
    }

    @Test
    void calculoInexistenteDevolve404() {
        assertThat(mvc.get().uri("/api/calculos/salvos/" + UUID.randomUUID())).hasStatus(404);
    }

    @Test
    void pedidoInvalidoNaoSalva() {
        String pensaoAlemDaDataBase = PEDIDO.replace("\"ultimoMes\": \"2024-09\"", "\"ultimoMes\": \"2024-11\"");

        assertThat(mvc.post().uri("/api/calculos/salvos").contentType(MediaType.APPLICATION_JSON)
                .content("{\"calculo\": %s}".formatted(pensaoAlemDaDataBase))).hasStatus(400);
        assertThat(mvc.post().uri("/api/calculos/salvos").contentType(MediaType.APPLICATION_JSON)
                .content("{\"titulo\": \"Sem pedido\"}")).hasStatus(400);
        assertThat(jdbc.sql("SELECT count(*) FROM calculo").query(Long.class).single()).isZero();
    }
}
