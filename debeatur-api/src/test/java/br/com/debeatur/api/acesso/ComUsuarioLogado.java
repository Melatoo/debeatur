package br.com.debeatur.api.acesso;

import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.webmvc.test.autoconfigure.MockMvcBuilderCustomizer;
import org.springframework.context.annotation.Bean;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.test.web.servlet.request.RequestPostProcessor;

import java.nio.charset.StandardCharsets;
import java.util.UUID;

import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.authentication;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;

/**
 * Toda requisição do MockMvc sai logada como admin do escritório padrão e com o token CSRF, para
 * os testes que não são de autenticação testarem só o que testam. O login de verdade está em
 * {@code SessaoTest}.
 *
 * <p>Nos testes com banco, a {@link ConferenciaDaSessao} só deixa passar usuário que existe:
 * eles gravam antes o usuário com {@link #cadastrar}.
 */
@TestConfiguration(proxyBeanMethods = false)
public class ComUsuarioLogado {

    public static final UUID ESCRITORIO_PADRAO = UsuarioInicial.ESCRITORIO_PADRAO;

    /** O admin de um escritório, com id fixo por escritório, sem passar pelo banco. */
    public static RequestPostProcessor logadoNo(UUID escritorio) {
        return logadoComo(adminDe(escritorio), escritorio, Papel.ADMIN);
    }

    public static RequestPostProcessor logadoComo(UUID usuario, UUID escritorio, Papel papel) {
        UsuarioAutenticado autenticado = new UsuarioAutenticado(usuario, escritorio, "teste@debeatur.test", papel, null);
        return authentication(UsernamePasswordAuthenticationToken.authenticated(autenticado, null,
                autenticado.getAuthorities()));
    }

    /** O id do admin de {@link #logadoNo}. */
    public static UUID adminDe(UUID escritorio) {
        return UUID.nameUUIDFromBytes(("admin de " + escritorio).getBytes(StandardCharsets.UTF_8));
    }

    /** Grava o admin de {@link #logadoNo} no escritório, que já precisa existir. */
    public static void cadastrar(JdbcClient jdbc, UUID escritorio) {
        jdbc.sql("""
                        INSERT INTO usuario (id, escritorio_id, email, papel, senha) VALUES (?, ?, ?, 'ADMIN', '{noop}x')
                        ON CONFLICT DO NOTHING
                        """)
                .params(adminDe(escritorio), escritorio, "admin-" + escritorio + "@debeatur.test")
                .update();
    }

    /** Grava um usuário com o papel e devolve o id dele. */
    public static UUID cadastrar(JdbcClient jdbc, UUID escritorio, String email, Papel papel) {
        UUID id = UUID.randomUUID();
        jdbc.sql("INSERT INTO usuario (id, escritorio_id, email, papel, senha) VALUES (?, ?, ?, ?, '{noop}x')")
                .params(id, escritorio, email, papel.name())
                .update();
        return id;
    }

    @Bean
    MockMvcBuilderCustomizer comUsuarioLogado() {
        return mvc -> mvc.defaultRequest(get("/").with(logadoNo(ESCRITORIO_PADRAO)).with(csrf()));
    }
}
