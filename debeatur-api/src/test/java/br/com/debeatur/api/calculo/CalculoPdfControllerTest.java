package br.com.debeatur.api.calculo;

import br.com.debeatur.core.indice.Indice;
import br.com.debeatur.core.indice.SalarioMinimoEmMemoria;
import br.com.debeatur.core.indice.SerieIndicesEmMemoria;
import br.com.debeatur.api.acesso.ComUsuarioLogado;
import br.com.debeatur.api.serie.FonteSeries;
import com.jayway.jsonpath.JsonPath;
import org.junit.jupiter.api.Test;
import org.openpdf.text.pdf.PRStream;
import org.openpdf.text.pdf.PdfDictionary;
import org.openpdf.text.pdf.PdfName;
import org.openpdf.text.pdf.PdfReader;
import org.openpdf.text.pdf.parser.PdfTextExtractor;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.assertj.MockMvcTester;
import org.springframework.test.web.servlet.assertj.MvcTestResult;

import java.io.IOException;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.nio.charset.StandardCharsets;
import java.text.DecimalFormat;
import java.text.DecimalFormatSymbols;
import java.time.Instant;
import java.time.InstantSource;
import java.time.YearMonth;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;
import java.util.stream.IntStream;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

/**
 * Demonstrativo em PDF. Os índices são fictícios: o que se testa é o PDF trazer o que o
 * cálculo devolveu, e os totais vêm da resposta JSON do mesmo pedido, não de contas aqui.
 */
@WebMvcTest(CalculoController.class)
@Import({CalculoService.class, ComUsuarioLogado.class})
class CalculoPdfControllerTest {

    private static final DecimalFormat REAIS = new DecimalFormat("#,##0.00",
            DecimalFormatSymbols.getInstance(Locale.forLanguageTag("pt-BR")));

    @Autowired
    private MockMvcTester mvc;

    @Autowired
    private CalculoService service;

    @MockitoBean
    private FonteSeries series;

    private MvcTestResult post(String uri, String json) {
        return mvc.post().uri(uri).contentType(MediaType.APPLICATION_JSON).content(json).exchange();
    }

    private static String texto(byte[] pdf) throws IOException {
        return String.join("\n", paginas(pdf));
    }

    /** O texto de cada página, na ordem. */
    private static List<String> paginas(byte[] pdf) throws IOException {
        PdfReader leitor = new PdfReader(pdf);
        PdfTextExtractor extrator = new PdfTextExtractor(leitor);
        List<String> paginas = new ArrayList<>();
        for (int pagina = 1; pagina <= leitor.getNumberOfPages(); pagina++) {
            paginas.add(extrator.getTextFromPage(pagina));
        }
        return paginas;
    }

    /**
     * O "Y" de "Página X de Y": o rodapé o escreve num template preenchido só ao fechar o
     * documento, que o extrator de texto não lê; aqui ele vem do conteúdo do próprio template.
     */
    private static int totalDePaginasDoRodape(byte[] pdf) throws IOException {
        PdfReader leitor = new PdfReader(pdf);
        PdfDictionary xobjetos = leitor.getPageN(1).getAsDict(PdfName.RESOURCES).getAsDict(PdfName.XOBJECT);
        for (PdfName nome : xobjetos.getKeys()) {
            PRStream template = (PRStream) PdfReader.getPdfObject(xobjetos.get(nome));
            Matcher total = Pattern.compile("\\((\\d+)\\)\\s*Tj")
                    .matcher(new String(PdfReader.getStreamBytes(template), StandardCharsets.ISO_8859_1));
            if (total.find()) {
                return Integer.parseInt(total.group(1));
            }
        }
        throw new AssertionError("o rodapé não tem o total de páginas");
    }

    private static BigDecimal numero(MvcTestResult json, String caminho) {
        String corpo = new String(json.getResponse().getContentAsByteArray(), StandardCharsets.UTF_8);
        return new BigDecimal(JsonPath.<Number>read(corpo, caminho).toString());
    }

    private static String reais(MvcTestResult json, String caminho) {
        return REAIS.format(numero(json, caminho));
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
    void devolveOPdfComoAnexo() {
        indicesDaPensao();

        MvcTestResult resposta = post("/api/calculos/pdf", PENSAO_COM_RITOS);

        assertThat(resposta).hasStatusOk().hasContentType(MediaType.APPLICATION_PDF);
        assertThat(resposta.getResponse().getHeader(HttpHeaders.CONTENT_DISPOSITION))
                .startsWith("attachment;")
                .containsIgnoringWhitespaces("demonstrativo-5001234-56.2024.8.13.0024-2024-05-15.pdf");
        assertThat(new String(resposta.getResponse().getContentAsByteArray(), 0, 5, StandardCharsets.US_ASCII))
                .isEqualTo("%PDF-");
    }

    @Test
    void emissaoSaiNoHorarioDeBrasiliaMesmoComOServidorEmUtc() throws IOException {
        indicesDaPensao();
        // 02:30 UTC de 03/11/2026 ainda é 23:30 de 02/11/2026 em Brasília
        InstantSource relogio = InstantSource.fixed(Instant.parse("2026-11-03T02:30:00Z"));
        MockMvcTester comRelogio = MockMvcTester.of(new CalculoController(service, relogio));

        MvcTestResult resposta = comRelogio.post().uri("/api/calculos/pdf")
                .contentType(MediaType.APPLICATION_JSON).content(PENSAO_COM_RITOS).exchange();
        String pdf = texto(resposta.getResponse().getContentAsByteArray());

        assertThat(pdf)
                .containsIgnoringWhitespaces("Emitido em 02/11/2026 às 23:30")
                .containsIgnoringWhitespaces("emitido em 02/11/2026 às 23:30")
                .containsIgnoringWhitespaces("em 02/11/2026.")
                .doesNotContain("03/11/2026")
                .doesNotContain("Calculado em");
    }

    @Test
    void cabecalhoIdentificaOProcessoEOsCriterios() throws IOException {
        indicesDaPensao();

        String pdf = texto(post("/api/calculos/pdf", PENSAO_COM_RITOS).getResponse().getContentAsByteArray());

        assertThat(pdf)
                .containsIgnoringWhitespaces("Execução de alimentos (arts. 524 e 528 do CPC)")
                .containsIgnoringWhitespaces("5001234-56.2024.8.13.0024")
                .containsIgnoringWhitespaces("2ª Vara de Família de Belo Horizonte")
                .containsIgnoringWhitespaces("A. B. C., representada por D. B.")
                .containsIgnoringWhitespaces("E. F. C.")
                .containsIgnoringWhitespaces("15/05/2024")
                .containsIgnoringWhitespaces("30% do salário mínimo vigente em cada mês, com vencimento no dia 10")
                .containsIgnoringWhitespaces("09/2023 a 04/2024")
                .containsIgnoringWhitespaces("02/2024: R$ 200,00")
                .containsIgnoringWhitespaces("01/03/2024")
                .containsIgnoringWhitespaces("Sentença: INPC + 1% a.m.")
                .containsIgnoringWhitespaces("desde 01/2023: INPC + 1% a.m.");
    }

    @Test
    void memoriaTrazCadaParcelaComTermosFatorEJuros() throws IOException {
        indicesDaPensao();
        MvcTestResult json = post("/api/calculos", PENSAO_COM_RITOS);

        String pdf = texto(post("/api/calculos/pdf", PENSAO_COM_RITOS).getResponse().getContentAsByteArray());

        // a parcela de 09/2023: vencimento, juros desde o vencimento, salário mínimo e devido
        assertThat(pdf).containsIgnoringWhitespaces("09/2023 10/09/2023 10/09/2023 1.320,00 396,00");
        // em seguida o fator em sete casas, o corrigido e os oito meses de juros a 1%, até 04/2024
        String fator = numero(json, "$.linhas[0].fatorCorrecao").setScale(7, RoundingMode.HALF_UP).toPlainString()
                .replace('.', ',');
        assertThat(pdf).containsIgnoringWhitespaces("396,00 — " + fator + " " + reais(json, "$.linhas[0].valorCorrigido")
                + " 8,000000% " + reais(json, "$.linhas[0].valorJuros") + " " + reais(json, "$.linhas[0].subtotal"));
        // o pagamento de 02/2024 aparece na coluna do pago
        assertThat(pdf).containsIgnoringWhitespaces("02/2024 10/02/2024 10/02/2024 1.412,00 423,60 200,00");
        assertThat(pdf).containsIgnoringWhitespaces("Rito da prisão").containsIgnoringWhitespaces("Rito da penhora");
        assertThat(pdf).containsIgnoringWhitespaces("Subtotal, 5 parcelas " + reais(json, "$.ritos.prisao.totalCorrigido"));
        assertThat(pdf).containsIgnoringWhitespaces("Subtotal, 3 parcelas " + reais(json, "$.ritos.penhora.totalCorrigido"));
    }

    @Test
    void totaisTrazemOArt523SobreAPenhora() throws IOException {
        indicesDaPensao();
        MvcTestResult json = post("/api/calculos", PENSAO_COM_RITOS);

        String pdf = texto(post("/api/calculos/pdf", PENSAO_COM_RITOS).getResponse().getContentAsByteArray());

        assertThat(pdf)
                .containsIgnoringWhitespaces("Multa de 10% e honorários de 10% sobre o débito da penhora")
                .containsIgnoringWhitespaces("Multa de 10% (art. 523, §1º, do CPC) R$ " + reais(json, "$.multaArt523"))
                .containsIgnoringWhitespaces("Honorários de 10% (art. 523, §1º, do CPC) R$ " + reais(json, "$.honorariosArt523"))
                .containsIgnoringWhitespaces("Rito da penhora: total com os acréscimos R$ " + reais(json, "$.ritos.totalPenhora"))
                .containsIgnoringWhitespaces("Total da execução R$ " + reais(json, "$.total"));
    }

    @Test
    void indicaAsFontesDosIndicesDaPensao() throws IOException {
        indicesDaPensao();

        String pdf = texto(post("/api/calculos/pdf", PENSAO_COM_RITOS).getResponse().getContentAsByteArray());

        assertThat(pdf)
                .containsIgnoringWhitespaces("INPC (IBGE): variações mensais da série 188 do SGS/BCB.")
                .containsIgnoringWhitespaces("Salário mínimo nacional: valor vigente em cada mês, da série 1619 do SGS/BCB.")
                .doesNotContain("Corregedoria")
                .doesNotContain("29543");
    }

    @Test
    void noRegimePadraoCitaATabelaDaCgjEATaxaLegal() throws IOException {
        SerieIndicesEmMemoria serie = new SerieIndicesEmMemoria()
                .com(Indice.INPC, YearMonth.of(2024, 7), "0.26")
                .com(Indice.IPCA, YearMonth.of(2024, 8), "-0.02")
                .com(Indice.IPCA, YearMonth.of(2024, 9), "0.44")
                .com(Indice.TAXA_LEGAL, YearMonth.of(2024, 8), "0.605306")
                .com(Indice.TAXA_LEGAL, YearMonth.of(2024, 9), "0.5");
        when(series.carregar(any(), any(), any())).thenReturn(serie);
        String avulsa = """
                {"dataBase": "2024-10-15", "aplicarArt523": false,
                 "parcelas": [{"descricao": "Danos materiais", "valor": 1000.00, "inicioCorrecao": "2024-07-10",
                               "inicioJuros": "2024-07-10"}]}
                """;

        MvcTestResult resposta = post("/api/calculos/pdf", avulsa);
        String pdf = texto(resposta.getResponse().getContentAsByteArray());

        assertThat(resposta.getResponse().getHeader(HttpHeaders.CONTENT_DISPOSITION))
                .containsIgnoringWhitespaces("demonstrativo-2024-10-15.pdf");
        assertThat(pdf)
                .containsIgnoringWhitespaces("Cumprimento de sentença (art. 524 do CPC)")
                .doesNotContain("Processo")
                .containsIgnoringWhitespaces("Danos materiais")
                .containsIgnoringWhitespaces("Não aplicados")
                .containsIgnoringWhitespaces("série 188 do SGS/BCB")
                .containsIgnoringWhitespaces("série 433 do SGS/BCB")
                .containsIgnoringWhitespaces("série 29543 do SGS/BCB")
                .containsIgnoringWhitespaces("Até 07/2024, o regime padrão segue a tabela")
                .containsIgnoringWhitespaces("Corregedoria-Geral de Justiça do TJMG")
                .doesNotContain("Salário mínimo")
                .containsIgnoringWhitespaces("08/2024: IPCA + 1% a.m. até 29/08/2024 e taxa legal pro rata nos dias seguintes");
    }

    @Test
    void recusaNumeroDeProcessoLongoDemais() {
        String pedido = """
                {"dataBase": "2024-10-15", "aplicarArt523": false, "processo": {"numero": "%s"},
                 "parcelas": [{"descricao": "Aluguel", "valor": 1000.00, "inicioCorrecao": "2024-10-01"}]}
                """.formatted("1".repeat(51));

        assertThat(post("/api/calculos/pdf", pedido)).hasStatus(400);
    }

    @Test
    void fontesTrazemSoOIndiceDosMesesCorrigidos() throws IOException {
        // juros desde 11/2023, sob o IPCA-E, mas correção só desde 02/2024, já sob o INPC
        when(series.carregar(any(), any(), any())).thenReturn(new SerieIndicesEmMemoria()
                .com(Indice.INPC, YearMonth.of(2024, 2), "0.50")
                .com(Indice.INPC, YearMonth.of(2024, 3), "0.50"));
        String pedido = """
                {"dataBase": "2024-04-15", "aplicarArt523": false,
                 "regime": {"nome": "Sentença", "regras": [
                   {"inicio": "2023-01", "fim": "2023-12", "correcao": "IPCA_E", "juros": "TAXA_FIXA", "percentualJurosMensal": 1},
                   {"inicio": "2024-01", "correcao": "INPC", "juros": "TAXA_FIXA", "percentualJurosMensal": 1}]},
                 "parcelas": [{"descricao": "Aluguel", "valor": 1000.00, "inicioCorrecao": "2024-02-10",
                               "inicioJuros": "2023-11-10"}]}
                """;

        String pdf = texto(post("/api/calculos/pdf", pedido).getResponse().getContentAsByteArray());

        // os critérios mostram as duas regras, porque os juros passam pelas duas
        assertThat(pdf)
                .containsIgnoringWhitespaces("01/2023 a 12/2023: IPCA-E + 1% a.m.")
                .containsIgnoringWhitespaces("série 188 do SGS/BCB")
                .doesNotContain("10764");
    }

    @Test
    void fontesNaoTrazemATaxaLegalSemJuros() throws IOException {
        when(series.carregar(any(), any(), any())).thenReturn(new SerieIndicesEmMemoria()
                .com(Indice.IPCA, YearMonth.of(2024, 10), "0.56")
                .com(Indice.IPCA, YearMonth.of(2024, 11), "0.39"));
        String pedido = """
                {"dataBase": "2024-12-15", "aplicarArt523": false,
                 "regime": {"nome": "Sentença", "regras": [{"inicio": "2024-09", "correcao": "IPCA", "juros": "TAXA_LEGAL"}]},
                 "parcelas": [{"descricao": "Reembolso", "valor": 1000.00, "inicioCorrecao": "2024-10-01"}]}
                """;

        String pdf = texto(post("/api/calculos/pdf", pedido).getResponse().getContentAsByteArray());

        assertThat(pdf).containsIgnoringWhitespaces("série 433 do SGS/BCB").doesNotContain("29543");
    }

    /**
     * Com cada quantidade de parcelas, a quebra cai num ponto diferente da tabela: o fechamento
     * nunca fica sem o cabeçalho das colunas, toda página diz "Página X de Y" e o arquivo fica
     * bem abaixo do limite de upload do PJe do TJMG (1,5 MB).
     */
    @Test
    void paginacaoNaoDeixaOFechamentoSemCabecalho() throws IOException {
        when(series.carregar(any(), any(), any())).thenReturn(new SerieIndicesEmMemoria()
                .com(Indice.INPC, YearMonth.of(2024, 3), "0.16"));
        int fechamentosQueMudaramDePagina = 0;
        for (int n = 20; n <= 80; n++) {
            String parcelas = IntStream.rangeClosed(1, n)
                    .mapToObj(i -> """
                            {"descricao": "Aluguel %d", "valor": 1000.00, "inicioCorrecao": "2024-03-10", "inicioJuros": "2024-03-10"}"""
                            .formatted(i))
                    .collect(Collectors.joining(","));
            String pedido = """
                    {"dataBase": "2024-04-15", "aplicarArt523": true,
                     "regime": {"nome": "Sentença", "regras": [{"inicio": "2024-01", "correcao": "INPC", "juros": "TAXA_FIXA", "percentualJurosMensal": 1}]},
                     "parcelas": [%s]}
                    """.formatted(parcelas);

            byte[] pdf = post("/api/calculos/pdf", pedido).getResponse().getContentAsByteArray();
            List<String> paginas = paginas(pdf);

            assertThat(pdf.length).as("%d parcelas", n).isLessThan(300_000);
            for (int p = 1; p <= paginas.size(); p++) {
                assertThat(paginas.get(p - 1)).as("%d parcelas, página %d", n, p)
                        .containsIgnoringWhitespaces("Página " + p + " de");
            }
            assertThat(totalDePaginasDoRodape(pdf)).as("%d parcelas", n).isEqualTo(paginas.size());
            String comFechamento = paginas.stream().filter(t -> t.contains("Subtotal,")).findFirst().orElseThrow();
            assertThat(comFechamento).as("%d parcelas", n).contains("Correção desde");
            if (!comFechamento.contains("Aluguel")) {
                fechamentosQueMudaramDePagina++;
            }
        }
        // o intervalo cobre o caso em que o fechamento abre a página seguinte sozinho
        assertThat(fechamentosQueMudaramDePagina).isPositive();
    }
}
