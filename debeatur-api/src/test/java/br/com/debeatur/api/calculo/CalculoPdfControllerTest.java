package br.com.debeatur.api.calculo;

import br.com.debeatur.core.indice.Indice;
import br.com.debeatur.core.indice.SalarioMinimoEmMemoria;
import br.com.debeatur.core.indice.SerieIndicesEmMemoria;
import br.com.debeatur.indices.SgsClient;
import com.jayway.jsonpath.JsonPath;
import org.junit.jupiter.api.Test;
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
import java.time.YearMonth;
import java.util.Locale;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

/**
 * Demonstrativo em PDF. Os índices são fictícios: o que se testa é o PDF trazer o que o
 * cálculo devolveu, e os totais vêm da resposta JSON do mesmo pedido, não de contas aqui.
 */
@WebMvcTest(CalculoController.class)
@Import(CalculoService.class)
class CalculoPdfControllerTest {

    private static final DecimalFormat REAIS = new DecimalFormat("#,##0.00",
            DecimalFormatSymbols.getInstance(Locale.forLanguageTag("pt-BR")));

    @Autowired
    private MockMvcTester mvc;

    @MockitoBean
    private SgsClient sgs;

    private MvcTestResult post(String uri, String json) {
        return mvc.post().uri(uri).contentType(MediaType.APPLICATION_JSON).content(json).exchange();
    }

    private static String texto(byte[] pdf) throws IOException {
        PdfReader leitor = new PdfReader(pdf);
        PdfTextExtractor extrator = new PdfTextExtractor(leitor);
        StringBuilder texto = new StringBuilder();
        for (int pagina = 1; pagina <= leitor.getNumberOfPages(); pagina++) {
            texto.append(extrator.getTextFromPage(pagina)).append('\n');
        }
        return texto.toString();
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
        when(sgs.carregar(any(), any(), any())).thenReturn(serie);
        when(sgs.carregarSalarioMinimo(any(), any())).thenReturn(salario);
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
        when(sgs.carregar(any(), any(), any())).thenReturn(serie);
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
}
