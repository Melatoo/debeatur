package br.com.debeatur.api.acesso;

import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.authentication.InternalAuthenticationServiceException;
import org.springframework.security.web.authentication.session.NullAuthenticatedSessionStrategy;
import org.springframework.security.web.context.HttpSessionSecurityContextRepository;
import org.springframework.test.web.servlet.assertj.MockMvcTester;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/** Como o login responde às falhas do provedor, sem banco nem filtros. */
class SessaoControllerTest {

    private final AuthenticationManager autenticacao = mock(AuthenticationManager.class);
    private final MockMvcTester mvc = MockMvcTester.of(new SessaoController(autenticacao,
            new NullAuthenticatedSessionStrategy(), new HttpSessionSecurityContextRepository(), new TentativasDeLogin()));

    private MockMvcTester.MockMvcRequestBuilder entrar() {
        return mvc.post().uri("/api/sessao").contentType(MediaType.APPLICATION_JSON)
                .content("{\"email\": \"ana@escritorio.adv.br\", \"senha\": \"qualquer coisa\"}");
    }

    @Test
    void credencialErradaDa401() {
        when(autenticacao.authenticate(any())).thenThrow(new BadCredentialsException("senha"));

        assertThat(entrar()).hasStatus(401).bodyJson()
                .hasPathSatisfying("$.detail",
                        v -> v.assertThat().isEqualTo("E-mail ou senha incorretos. Confira e tente de novo."));
    }

    /** O banco fora do ar não pode passar por senha errada. */
    @Test
    void falhaAoConsultarOUsuarioDa503() {
        when(autenticacao.authenticate(any()))
                .thenThrow(new InternalAuthenticationServiceException("conexão recusada"));

        assertThat(entrar()).hasStatus(503).bodyJson()
                .hasPathSatisfying("$.detail", v -> v.assertThat().asString().startsWith("Não foi possível entrar agora"));
    }

    /** Com o banco fora do ar ninguém errou a senha: não conta para o bloqueio. */
    @Test
    void falhaAoConsultarOUsuarioNaoContaComoTentativa() {
        when(autenticacao.authenticate(any()))
                .thenThrow(new InternalAuthenticationServiceException("conexão recusada"));

        for (int i = 0; i < TentativasDeLogin.FALHAS + 1; i++) {
            assertThat(entrar()).hasStatus(503);
        }
    }
}
