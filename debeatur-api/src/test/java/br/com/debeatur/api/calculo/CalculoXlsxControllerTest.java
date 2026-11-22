package br.com.debeatur.api.calculo;

import br.com.debeatur.api.acesso.ComUsuarioLogado;
import br.com.debeatur.api.serie.FonteSeries;
import br.com.debeatur.core.indice.Indice;
import br.com.debeatur.core.indice.SalarioMinimoEmMemoria;
import br.com.debeatur.core.indice.SerieIndicesEmMemoria;
import com.jayway.jsonpath.JsonPath;
import org.dhatim.fastexcel.reader.Cell;
import org.dhatim.fastexcel.reader.CellType;
import org.dhatim.fastexcel.reader.ReadableWorkbook;
import org.dhatim.fastexcel.reader.ReadingOptions;
import org.dhatim.fastexcel.reader.Row;
import org.dhatim.fastexcel.reader.Sheet;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.assertj.MockMvcTester;
import org.springframework.test.web.servlet.assertj.MvcTestResult;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.time.InstantSource;
import java.time.LocalDate;
import java.time.YearMonth;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

/**
 * Demonstrativo em planilha. Como no teste do PDF, os índices são fictícios e os valores
 * esperados vêm da resposta JSON do mesmo pedido: o que se testa é a planilha trazer, como
 * números e datas, o que o cálculo devolveu.
 */
@WebMvcTest(CalculoController.class)
@Import({CalculoService.class, ComUsuarioLogado.class})
class CalculoXlsxControllerTest {

    private static final MediaType XLSX = MediaType.parseMediaType(DemonstrativoXlsx.TIPO);

    @Autowired
    private MockMvcTester mvc;

    @Autowired
    private CalculoService service;

    @MockitoBean
    private FonteSeries series;

    private MvcTestResult post(String uri, String json) {
        return mvc.post().uri(uri).contentType(MediaType.APPLICATION_JSON).content(json).exchange();
    }

    /** As linhas de cada aba, pelo nome da aba. */
    private static Map<String, List<Row>> abas(byte[] xlsx) throws IOException {
        try (ReadableWorkbook planilha = new ReadableWorkbook(new ByteArrayInputStream(xlsx), new ReadingOptions(true, false))) {
            return planilha.getSheets().collect(Collectors.toMap(Sheet::getName, aba -> {
                try {
                    return aba.read();
                } catch (IOException e) {
                    throw new UncheckedIOException(e);
                }
            }, (a, b) -> a, java.util.LinkedHashMap::new));
        }
    }

    private Map<String, List<Row>> planilha(String pedido) throws IOException {
        return abas(post("/api/calculos/xlsx", pedido).getResponse().getContentAsByteArray());
    }

    /** A primeira linha cuja primeira célula tem esse texto. */
    private static Row linha(List<Row> aba, String primeiraCelula) {
        return aba.stream()
                .filter(r -> r.getCellAsString(0).filter(primeiraCelula::equals).isPresent())
                .findFirst()
                .orElseThrow(() -> new AssertionError("sem a linha \"" + primeiraCelula + "\""));
    }

    /** O texto de todas as células da aba, linha a linha. */
    private static String texto(List<Row> aba) {
        return aba.stream()
                .map(r -> r.stream().filter(c -> c != null).map(Cell::getText).collect(Collectors.joining(" | ")))
                .collect(Collectors.joining("\n"));
    }

    private static BigDecimal numero(MvcTestResult json, String caminho) {
        String corpo = new String(json.getResponse().getContentAsByteArray(), StandardCharsets.UTF_8);
        return new BigDecimal(JsonPath.<Number>read(corpo, caminho).toString());
    }

    private static Cell celula(Row linha, int coluna) {
        return linha.getCell(coluna);
    }

    /** Pensão de 09/2023 a 04/2024, ajuizada em 01/03/2024: a penhora fica com 09 a 11/2023. */
    private static final String PENSAO_COM_RITOS = """
            {"dataBase": "2024-05-15", "aplicarArt523": true,
             "processo": {"numero": "5001234-56.2024.8.13.0024", "juizo": "2ª Vara de Família de Belo Horizonte",
                          "exequente": "A. B. C., representada por D. B.", "executado": "E. F. C."},
             "regime": {"nome": "Sentença: INPC + 1% a.m.",
                        "regras": [{"inicio": "2023-01", "correcao": "INPC", "juros": "TAXA_FIXA", "percentualJurosMensal": 1}]},
             "pensao": {"percentual": 30, "diaVencimento": 10, "primeiroMes": "2023-09", "ultimoMes": "2024-04",
                        "inicioJuros": "VENCIMENTO", "ajuizamento": "2024-03-01",
                        "pagamentos": [{"mes": "2024-02", "valor": 200.00}]}}
            """;

    private void indicesDaPensao() {
        SerieIndicesEmMemoria serie = new SerieIndicesEmMemoria();
        SalarioMinimoEmMemoria salario = new SalarioMinimoEmMemoria();
        for (YearMonth mes = YearMonth.of(2023, 9); mes.isBefore(YearMonth.of(2024, 5)); mes = mes.plusMonths(1)) {
            serie.com(Indice.INPC, mes, "0.50");
            salario.com(mes, mes.getYear() == 2023 ? "1320.00" : "1412.00");
        }
        when(series.carregar(any(), any(), any())).thenReturn(serie);
        when(series.carregarSalarioMinimo(any(), any())).thenReturn(salario);
    }

    @Test
    void devolveAPlanilhaComoAnexo() throws IOException {
        indicesDaPensao();

        MvcTestResult resposta = post("/api/calculos/xlsx", PENSAO_COM_RITOS);

        assertThat(resposta).hasStatusOk().hasContentType(XLSX);
        assertThat(resposta.getResponse().getHeader(HttpHeaders.CONTENT_DISPOSITION))
                .startsWith("attachment;")
                .containsIgnoringWhitespaces("demonstrativo-5001234-56.2024.8.13.0024-2024-05-15.xlsx");
        assertThat(abas(resposta.getResponse().getContentAsByteArray()).keySet())
                .containsExactly("Demonstrativo", "Memória de cálculo");
    }

    @Test
    void recusaOPedidoInvalidoComoOPdf() {
        assertThat(post("/api/calculos/xlsx", "{\"aplicarArt523\": true}")).hasStatus(400);
    }

    @Test
    void demonstrativoTrazOProcessoOsCriteriosEOResumo() throws IOException {
        indicesDaPensao();
        InstantSource relogio = InstantSource.fixed(Instant.parse("2026-11-03T02:30:00Z"));
        MockMvcTester comRelogio = MockMvcTester.of(new CalculoController(service, relogio));
        MvcTestResult json = post("/api/calculos", PENSAO_COM_RITOS);

        List<Row> aba = abas(comRelogio.post().uri("/api/calculos/xlsx").contentType(MediaType.APPLICATION_JSON)
                .content(PENSAO_COM_RITOS).exchange().getResponse().getContentAsByteArray()).get("Demonstrativo");

        assertThat(texto(aba))
                .contains("Execução de alimentos (arts. 524 e 528 do CPC)")
                .contains("Processo | 5001234-56.2024.8.13.0024")
                .contains("Juízo | 2ª Vara de Família de Belo Horizonte")
                .contains("Data-base do cálculo | 15/05/2024")
                // no horário de Brasília, como no PDF
                .contains("Emitido em | 02/11/2026 às 23:30")
                .contains("Pensão | 30% do salário mínimo vigente em cada mês, com vencimento no dia 10")
                .contains("Pagamentos parciais | 02/2024: R$ 200,00")
                .contains("• desde 09/2023: INPC + 1% a.m.")
                .contains("Salário mínimo nacional");
        // os valores do resumo são números, com os do motor
        assertThat(celula(linha(aba, "Multa de 10% (art. 523, §1º, do CPC)"), 1).asNumber())
                .isEqualByComparingTo(numero(json, "$.multaArt523"));
        assertThat(celula(linha(aba, "Rito da penhora: total com os acréscimos"), 1).asNumber())
                .isEqualByComparingTo(numero(json, "$.ritos.totalPenhora"));
        Cell total = celula(linha(aba, "Total da execução"), 1);
        assertThat(total.getType()).isEqualTo(CellType.NUMBER);
        assertThat(total.asNumber()).isEqualByComparingTo(numero(json, "$.total"));
    }

    @Test
    void memoriaGuardaDatasENumerosDeCadaParcela() throws IOException {
        indicesDaPensao();
        MvcTestResult json = post("/api/calculos", PENSAO_COM_RITOS);

        List<Row> aba = planilha(PENSAO_COM_RITOS).get("Memória de cálculo");

        Row cabecalho = linha(aba, "Parcela");
        assertThat(cabecalho.stream().map(Cell::getText).toList()).containsExactly(
                "Parcela", "Correção desde", "Juros desde", "Salário mínimo (R$)", "Devido (R$)", "Pago (R$)",
                "Fator de correção", "Corrigido (R$)", "Juros (%)", "Juros (R$)", "Atualizado (R$)");

        Row setembro = linha(aba, "09/2023");
        assertThat(celula(setembro, 1).asDate().toLocalDate()).isEqualTo(LocalDate.of(2023, 9, 10));
        assertThat(celula(setembro, 1).getDataFormatString()).isEqualTo("dd/mm/yyyy");
        assertThat(celula(setembro, 3).asNumber()).isEqualByComparingTo("1320.00");
        assertThat(celula(setembro, 4).asNumber()).isEqualByComparingTo("396.00");
        // sem pagamento, o traço, como no PDF
        assertThat(celula(setembro, 5).getText()).isEqualTo("—");
        // o fator com mais casas que as sete que aparecem (o Excel guarda cerca de 15 dígitos)
        BigDecimal fator = numero(json, "$.linhas[0].fatorCorrecao");
        assertThat(celula(setembro, 6).asNumber().setScale(12, RoundingMode.HALF_UP))
                .isEqualByComparingTo(fator.setScale(12, RoundingMode.HALF_UP));
        assertThat(celula(setembro, 6).getDataFormatString()).isEqualTo("0.0000000");
        assertThat(celula(setembro, 7).asNumber()).isEqualByComparingTo(numero(json, "$.linhas[0].valorCorrigido"));
        assertThat(celula(setembro, 8).asNumber()).isEqualByComparingTo("8");
        assertThat(celula(setembro, 10).asNumber()).isEqualByComparingTo(numero(json, "$.linhas[0].subtotal"));

        assertThat(celula(linha(aba, "02/2024"), 5).asNumber()).isEqualByComparingTo("200.00");
        assertThat(texto(aba)).contains("Rito da prisão").contains("Rito da penhora");
        Row prisao = linha(aba, "Subtotal, 5 parcelas");
        assertThat(celula(prisao, 7).asNumber()).isEqualByComparingTo(numero(json, "$.ritos.prisao.totalCorrigido"));
        assertThat(celula(prisao, 10).asNumber()).isEqualByComparingTo(numero(json, "$.ritos.prisao.debitoAtualizado"));
        assertThat(celula(linha(aba, "Subtotal, 3 parcelas"), 9).asNumber())
                .isEqualByComparingTo(numero(json, "$.ritos.penhora.totalJuros"));
        assertThat(celula(linha(aba, "Total da penhora"), 10).asNumber())
                .isEqualByComparingTo(numero(json, "$.ritos.totalPenhora"));
        assertThat(celula(linha(aba, "Total da execução"), 10).asNumber()).isEqualByComparingTo(numero(json, "$.total"));
    }

    @Test
    void totaisSaoValoresDoMotorENaoFormulas() throws IOException {
        indicesDaPensao();

        Map<String, List<Row>> planilha = planilha(PENSAO_COM_RITOS);

        assertThat(planilha.values().stream().flatMap(List::stream).flatMap(Row::stream)
                .filter(c -> c != null && c.getType() == CellType.FORMULA)).isEmpty();
    }

    @Test
    void trazOsAbatimentosEOSaldoSemRitos() throws IOException {
        indicesDaPensao();
        String pedido = """
                {"dataBase": "2024-05-15", "aplicarArt523": true,
                 "regime": {"regras": [{"inicio": "2023-01", "correcao": "INPC", "juros": "TAXA_FIXA", "percentualJurosMensal": 1}]},
                 "pensao": {"percentual": 30, "diaVencimento": 10, "primeiroMes": "2023-09", "ultimoMes": "2024-04",
                            "inicioJuros": "VENCIMENTO",
                            "abatimentos": [{"data": "2024-05-02", "valor": 300.00, "origem": "Depósito"}]}}
                """;
        MvcTestResult json = post("/api/calculos", pedido);
        assertThat(json).hasStatusOk();

        Map<String, List<Row>> planilha = planilha(pedido);

        List<Row> memoria = planilha.get("Memória de cálculo");
        assertThat(texto(memoria)).doesNotContain("Rito da");
        Row abatimento = linha(memoria, "Abatimento de 02/05/2024 (Depósito)");
        int ultima = linha(memoria, "Parcela").getCellCount() - 1;
        assertThat(celula(abatimento, ultima).asNumber()).isEqualByComparingTo("-300.00");
        assertThat(celula(linha(memoria, "Saldo"), ultima).asNumber())
                .isEqualByComparingTo(numero(json, "$.debitoAtualizado").subtract(new BigDecimal("300.00")));
        assertThat(celula(linha(memoria, "Total da execução"), ultima).asNumber()).isEqualByComparingTo(numero(json, "$.total"));

        List<Row> demonstrativo = planilha.get("Demonstrativo");
        assertThat(celula(linha(demonstrativo, "Abatimentos (valor nominal)"), 1).asNumber()).isEqualByComparingTo("-300.00");
    }

    @Test
    void parcelasAvulsasSemJurosNemProcesso() throws IOException {
        when(series.carregar(any(), any(), any())).thenReturn(new SerieIndicesEmMemoria()
                .com(Indice.IPCA, YearMonth.of(2024, 10), "0.56")
                .com(Indice.IPCA, YearMonth.of(2024, 11), "0.39"));
        String pedido = """
                {"dataBase": "2024-12-15", "aplicarArt523": false,
                 "regime": {"nome": "Sentença", "regras": [{"inicio": "2024-09", "correcao": "IPCA", "juros": "TAXA_LEGAL"}]},
                 "parcelas": [{"descricao": "Reembolso", "valor": 1000.00, "inicioCorrecao": "2024-10-01"}]}
                """;

        Map<String, List<Row>> planilha = planilha(pedido);

        Row reembolso = linha(planilha.get("Memória de cálculo"), "Reembolso");
        assertThat(celula(reembolso, 1).asDate().toLocalDate()).isEqualTo(LocalDate.of(2024, 10, 1));
        assertThat(celula(reembolso, 2).getText()).isEqualTo("sem juros");
        assertThat(celula(reembolso, 3).asNumber()).isEqualByComparingTo("1000.00");
        assertThat(texto(planilha.get("Demonstrativo")))
                .contains("Cumprimento de sentença (art. 524 do CPC)")
                .contains("Art. 523, §1º | Não aplicados")
                .doesNotContain("Processo |");
    }
}
