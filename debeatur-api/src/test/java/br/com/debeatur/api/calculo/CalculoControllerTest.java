package br.com.debeatur.api.calculo;

import br.com.debeatur.core.indice.Indice;
import br.com.debeatur.core.indice.SerieIndicesEmMemoria;
import br.com.debeatur.api.serie.FonteSeries;
import com.jayway.jsonpath.JsonPath;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.assertj.MockMvcTester;
import org.springframework.test.web.servlet.assertj.MvcTestResult;

import java.nio.charset.StandardCharsets;
import java.time.YearMonth;
import java.util.EnumSet;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Ligação entre a API e o motor. Os índices são fictícios: o que se testa é o regime
 * chegar ao motor, não os valores do motor, que têm gabarito próprio no core.
 */
@WebMvcTest({CalculoController.class, RegimeController.class})
@Import(CalculoService.class)
class CalculoControllerTest {

    @Autowired
    private MockMvcTester mvc;

    @MockitoBean
    private FonteSeries series;

    private MockMvcTester.MockMvcRequestBuilder calcular(String json) {
        return mvc.post().uri("/api/calculos").contentType(MediaType.APPLICATION_JSON).content(json);
    }

    private static String requisicao(String regime) {
        return """
                {
                  "dataBase": "2024-04-15",
                  "aplicarArt523": false,
                  "parcelas": [
                    {"descricao": "Aluguel", "valor": 1000.00, "inicioCorrecao": "2024-01-10", "inicioJuros": "2024-02-01"}
                  ]%s
                }
                """.formatted(regime == null ? "" : ",\n  \"regime\": " + regime);
    }

    @Test
    void calculaComORegimeInformado() {
        when(series.carregar(EnumSet.of(Indice.IPCA_E), YearMonth.of(2024, 1), YearMonth.of(2024, 3)))
                .thenReturn(new SerieIndicesEmMemoria()
                        .com(Indice.IPCA_E, YearMonth.of(2024, 1), "1.00")
                        .com(Indice.IPCA_E, YearMonth.of(2024, 2), "0.50")
                        .com(Indice.IPCA_E, YearMonth.of(2024, 3), "0.20"));

        String regime = """
                {"nome": "Sentença: IPCA-E + 0,5% a.m.",
                 "regras": [{"inicio": "2023-01", "fim": null, "correcao": "IPCA_E", "juros": "TAXA_FIXA", "percentualJurosMensal": 0.5}]}
                """;

        // fator = 1,01 × 1,005 × 1,002 = 1,0170801; juros = 2 meses × 0,5% = 1% de R$ 1.017,08
        assertThat(calcular(requisicao(regime))).hasStatusOk().bodyJson()
                .hasPathSatisfying("$.regime.nome", v -> v.assertThat().isEqualTo("Sentença: IPCA-E + 0,5% a.m."))
                .hasPathSatisfying("$.regime.regras[0].correcao", v -> v.assertThat().isEqualTo("IPCA_E"))
                .hasPathSatisfying("$.regime.regras[0].descricaoJuros", v -> v.assertThat().isEqualTo("0.5% a.m."))
                .hasPathSatisfying("$.linhas[0].valorCorrigido", v -> v.assertThat().asNumber().isEqualTo(1017.08))
                .hasPathSatisfying("$.linhas[0].percentualJuros", v -> v.assertThat().asNumber().isEqualTo(1.0))
                .hasPathSatisfying("$.linhas[0].valorJuros", v -> v.assertThat().asNumber().isEqualTo(10.17))
                .hasPathSatisfying("$.total", v -> v.assertThat().asNumber().isEqualTo(1027.25))
                // a série que o motor leu, para conferir o fator mês a mês
                .hasPathSatisfying("$.series[0].serie", v -> v.assertThat().isEqualTo("IPCA_E"))
                .hasPathSatisfying("$.series[0].descricao", v -> v.assertThat().isEqualTo("IPCA-E (IBGE)"))
                .hasPathSatisfying("$.series[0].fonte", v -> v.assertThat().isEqualTo("SGS/BCB, série 10764"))
                .hasPathSatisfying("$.series[0].valores[0].mes", v -> v.assertThat().isEqualTo("2024-01"))
                .hasPathSatisfying("$.series[0].valores[2].valor", v -> v.assertThat().asNumber().isEqualTo(0.20))
                .hasPathSatisfying("$.series.length()", v -> v.assertThat().asNumber().isEqualTo(1));
    }

    @Test
    void regimeSemNomeRecebeONomePadrao() {
        when(series.carregar(any(), any(), any())).thenReturn(new SerieIndicesEmMemoria()
                .com(Indice.INPC, YearMonth.of(2024, 1), "0")
                .com(Indice.INPC, YearMonth.of(2024, 2), "0")
                .com(Indice.INPC, YearMonth.of(2024, 3), "0"));

        String regime = """
                {"regras": [{"inicio": "2024-01", "correcao": "INPC", "juros": "SEM_JUROS"}]}
                """;

        assertThat(calcular(requisicao(regime))).hasStatusOk().bodyJson()
                .hasPathSatisfying("$.regime.nome", v -> v.assertThat().isEqualTo(RegimeDto.NOME_PADRAO))
                .hasPathSatisfying("$.total", v -> v.assertThat().asNumber().isEqualTo(1000.0));
    }

    @Test
    void semRegimeUsaOPadraoDoTjmg() {
        String mesmoMes = """
                {"dataBase": "2024-04-15", "aplicarArt523": false,
                 "parcelas": [{"descricao": "Aluguel", "valor": 1000.00, "inicioCorrecao": "2024-04-01"}]}
                """;

        assertThat(calcular(mesmoMes)).hasStatusOk().bodyJson()
                .hasPathSatisfying("$.regime.nome", v -> v.assertThat().asString().startsWith("Cível padrão TJMG"))
                .hasPathSatisfying("$.regime.regras", v -> v.assertThat().asArray().hasSize(3));
        verify(series, never()).carregar(any(), any(), any());
    }

    @Test
    void recusaRegrasSobrepostas() {
        String regime = """
                {"regras": [
                  {"inicio": "2023-01", "fim": "2024-06", "correcao": "INPC", "juros": "TAXA_FIXA", "percentualJurosMensal": 1},
                  {"inicio": "2024-03", "correcao": "IPCA", "juros": "TAXA_LEGAL"}]}
                """;

        assertThat(calcular(requisicao(regime))).hasStatus(400).bodyJson()
                .hasPathSatisfying("$.detail", v -> v.assertThat().asString().contains("2024-07"));
    }

    @Test
    void recusaRegimeQueNaoCobreOPeriodo() {
        String regime = """
                {"regras": [{"inicio": "2024-02", "correcao": "INPC", "juros": "SEM_JUROS"}]}
                """;

        assertThat(calcular(requisicao(regime))).hasStatus(400).bodyJson()
                .hasPathSatisfying("$.detail", v -> v.assertThat().asString().contains("não cobre"));
    }

    @Test
    void recusaTaxaFixaSemPercentual() {
        String regime = """
                {"regras": [{"inicio": "2023-01", "correcao": "INPC", "juros": "TAXA_FIXA"}]}
                """;

        assertThat(calcular(requisicao(regime))).hasStatus(400).bodyJson()
                .hasPathSatisfying("$.detail", v -> v.assertThat().asString().contains("percentual mensal"));
    }

    @Test
    void recusaTaxaLegalComoCorrecao() {
        String regime = """
                {"regras": [{"inicio": "2023-01", "correcao": "TAXA_LEGAL", "juros": "SEM_JUROS"}]}
                """;

        assertThat(calcular(requisicao(regime))).hasStatus(400);
    }

    @Test
    void recusaRegimeSemRegras() {
        assertThat(calcular(requisicao("{\"regras\": []}"))).hasStatus(400);
    }

    @Test
    void devolveORegimePadraoParaEdicao() {
        assertThat(mvc.get().uri("/api/regimes/padrao")).hasStatusOk().bodyJson()
                .hasPathSatisfying("$.regras", v -> v.assertThat().asArray().hasSize(3))
                .hasPathSatisfying("$.regras[0].inicio", v -> v.assertThat().isEqualTo("1994-07"))
                .hasPathSatisfying("$.regras[0].fim", v -> v.assertThat().isEqualTo("2024-07"))
                .hasPathSatisfying("$.regras[0].correcao", v -> v.assertThat().isEqualTo("INPC"))
                .hasPathSatisfying("$.regras[0].juros", v -> v.assertThat().isEqualTo("TAXA_FIXA"))
                .hasPathSatisfying("$.regras[0].percentualJurosMensal", v -> v.assertThat().asNumber().isEqualTo(1))
                .hasPathSatisfying("$.regras[1].inicio", v -> v.assertThat().isEqualTo("2024-08"))
                .hasPathSatisfying("$.regras[1].fim", v -> v.assertThat().isEqualTo("2024-08"))
                .hasPathSatisfying("$.regras[1].juros", v -> v.assertThat().isEqualTo("TRANSICAO_TAXA_LEGAL"))
                .hasPathSatisfying("$.regras[1].percentualJurosMensal", v -> v.assertThat().asNumber().isEqualTo(1))
                .hasPathSatisfying("$.regras[1].ultimoDiaTaxaFixa", v -> v.assertThat().isEqualTo("2024-08-29"))
                .hasPathSatisfying("$.regras[2].fim", v -> v.assertThat().isNull())
                .hasPathSatisfying("$.regras[2].juros", v -> v.assertThat().isEqualTo("TAXA_LEGAL"));
    }

    @Test
    void regimePadraoReenviadoSemAlteracaoDaOMesmoCalculo() throws Exception {
        when(series.carregar(any(), any(), any())).thenReturn(new SerieIndicesEmMemoria()
                .com(Indice.INPC, YearMonth.of(2024, 7), "0.26")
                .com(Indice.IPCA, YearMonth.of(2024, 8), "-0.02")
                .com(Indice.IPCA, YearMonth.of(2024, 9), "0.44")
                .com(Indice.TAXA_LEGAL, YearMonth.of(2024, 8), "0.605306")
                .com(Indice.TAXA_LEGAL, YearMonth.of(2024, 9), "0.676227"));
        String padrao = mvc.get().uri("/api/regimes/padrao").exchange()
                .getResponse().getContentAsString(StandardCharsets.UTF_8);
        String semRegime = """
                {"dataBase": "2024-10-15", "aplicarArt523": false,
                 "parcelas": [{"descricao": "Pensão de julho", "valor": 1000.00, "inicioCorrecao": "2024-07-10"}]%s}
                """;

        String calculoPadrao = corpo(calcular(semRegime.formatted("")));
        String calculoEditado = corpo(calcular(semRegime.formatted(",\n \"regime\": " + padrao)));

        // agosto/2024 com a transição (29/30 de 1% + 2/31 da taxa legal), e não 1% nem a taxa legal inteira
        assertThat(JsonPath.<Object>read(calculoEditado, "$.linhas"))
                .isEqualTo(JsonPath.read(calculoPadrao, "$.linhas"));
        assertThat(JsonPath.<Object>read(calculoEditado, "$.total"))
                .isEqualTo(JsonPath.read(calculoPadrao, "$.total"));
    }

    @Test
    void recusaTransicaoSemUltimoDiaDaTaxaFixa() {
        String regime = """
                {"regras": [
                  {"inicio": "2024-01", "fim": "2024-02", "correcao": "INPC", "juros": "TAXA_FIXA", "percentualJurosMensal": 1},
                  {"inicio": "2024-03", "fim": "2024-03", "correcao": "IPCA", "juros": "TRANSICAO_TAXA_LEGAL", "percentualJurosMensal": 1},
                  {"inicio": "2024-04", "correcao": "IPCA", "juros": "TAXA_LEGAL"}]}
                """;

        assertThat(calcular(requisicao(regime))).hasStatus(400).bodyJson()
                .hasPathSatisfying("$.detail", v -> v.assertThat().asString().contains("último dia da taxa fixa"));
    }

    @Test
    void recusaTransicaoEmRegraDeMaisDeUmMes() {
        String regime = """
                {"regras": [
                  {"inicio": "2024-01", "fim": "2024-07", "correcao": "INPC", "juros": "TAXA_FIXA", "percentualJurosMensal": 1},
                  {"inicio": "2024-08", "fim": "2024-09", "correcao": "IPCA", "juros": "TRANSICAO_TAXA_LEGAL",
                   "percentualJurosMensal": 1, "ultimoDiaTaxaFixa": "2024-08-29"},
                  {"inicio": "2024-10", "correcao": "IPCA", "juros": "TAXA_LEGAL"}]}
                """;

        assertThat(calcular(requisicao(regime))).hasStatus(400).bodyJson()
                .hasPathSatisfying("$.detail", v -> v.assertThat().asString().contains("só para o mês do corte"));
    }

    @Test
    void recusaTransicaoAntesDaVigenciaDaLei14905() {
        String regime = """
                {"regras": [
                  {"inicio": "2024-01", "fim": "2024-01", "correcao": "IPCA", "juros": "TRANSICAO_TAXA_LEGAL",
                   "percentualJurosMensal": 1, "ultimoDiaTaxaFixa": "2024-01-15"},
                  {"inicio": "2024-02", "correcao": "IPCA", "juros": "TAXA_LEGAL"}]}
                """;

        assertThat(calcular(requisicao(regime))).hasStatus(400).bodyJson()
                .hasPathSatisfying("$.detail", v -> v.assertThat().asString().contains("30/08/2024"));
    }

    @Test
    void indiceDoMesQueNaoSaiuDevolve422ComASerieEOMes() {
        // Janeiro e fevereiro saíram; março, o último mês antes da data-base, ainda não.
        when(series.carregar(any(), any(), any())).thenReturn(new SerieIndicesEmMemoria()
                .com(Indice.IPCA_E, YearMonth.of(2024, 1), "1.00")
                .com(Indice.IPCA_E, YearMonth.of(2024, 2), "0.50"));
        String regime = """
                {"regras": [{"inicio": "2023-01", "correcao": "IPCA_E", "juros": "TAXA_FIXA", "percentualJurosMensal": 0.5}]}
                """;

        assertThat(calcular(requisicao(regime))).hasStatus(422).bodyJson()
                .hasPathSatisfying("$.type", v -> v.assertThat().isEqualTo("/problemas/serie-indisponivel"))
                .hasPathSatisfying("$.serie", v -> v.assertThat().isEqualTo("IPCA_E"))
                .hasPathSatisfying("$.descricao", v -> v.assertThat().isEqualTo("IPCA-E (IBGE)"))
                .hasPathSatisfying("$.mes", v -> v.assertThat().isEqualTo("2024-03"))
                .hasPathSatisfying("$.detail", v -> v.assertThat().isEqualTo("IPCA-E (IBGE) indisponível para 03/2024."));
    }

    private static String corpo(MockMvcTester.MockMvcRequestBuilder requisicao) throws Exception {
        MvcTestResult resultado = requisicao.exchange();
        assertThat(resultado).hasStatusOk();
        return resultado.getResponse().getContentAsString(StandardCharsets.UTF_8);
    }
}
