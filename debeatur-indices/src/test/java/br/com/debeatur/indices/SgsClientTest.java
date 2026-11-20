package br.com.debeatur.indices;

import br.com.debeatur.core.indice.Indice;
import com.sun.net.httpserver.HttpServer;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.io.OutputStream;
import java.math.BigDecimal;
import java.net.InetSocketAddress;
import java.net.URI;
import java.net.http.HttpClient;
import java.nio.charset.StandardCharsets;
import java.time.YearMonth;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class SgsClientTest {

    private static final YearMonth OUT = YearMonth.of(2026, 10);

    private HttpServer servidor;

    @AfterEach
    void pararServidor() {
        if (servidor != null) {
            servidor.stop(0);
        }
    }

    /** SGS simulado que responde sempre com o status e o corpo informados. */
    private SgsClient sgsRespondendo(int status, String corpo) throws IOException {
        servidor = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        servidor.createContext("/", troca -> {
            byte[] bytes = corpo.getBytes(StandardCharsets.UTF_8);
            troca.sendResponseHeaders(status, bytes.length);
            try (OutputStream saida = troca.getResponseBody()) {
                saida.write(bytes);
            }
        });
        servidor.start();
        URI base = URI.create("http://127.0.0.1:" + servidor.getAddress().getPort() + "/dados/serie/");
        return new SgsClient(HttpClient.newHttpClient(), base);
    }

    /** Todo demonstrativo diz a série do SGS de cada índice: um índice sem código derrubaria o cálculo. */
    @Test
    void todoIndiceTemCodigoNoSgs() {
        for (Indice indice : Indice.values()) {
            assertThat(SgsClient.codigo(indice)).as(indice.name()).isPositive();
        }
    }

    @Test
    void interpretaRespostaDoSgs() {
        String corpo = """
                [{"data":"01/09/2024","valor":"0.44"},{"data":"01/10/2024","valor":"0.56"}]
                """;

        Map<YearMonth, BigDecimal> serie = new SgsClient().interpretar(corpo);

        assertThat(serie).containsOnlyKeys(YearMonth.of(2024, 9), YearMonth.of(2024, 10));
        assertThat(serie.get(YearMonth.of(2024, 10))).isEqualByComparingTo("0.56");
    }

    @Test
    void periodoSemValorPublicadoDevolveSerieVazia() throws IOException {
        // corpo real do SGS para um período sem valores
        SgsClient sgs = sgsRespondendo(404, """
                {"erro":{"statusCode":404,"detail":"br.gov.bcb.pec.sgs.comum.excecoes.SGSNegocioException: Value(s) not found"}}""");

        assertThat(sgs.buscar(Indice.IPCA, OUT, OUT)).isEmpty();
    }

    @Test
    void outro404ContinuaSendoFalha() throws IOException {
        SgsClient sgs = sgsRespondendo(404, "<html>Not Found</html>");

        assertThatThrownBy(() -> sgs.buscarSalarioMinimo(OUT, OUT)).isInstanceOf(SgsException.class);
    }

    @Test
    void respostaQueNaoEJsonDoSgsEFalhaDoSgs() throws IOException {
        SgsClient sgs = sgsRespondendo(200, "<html>Sistema em manutenção</html>");

        assertThatThrownBy(() -> sgs.buscarSalarioMinimo(OUT, OUT))
                .isInstanceOf(SgsException.class)
                .hasMessageContaining("Resposta inválida");
    }

    @Test
    void valorMalformadoEFalhaDoSgsENaoDoPedido() throws IOException {
        // sem o tratamento, o NumberFormatException viraria um 400 de "parâmetro inválido"
        SgsClient sgs = sgsRespondendo(200, """
                [{"data":"01/10/2026","valor":"n/d"}]""");

        assertThatThrownBy(() -> sgs.buscarSalarioMinimo(OUT, OUT)).isInstanceOf(SgsException.class);
    }

    /** SGS simulado que responde a SELIC diária e guarda cada consulta (caminho e parâmetros). */
    private SgsClient sgsSelic(String diaria, List<String> consultas) throws IOException {
        servidor = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        servidor.createContext("/", troca -> {
            consultas.add(troca.getRequestURI().getPath() + "?" + troca.getRequestURI().getQuery());
            byte[] bytes = diaria.getBytes(StandardCharsets.UTF_8);
            troca.sendResponseHeaders(200, bytes.length);
            try (OutputStream saida = troca.getResponseBody()) {
                saida.write(bytes);
            }
        });
        servidor.start();
        URI base = URI.create("http://127.0.0.1:" + servidor.getAddress().getPort() + "/dados/serie/");
        return new SgsClient(HttpClient.newHttpClient(), base);
    }

    @Test
    void selicDoMesEOProdutoDaSelicDiaria() {
        Map<YearMonth, BigDecimal> taxas = new SgsClient().acumularPorMes("""
                [{"data":"02/01/2024","valor":"0.5"},{"data":"03/01/2024","valor":"0.5"},
                 {"data":"01/02/2024","valor":"1"}]""");

        // 1,005 × 1,005 = 1,010025: 1,0025%, e não a soma de 1%
        assertThat(taxas.get(YearMonth.of(2024, 1))).isEqualByComparingTo("1.0025");
        assertThat(taxas.get(YearMonth.of(2024, 2))).isEqualByComparingTo("1");
    }

    @Test
    void mesAindaAbertoFicaDeFora() throws IOException {
        // em 19/11/2026 a diária vai até o dia 19, e a 4390 já mostra novembro com 0,66: um mês
        // só entra quando a diária tem um dia do mês seguinte
        List<String> consultas = new ArrayList<>();
        SgsClient sgs = sgsSelic("""
                [{"data":"30/10/2026","valor":"0.05"},{"data":"03/11/2026","valor":"0.05"},
                 {"data":"19/11/2026","valor":"0.05"}]""", consultas);

        Map<YearMonth, BigDecimal> selic = sgs.buscar(Indice.SELIC, YearMonth.of(2026, 10), YearMonth.of(2026, 11));

        assertThat(selic).containsOnlyKeys(OUT);
        assertThat(selic.get(OUT)).isEqualByComparingTo("0.05");
        assertThat(consultas).allMatch(c -> c.contains("bcdata.sgs.11/"));
    }

    @Test
    void ultimoMesPedidoEntraQuandoOSeguinteJaComecou() throws IOException {
        // a consulta vai até o mês seguinte ao pedido para saber se ele fechou
        List<String> consultas = new ArrayList<>();
        SgsClient sgs = sgsSelic("""
                [{"data":"30/09/2026","valor":"0.04"},{"data":"01/10/2026","valor":"0.05"}]""", consultas);

        Map<YearMonth, BigDecimal> selic = sgs.buscar(Indice.SELIC, YearMonth.of(2026, 9), YearMonth.of(2026, 9));

        assertThat(selic).containsOnlyKeys(YearMonth.of(2026, 9));
        assertThat(consultas).singleElement().asString().endsWith("dataInicial=01/09/2026&dataFinal=31/10/2026");
    }

    @Test
    void selicDiariaEConsultadaEmJanelasDeCincoAnos() throws IOException {
        // o SGS recusa consulta de mais de 10 anos a uma série diária
        List<String> consultas = new ArrayList<>();
        SgsClient sgs = sgsSelic("""
                [{"data":"02/01/2014","valor":"0.03"},{"data":"01/12/2025","valor":"0.05"}]""", consultas);

        sgs.buscar(Indice.SELIC, YearMonth.of(2014, 1), YearMonth.of(2025, 11));

        assertThat(consultas).extracting(c -> c.substring(c.indexOf("dataInicial"))).containsExactly(
                "dataInicial=01/01/2014&dataFinal=31/12/2018",
                "dataInicial=01/01/2019&dataFinal=31/12/2023",
                "dataInicial=01/01/2024&dataFinal=31/12/2025");
    }

    @Test
    void consultaQueComecaNoMesAbertoDevolveSerieVazia() throws IOException {
        // a reconsulta do cache parte do primeiro mês sem valor, que é o mês corrente
        List<String> consultas = new ArrayList<>();
        SgsClient sgs = sgsSelic("""
                [{"data":"03/11/2026","valor":"0.05"},{"data":"19/11/2026","valor":"0.05"}]""", consultas);

        assertThat(sgs.buscar(Indice.SELIC, YearMonth.of(2026, 11), YearMonth.of(2026, 12))).isEmpty();
    }

    @Test
    void periodoSemSelicDiariaDevolveSerieVazia() throws IOException {
        SgsClient sgs = sgsRespondendo(404, """
                {"erro":{"statusCode":404,"detail":"br.gov.bcb.pec.sgs.comum.excecoes.SGSNegocioException: Value(s) not found"}}""");

        assertThat(sgs.buscar(Indice.SELIC, OUT, OUT)).isEmpty();
    }

    @Test
    void demonstrativoCitaASerieDeOndeVieramOsValores() {
        List<BigDecimal> daDiaria = List.of(new BigDecimal("0.966690174365814"), new BigDecimal("0.8"));
        List<BigDecimal> daMensal = List.of(new BigDecimal("0.97"), new BigDecimal("0.80"));

        assertThat(SgsClient.fonte(Indice.SELIC, daDiaria)).contains("série 11").contains("dias úteis");
        // cálculo salvo antes da SGS 11: o snapshot tem a 4390, com duas casas
        assertThat(SgsClient.fonte(Indice.SELIC, daMensal)).isEqualTo("série 4390 do SGS/BCB");
        assertThat(SgsClient.codigo(Indice.SELIC, daMensal)).isEqualTo(4390);
        assertThat(SgsClient.fonte(Indice.IPCA, daMensal)).isEqualTo("série 433 do SGS/BCB");
    }
}
