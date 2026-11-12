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

    private HttpResponse<String> entrar(String token, String xForwardedFor, String email) throws Exception {
        return enviar(HttpRequest.newBuilder(uri("/api/sessao"))
                .header("Content-Type", "application/json")
                .header("Cookie", "XSRF-TOKEN=" + token)
                .header("X-XSRF-TOKEN", token)
                .header("X-Forwarded-For", xForwardedFor)
                .POST(HttpRequest.BodyPublishers.ofString(
                        "{\"email\": \"%s\", \"senha\": \"senha errada\"}".formatted(email))));
    }

    /**
     * O IP das tentativas é o que o nginx acrescenta à direita do {@code X-Forwarded-For}
     * ({@code $proxy_add_x_forwarded_for}); o que o cliente pôs à esquerda não conta.
     */
    @Test
    void contaAsTentativasPeloIpQueONginxAcrescenta() throws Exception {
        String token = enviar(HttpRequest.newBuilder(uri("/api/sessao"))).headers().allValues("Set-Cookie").stream()
                .filter(cookie -> cookie.startsWith("XSRF-TOKEN="))
                .map(cookie -> cookie.substring("XSRF-TOKEN=".length(), cookie.indexOf(';')))
                .findFirst().orElseThrow();
        for (int i = 1; i <= TentativasDeLogin.FALHAS; i++) {
            assertThat(entrar(token, "10.9.9." + i + ", 203.0.113.9", "conta" + i + "@escritorio.adv.br").statusCode())
                    .isEqualTo(401);
        }

        HttpResponse<String> bloqueado = entrar(token, "10.9.9.99, 203.0.113.9", "outra@escritorio.adv.br");
        assertThat(bloqueado.statusCode()).isEqualTo(429);
        assertThat(bloqueado.headers().firstValue("Retry-After")).isPresent();
        assertThat(entrar(token, "203.0.113.10", "outra@escritorio.adv.br").statusCode()).isEqualTo(401);
    }
}
