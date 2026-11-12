package br.com.debeatur.api.acesso;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;
import org.springframework.http.ResponseEntity;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.AuthenticationException;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.core.context.SecurityContext;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.web.authentication.session.SessionAuthenticationStrategy;
import org.springframework.security.web.context.SecurityContextRepository;
import org.springframework.security.web.csrf.CsrfToken;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * A sessão do usuário: {@code POST} entra, {@code GET} diz quem está logado e {@code DELETE}
 * sai (este último é o logout do Spring Security, configurado em {@link Seguranca}).
 */
@RestController
@RequestMapping(Seguranca.SESSAO)
class SessaoController {

    record Credenciais(@NotBlank String email, @NotBlank String senha) {
    }

    record Sessao(String email) {

        static Sessao de(UsuarioAutenticado usuario) {
            return new Sessao(usuario.email());
        }
    }

    private final AuthenticationManager autenticacao;
    private final SessionAuthenticationStrategy aoEntrar;
    private final SecurityContextRepository contextos;

    SessaoController(AuthenticationManager autenticacao, SessionAuthenticationStrategy aoEntrar,
                     SecurityContextRepository contextos) {
        this.autenticacao = autenticacao;
        this.aoEntrar = aoEntrar;
        this.contextos = contextos;
    }

    @PostMapping
    Sessao entrar(@Valid @RequestBody Credenciais credenciais, HttpServletRequest pedido, HttpServletResponse resposta) {
        Authentication autenticado = autenticacao.authenticate(
                UsernamePasswordAuthenticationToken.unauthenticated(credenciais.email().strip(), credenciais.senha()));
        aoEntrar.onAuthentication(autenticado, pedido, resposta);
        SecurityContext contexto = SecurityContextHolder.getContextHolderStrategy().createEmptyContext();
        contexto.setAuthentication(autenticado);
        SecurityContextHolder.getContextHolderStrategy().setContext(contexto);
        contextos.saveContext(contexto, pedido, resposta);
        // a renovação do token só grava o cookie novo quando alguém o lê
        if (pedido.getAttribute(CsrfToken.class.getName()) instanceof CsrfToken renovado) {
            renovado.getToken();
        }
        return Sessao.de((UsuarioAutenticado) autenticado.getPrincipal());
    }

    /**
     * 401 sem sessão. Fica aberto para o frontend saber se mostra o login e para entregar o
     * cookie do token CSRF, que só é gravado quando o token é lido.
     */
    @GetMapping
    ResponseEntity<Sessao> atual(@AuthenticationPrincipal UsuarioAutenticado usuario, CsrfToken token) {
        token.getToken();
        return usuario == null
                ? ResponseEntity.status(HttpStatus.UNAUTHORIZED).build()
                : ResponseEntity.ok(Sessao.de(usuario));
    }

    /** A mesma resposta para e-mail inexistente e senha errada, para não revelar quem tem conta. */
    @ExceptionHandler(AuthenticationException.class)
    ProblemDetail naoAutenticado(AuthenticationException e) {
        return ProblemDetail.forStatusAndDetail(HttpStatus.UNAUTHORIZED, "E-mail ou senha incorretos.");
    }
}
