package br.com.debeatur.api.acesso;

import jakarta.servlet.DispatcherType;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.ProviderManager;
import org.springframework.security.authentication.dao.DaoAuthenticationProvider;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.crypto.factory.PasswordEncoderFactories;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.access.intercept.AuthorizationFilter;
import org.springframework.security.web.authentication.HttpStatusEntryPoint;
import org.springframework.security.web.authentication.logout.HttpStatusReturningLogoutSuccessHandler;
import org.springframework.security.web.authentication.session.ChangeSessionIdAuthenticationStrategy;
import org.springframework.security.web.authentication.session.CompositeSessionAuthenticationStrategy;
import org.springframework.security.web.authentication.session.SessionAuthenticationStrategy;
import org.springframework.security.web.context.HttpSessionSecurityContextRepository;
import org.springframework.security.web.context.SecurityContextRepository;
import org.springframework.security.web.csrf.CookieCsrfTokenRepository;
import org.springframework.security.web.csrf.CsrfAuthenticationStrategy;
import org.springframework.security.web.csrf.CsrfTokenRepository;
import org.springframework.security.web.servlet.util.matcher.PathPatternRequestMatcher;

import java.util.List;

/**
 * Login por sessão (#7). O frontend fala com a API pela mesma origem (o nginx faz o proxy de
 * {@code /api/}), então a sessão vai num cookie HttpOnly e o CSRF segue o padrão de SPA: o
 * token vem no cookie {@code XSRF-TOKEN} e volta no cabeçalho {@code X-XSRF-TOKEN}.
 *
 * <p>Tudo em {@code /api/} exige login, menos entrar, consultar a sessão e aceitar um convite.
 * Gerenciar os usuários e os convites do escritório exige o papel de admin ({@link Papel}). Sem
 * login a API responde 401, sem redirecionar; sem o papel, 403. A cada pedido, a
 * {@link ConferenciaDaSessao} confere se o usuário segue ativo e com o mesmo papel.
 */
@Configuration(proxyBeanMethods = false)
class Seguranca {

    static final String SESSAO = "/api/sessao";
    static final String USUARIOS = "/api/usuarios";
    static final String CONVITES = "/api/convites";

    @Bean
    SecurityFilterChain filtros(HttpSecurity http, CsrfTokenRepository tokens,
                                SecurityContextRepository contextos, Usuarios usuarios) throws Exception {
        return http
                .authorizeHttpRequests(pedidos -> pedidos
                        // sem isto, o 403 do CSRF (e todo erro de quem não entrou) vira 401 ao passar pelo /error
                        .dispatcherTypeMatchers(DispatcherType.ERROR).permitAll()
                        .requestMatchers(HttpMethod.POST, SESSAO).permitAll()
                        .requestMatchers(HttpMethod.GET, SESSAO).permitAll()
                        .requestMatchers(HttpMethod.POST, CONVITES + "/consulta", CONVITES + "/aceite").permitAll()
                        .requestMatchers(USUARIOS, USUARIOS + "/**", CONVITES, CONVITES + "/**").hasRole(Papel.ADMIN.name())
                        .requestMatchers(HttpMethod.GET, "/actuator/health", "/actuator/health/**").permitAll()
                        .anyRequest().authenticated())
                .csrf(csrf -> csrf.spa().csrfTokenRepository(tokens))
                .securityContext(contexto -> contexto.securityContextRepository(contextos))
                .addFilterBefore(new ConferenciaDaSessao(usuarios, contextos), AuthorizationFilter.class)
                .exceptionHandling(erros -> erros.authenticationEntryPoint(new HttpStatusEntryPoint(HttpStatus.UNAUTHORIZED)))
                .logout(saida -> saida
                        .logoutRequestMatcher(PathPatternRequestMatcher.withDefaults().matcher(HttpMethod.DELETE, SESSAO))
                        .logoutSuccessHandler(new HttpStatusReturningLogoutSuccessHandler(HttpStatus.NO_CONTENT)))
                .build();
    }

    @Bean
    CsrfTokenRepository tokensCsrf() {
        return CookieCsrfTokenRepository.withHttpOnlyFalse();
    }

    @Bean
    SecurityContextRepository contextos() {
        return new HttpSessionSecurityContextRepository();
    }

    /** Ao entrar, a sessão troca de id (fixação de sessão) e o token CSRF é renovado. */
    @Bean
    SessionAuthenticationStrategy aoEntrar(CsrfTokenRepository tokens) {
        return new CompositeSessionAuthenticationStrategy(List.of(
                new ChangeSessionIdAuthenticationStrategy(), new CsrfAuthenticationStrategy(tokens)));
    }

    @Bean
    PasswordEncoder senhas() {
        return PasswordEncoderFactories.createDelegatingPasswordEncoder();
    }

    @Bean
    AuthenticationManager autenticacao(Usuarios usuarios, PasswordEncoder senhas) {
        DaoAuthenticationProvider provedor = new DaoAuthenticationProvider(usuarios);
        provedor.setPasswordEncoder(senhas);
        return new ProviderManager(provedor);
    }
}
