package br.com.debeatur.api.acesso;

import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.webmvc.test.autoconfigure.MockMvcBuilderCustomizer;
import org.springframework.context.annotation.Bean;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.test.web.servlet.request.RequestPostProcessor;

import java.util.List;
import java.util.UUID;

import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.authentication;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;

/**
 * Toda requisição do MockMvc sai logada no escritório padrão e com o token CSRF, para os testes
 * que não são de autenticação testarem só o que testam. O login de verdade está em
 * {@code SessaoTest}.
 */
@TestConfiguration(proxyBeanMethods = false)
public class ComUsuarioLogado {

    public static final UUID ESCRITORIO_PADRAO = UsuarioInicial.ESCRITORIO_PADRAO;

    /** Um usuário do escritório, sem passar pelo banco. */
    public static RequestPostProcessor logadoNo(UUID escritorio) {
        UsuarioAutenticado usuario = new UsuarioAutenticado(UUID.randomUUID(), escritorio, "teste@debeatur.test", null);
        return authentication(UsernamePasswordAuthenticationToken.authenticated(usuario, null, List.of()));
    }

    @Bean
    MockMvcBuilderCustomizer comUsuarioLogado() {
        return mvc -> mvc.defaultRequest(get("/").with(logadoNo(ESCRITORIO_PADRAO)).with(csrf()));
    }
}
