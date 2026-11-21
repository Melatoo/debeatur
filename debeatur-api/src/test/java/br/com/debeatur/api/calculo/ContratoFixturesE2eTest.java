package br.com.debeatur.api.calculo;

import br.com.debeatur.api.acesso.ComUsuarioLogado;
import br.com.debeatur.api.serie.FonteSeries;
import br.com.debeatur.core.indice.Indice;
import br.com.debeatur.core.indice.IndiceIndisponivelException;
import br.com.debeatur.core.indice.SalarioMinimoEmMemoria;
import br.com.debeatur.core.indice.SerieIndicesEmMemoria;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.assertj.MockMvcTester;
import org.springframework.test.web.servlet.assertj.MvcTestResult;
import tools.jackson.core.type.TypeReference;
import tools.jackson.databind.DeserializationFeature;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.YearMonth;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;
import java.util.TreeSet;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

/**
 * Contrato entre a API e as respostas simuladas do e2e ({@code frontend/e2e/fixtures}).
 * <p>
 * O e2e roda a tela contra essas fixtures, não contra a API. Se a API muda o formato de uma
 * resposta e a fixture fica como estava, o e2e continua verde e a tela quebra em produção. Este
 * teste compara a <b>forma</b> (campos e tipos) de cada fixture com a resposta de verdade, nos dois
 * sentidos: campo que a fixture tem e a API não manda, e campo que a API manda e a fixture não
 * tem. Valores não entram (os índices aqui são fictícios), salvo no regime padrão, que é fixo.
 * <p>
 * Quando este teste falha depois de uma mudança intencional na API, a correção é regravar a
 * fixture a partir de uma resposta real e rodar o e2e, não afrouxar a comparação.
 */
@WebMvcTest({CalculoController.class, RegimeController.class})
@Import({CalculoService.class, ComUsuarioLogado.class})
class ContratoFixturesE2eTest {

    private static final Path FIXTURES = Path.of("..", "frontend", "e2e", "fixtures");

    @Autowired
    private MockMvcTester mvc;

    @Autowired
    private JsonMapper json;

    @MockitoBean
    private FonteSeries series;

    @Test
    void oPedidoQueATelaMandaEAceitoEODemonstrativoTemAFormaDaFixture() {
        seriesFicticias();

        MvcTestResult resposta = mvc.post().uri("/api/calculos").contentType(MediaType.APPLICATION_JSON)
                .content(fixture("pedido.json")).exchange();

        assertThat(resposta).as("POST /api/calculos com pedido.json").hasStatusOk();
        JsonNode api = json.readTree(corpo(resposta));
        assertThat(diferencasDeForma(arvore("demonstrativo.json"), api)).as("demonstrativo.json").isEmpty();
        assertThat(diferencasDeForma(arvore("demonstrativo-rendimentos.json"), api))
                .as("demonstrativo-rendimentos.json").isEmpty();
    }

    @Test
    void oRegimePadraoEIgualAFixture() {
        MvcTestResult resposta = mvc.get().uri("/api/regimes/padrao").exchange();

        assertThat(resposta).hasStatusOk();
        assertThat(json.readTree(corpo(resposta))).as("regime-padrao.json").isEqualTo(arvore("regime-padrao.json"));
    }

    @Test
    void oRegimeDoTema1368EIgualAFixture() {
        MvcTestResult resposta = mvc.get().uri("/api/regimes/tema-1368").exchange();

        assertThat(resposta).hasStatusOk();
        assertThat(json.readTree(corpo(resposta))).as("regime-tema-1368.json").isEqualTo(arvore("regime-tema-1368.json"));
    }

    @Test
    void aSerieIndisponivelTemAFormaDaFixture() {
        seriesFicticias();
        when(series.carregar(any(), any(), any())).thenThrow(new IndiceIndisponivelException(Indice.IPCA,
                YearMonth.of(2026, 10)));

        MvcTestResult resposta = mvc.post().uri("/api/calculos").contentType(MediaType.APPLICATION_JSON)
                .content(fixture("pedido.json")).exchange();

        assertThat(resposta).hasStatus(422);
        assertThat(diferencasDeForma(arvore("serie-indisponivel.json"), json.readTree(corpo(resposta))))
                .as("serie-indisponivel.json").isEmpty();
    }

    /**
     * Os cálculos salvos precisam do banco para sair da API; aqui a fixture passa pelos próprios
     * tipos da resposta, sem aceitar campo desconhecido, e volta a JSON. Campo a mais na fixture
     * falha na leitura; campo a menos aparece na volta.
     */
    @Test
    void osCalculosSalvosTemOsCamposDosTiposDaResposta() {
        JsonMapper estrito = json.rebuild().enable(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES).build();

        JsonNode salvo = arvore("calculo-salvo.json");
        JsonNode salvoDeVolta = estrito.valueToTree(estrito.treeToValue(salvo, CalculoSalvoDtos.CalculoSalvo.class));
        assertThat(diferencasDeForma(salvo, salvoDeVolta)).as("calculo-salvo.json").isEmpty();

        JsonNode lista = arvore("calculos-salvos.json");
        JsonNode listaDeVolta = estrito.valueToTree(estrito.readValue(fixture("calculos-salvos.json"),
                new TypeReference<List<CalculoSalvoDtos.Resumo>>() {
                }));
        assertThat(diferencasDeForma(lista, listaDeVolta)).as("calculos-salvos.json").isEmpty();
    }

    /** Todos os índices e o salário mínimo em todos os meses, com valores quaisquer. */
    private void seriesFicticias() {
        SerieIndicesEmMemoria serie = new SerieIndicesEmMemoria();
        SalarioMinimoEmMemoria salarioMinimo = new SalarioMinimoEmMemoria();
        for (YearMonth mes = YearMonth.of(1994, 7); mes.isBefore(YearMonth.of(2027, 1)); mes = mes.plusMonths(1)) {
            for (Indice indice : Indice.values()) {
                serie.com(indice, mes, "0.10");
            }
            salarioMinimo.com(mes, "1412.00");
        }
        when(series.carregar(any(), any(), any())).thenReturn(serie);
        when(series.carregarSalarioMinimo(any(), any())).thenReturn(salarioMinimo);
    }

    /**
     * Diferenças de forma entre a fixture e a resposta. Cada caminho tem um tipo; os elementos de
     * uma lista se juntam em {@code []}. Nulo combina com qualquer tipo, e o que está dentro de um
     * nulo ou de uma lista vazia do outro lado não se compara.
     */
    static List<String> diferencasDeForma(JsonNode fixture, JsonNode api) {
        Map<String, String> daFixture = forma(fixture);
        Map<String, String> daApi = forma(api);
        List<String> diferencas = new ArrayList<>();
        for (String caminho : new TreeSet<>(union(daFixture, daApi))) {
            String f = daFixture.get(caminho);
            String a = daApi.get(caminho);
            if (f != null && a != null) {
                if (!f.equals(a) && !f.equals("null") && !a.equals("null")) {
                    diferencas.add(caminho + ": " + f + " na fixture, " + a + " na API");
                }
                continue;
            }
            String pai = pai(caminho);
            Map<String, String> outroLado = f == null ? daFixture : daApi;
            if (!"object".equals(outroLado.get(pai))) {
                continue;
            }
            diferencas.add(f == null
                    ? caminho + ": a API manda e a fixture não tem"
                    : caminho + ": a fixture tem e a API não manda");
        }
        return diferencas;
    }

    private static Map<String, String> forma(JsonNode raiz) {
        Map<String, String> forma = new TreeMap<>();
        juntar(raiz, "$", forma);
        return forma;
    }

    private static void juntar(JsonNode no, String caminho, Map<String, String> forma) {
        String tipo = no.getNodeType().name().toLowerCase();
        if (!tipo.equals("null") || !forma.containsKey(caminho)) {
            forma.put(caminho, tipo);
        }
        if (no.isObject()) {
            no.properties().forEach(campo -> juntar(campo.getValue(), caminho + "." + campo.getKey(), forma));
        } else if (no.isArray()) {
            no.values().forEach(elemento -> juntar(elemento, caminho + "[]", forma));
        }
    }

    private static String pai(String caminho) {
        if (caminho.endsWith("[]")) {
            return caminho.substring(0, caminho.length() - 2);
        }
        return caminho.substring(0, caminho.lastIndexOf('.'));
    }

    private static TreeSet<String> union(Map<String, String> a, Map<String, String> b) {
        TreeSet<String> todos = new TreeSet<>(a.keySet());
        todos.addAll(b.keySet());
        return todos;
    }

    private JsonNode arvore(String nome) {
        return json.readTree(fixture(nome));
    }

    private static String fixture(String nome) {
        try {
            return Files.readString(FIXTURES.resolve(nome), StandardCharsets.UTF_8);
        } catch (IOException e) {
            throw new IllegalStateException("Fixture do e2e não encontrada: " + nome, e);
        }
    }

    private static String corpo(MvcTestResult resposta) {
        try {
            return resposta.getResponse().getContentAsString(StandardCharsets.UTF_8);
        } catch (java.io.UnsupportedEncodingException e) {
            throw new IllegalStateException(e);
        }
    }
}
