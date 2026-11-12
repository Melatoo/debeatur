package br.com.debeatur.api.acesso;

import br.com.debeatur.api.PostgresDeTeste;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.context.annotation.Import;
import org.testcontainers.junit.jupiter.Testcontainers;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * O que o MockMvc não mostra: no Tomcat de verdade, um erro passa pelo despacho para
 * {@code /error}, e sem liberá-lo o 403 do CSRF chegava ao navegador como 401.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT,
        properties = "debeatur.series.atualizacao=-")
@Import(PostgresDeTeste.class)
@Testcontainers(disabledWithoutDocker = true)
class SessaoNoServidorTest {

    @LocalServerPort
    private int porta;

    private final HttpClient http = HttpClient.newHttpClient();

    private HttpResponse<String> enviar(HttpRequest.Builder pedido) throws Exception {
        return http.send(pedido.build(), HttpResponse.BodyHandlers.ofString());
    }

    private URI uri(String caminho) {
        return URI.create("http://localhost:" + porta + caminho);
    }

    @Test
    void semOTokenCsrfOServidorResponde403() throws Exception {
        HttpResponse<String> resposta = enviar(HttpRequest.newBuilder(uri("/api/sessao"))
                .header("Content-Type", "application/json")
                .POST(HttpRequest.BodyPublishers.ofString("{\"email\": \"a@b.c\", \"senha\": \"qualquer\"}")));

        assertThat(resposta.statusCode()).isEqualTo(403);
    }

    @Test
    void semLoginOServidorResponde401() throws Exception {
        assertThat(enviar(HttpRequest.newBuilder(uri("/api/calculos/salvos"))).statusCode()).isEqualTo(401);
    }
}
