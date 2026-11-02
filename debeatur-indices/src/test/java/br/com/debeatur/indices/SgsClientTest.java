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
}
