package br.com.debeatur.api.acesso;

import br.com.debeatur.api.PostgresDeTeste;
import jakarta.servlet.http.Cookie;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.mock.web.MockHttpSession;
import org.springframework.security.core.context.SecurityContext;
import org.springframework.security.web.context.HttpSessionSecurityContextRepository;
import org.springframework.test.web.servlet.assertj.MockMvcTester;
import org.springframework.test.web.servlet.assertj.MvcTestResult;
import org.springframework.test.web.servlet.request.RequestPostProcessor;
import org.testcontainers.junit.jupiter.Testcontainers;

import java.util.Arrays;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Login, sessão e CSRF de ponta a ponta, com o usuário inicial criado na subida a partir das
 * propriedades, como em produção. As requisições fazem o que o navegador faz: o token CSRF vem
 * do cookie e volta no cabeçalho, sem o atalho {@code csrf()} do spring-security-test.
 */
@SpringBootTest(properties = {
        "debeatur.series.atualizacao=-",
        "debeatur.usuario-inicial.email=Ana@Escritorio.adv.br",
        "debeatur.usuario-inicial.senha=" + SessaoTest.SENHA})
@AutoConfigureMockMvc
@Import(PostgresDeTeste.class)
@Testcontainers(disabledWithoutDocker = true)
class SessaoTest {

    static final String SENHA = "uma senha longa o bastante";

    private static final String EMAIL = "ana@escritorio.adv.br";

    @Autowired
    private MockMvcTester mvc;

    @Autowired
    private JdbcClient jdbc;

    private static String credenciais(String email, String senha) {
        return "{\"email\": \"%s\", \"senha\": \"%s\"}".formatted(email, senha);
    }

    /** O cookie como o navegador fica com ele: vale o último Set-Cookie de mesmo nome. */
    private static Cookie cookieXsrf(MockHttpServletResponse resposta) {
        return Arrays.stream(resposta.getCookies()).filter(c -> c.getName().equals("XSRF-TOKEN"))
                .reduce((primeiro, ultimo) -> ultimo).orElse(null);
    }

    /** O token que o frontend recebe ao abrir: o cookie do {@code GET /api/sessao}. */
    private Cookie token() {
        return cookieXsrf(mvc.get().uri("/api/sessao").exchange().getResponse());
    }

    /** Cookie e cabeçalho com o mesmo token, como o frontend manda. */
    private static RequestPostProcessor comToken(Cookie token) {
        return pedido -> {
            pedido.setCookies(token);
            pedido.addHeader("X-XSRF-TOKEN", token.getValue());
            return pedido;
        };
    }

    /** O cookie JSESSIONID de uma sessão que já existe no servidor. */
    private static RequestPostProcessor naSessao(MockHttpSession sessao) {
        return pedido -> {
            pedido.setSession(sessao);
            pedido.setRequestedSessionId(sessao.getId());
            pedido.setRequestedSessionIdValid(true);
            return pedido;
        };
    }

    private MvcTestResult entrar(String email, String senha) {
        return mvc.post().uri("/api/sessao").with(comToken(token())).contentType(MediaType.APPLICATION_JSON)
                .content(credenciais(email, senha)).exchange();
    }

    private static MockHttpSession sessao(MvcTestResult resultado) {
        return (MockHttpSession) resultado.getRequest().getSession(false);
    }

    @Test
    void criaOUsuarioInicialNoEscritorioPadraoComASenhaEmHash() {
        record Usuario(UUID escritorio, String senha) {
        }
        Usuario usuario = jdbc.sql("SELECT escritorio_id, senha FROM usuario WHERE email = 'Ana@Escritorio.adv.br'")
                .query((rs, linha) -> new Usuario(rs.getObject("escritorio_id", UUID.class), rs.getString("senha")))
                .single();

        assertThat(usuario.escritorio()).isEqualTo(UsuarioInicial.ESCRITORIO_PADRAO);
        assertThat(usuario.senha()).startsWith("{bcrypt}").doesNotContain(SENHA);
    }

    @Test
    void semLoginAApiResponde401() {
        assertThat(mvc.get().uri("/api/calculos/salvos")).hasStatus(401);
        assertThat(mvc.get().uri("/api/regimes/padrao")).hasStatus(401);
        assertThat(mvc.post().uri("/api/calculos").with(comToken(token())).contentType(MediaType.APPLICATION_JSON)
                .content("{}")).hasStatus(401);
        assertThat(mvc.get().uri("/api/sessao")).hasStatus(401);
    }

    @Test
    void oHealthDoActuatorFicaAbertoEORestoNao() {
        assertThat(mvc.get().uri("/actuator/health")).hasStatusOk();
        assertThat(mvc.get().uri("/actuator")).hasStatus(401);
        assertThat(mvc.get().uri("/actuator/env")).hasStatus(401);
    }

    @Test
    void logadoSemOTokenCsrfNaoAlteraNada() {
        MockHttpSession sessao = sessao(entrar(EMAIL, SENHA));

        assertThat(mvc.post().uri("/api/calculos/salvos").with(naSessao(sessao)).contentType(MediaType.APPLICATION_JSON)
                .content("{\"calculo\": {}}")).hasStatus(403);
    }

    @Test
    void entraComEmailSemDiferenciarMaiusculasEUsaASessao() {
        MvcTestResult login = entrar("  ana@escritorio.ADV.br ", SENHA);

        assertThat(login).hasStatusOk().bodyJson()
                .hasPathSatisfying("$.email", v -> v.assertThat().isEqualTo("Ana@Escritorio.adv.br"));
        MockHttpSession sessao = sessao(login);
        assertThat(mvc.get().uri("/api/sessao").with(naSessao(sessao))).hasStatusOk().bodyJson()
                .hasPathSatisfying("$.email", v -> v.assertThat().isEqualTo("Ana@Escritorio.adv.br"));
        assertThat(mvc.get().uri("/api/calculos/salvos").with(naSessao(sessao))).hasStatusOk();
    }

    @Test
    void senhaErradaEEmailInexistenteDaoAMesmaResposta() {
        MvcTestResult senhaErrada = entrar(EMAIL, "outra senha qualquer");
        MvcTestResult semConta = entrar("ninguem@escritorio.adv.br", SENHA);

        assertThat(senhaErrada).hasStatus(401).bodyJson()
                .hasPathSatisfying("$.detail", v -> v.assertThat().isEqualTo("E-mail ou senha incorretos. Confira e tente de novo."));
        assertThat(semConta).hasStatus(401);
        assertThat(semConta.getResponse().getContentAsByteArray())
                .isEqualTo(senhaErrada.getResponse().getContentAsByteArray());
    }

    @Test
    void credenciaisEmBrancoDao400() {
        assertThat(entrar("", "")).hasStatus(400);
    }

    @Test
    void entrarTrocaOIdDaSessao() {
        MockHttpSession anterior = new MockHttpSession();
        // o MockHttpSession troca o id no próprio objeto
        String idAnterior = anterior.getId();

        MvcTestResult login = mvc.post().uri("/api/sessao").with(naSessao(anterior)).with(comToken(token()))
                .contentType(MediaType.APPLICATION_JSON).content(credenciais(EMAIL, SENHA)).exchange();

        assertThat(login).hasStatusOk();
        assertThat(sessao(login).getId()).isNotEqualTo(idAnterior);
    }

    @Test
    void semOTokenCsrfNaoEntra() {
        Cookie token = token();
        assertThat(token).isNotNull();
        assertThat(token.isHttpOnly()).isFalse();

        assertThat(mvc.post().uri("/api/sessao").cookie(token).contentType(MediaType.APPLICATION_JSON)
                .content(credenciais(EMAIL, SENHA))).as("sem o cabeçalho").hasStatus(403);
        assertThat(mvc.post().uri("/api/sessao").cookie(token).header("X-XSRF-TOKEN", "outro")
                .contentType(MediaType.APPLICATION_JSON).content(credenciais(EMAIL, SENHA)))
                .as("com outro valor no cabeçalho").hasStatus(403);
    }

    /** Entrar renova o token, e o novo já vem na resposta do login para o próximo POST. */
    @Test
    void entrarRenovaOTokenCsrf() {
        Cookie anterior = token();
        MvcTestResult login = mvc.post().uri("/api/sessao").with(comToken(anterior))
                .contentType(MediaType.APPLICATION_JSON).content(credenciais(EMAIL, SENHA)).exchange();
        assertThat(login).hasStatusOk();

        Cookie renovado = cookieXsrf(login.getResponse());
        assertThat(renovado).isNotNull();
        assertThat(renovado.getValue()).isNotBlank().isNotEqualTo(anterior.getValue());
        // no double-submit o servidor não guarda o token: vale o par cookie e cabeçalho
        MockHttpSession sessao = sessao(login);
        assertThat(mvc.delete().uri("/api/sessao").with(naSessao(sessao)).cookie(renovado)
                .header("X-XSRF-TOKEN", anterior.getValue())).as("cabeçalho com o token anterior").hasStatus(403);
        assertThat(mvc.delete().uri("/api/sessao").with(naSessao(sessao)).with(comToken(renovado))).hasStatus(204);
    }

    @Test
    void sairEncerraASessao() {
        MvcTestResult login = entrar(EMAIL, SENHA);
        MockHttpSession sessao = sessao(login);

        assertThat(mvc.delete().uri("/api/sessao").with(naSessao(sessao)).with(comToken(cookieXsrf(login.getResponse()))))
                .hasStatus(204);
        assertThat(sessao.isInvalid()).isTrue();
    }

    @Test
    void naoGuardaOHashDaSenhaNaSessao() {
        MockHttpSession sessao = sessao(entrar(EMAIL, SENHA));

        SecurityContext contexto = (SecurityContext) sessao.getAttribute(
                HttpSessionSecurityContextRepository.SPRING_SECURITY_CONTEXT_KEY);
        UsuarioAutenticado usuario = (UsuarioAutenticado) contexto.getAuthentication().getPrincipal();
        assertThat(usuario.getPassword()).isNull();
        assertThat(contexto.getAuthentication().getCredentials()).isNull();
    }
}
