package br.com.debeatur.api.acesso;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.servlet.http.HttpSession;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContext;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.core.context.SecurityContextHolderStrategy;
import org.springframework.security.web.context.SecurityContextRepository;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.util.Optional;

/**
 * Confere, a cada pedido de quem está logado, se o usuário continua ativo e com o mesmo papel. A
 * sessão guarda o usuário de quando ele entrou; sem esta conferência, quem o admin desativou ou
 * rebaixou seguiria com o acesso antigo até a sessão expirar.
 *
 * <ul>
 *   <li>Desativado (ou apagado): a sessão acaba, e o pedido segue como de quem não entrou.</li>
 *   <li>Papel trocado: a sessão passa a ter o papel novo, já neste pedido.</li>
 * </ul>
 *
 * <p>Custa uma consulta pela chave primária por pedido. Não é um bean: como {@code Filter}, o
 * Spring Boot o registraria também fora da cadeia de segurança.
 */
final class ConferenciaDaSessao extends OncePerRequestFilter {

    private final Usuarios usuarios;
    private final SecurityContextRepository contextos;
    private final SecurityContextHolderStrategy contexto = SecurityContextHolder.getContextHolderStrategy();

    ConferenciaDaSessao(Usuarios usuarios, SecurityContextRepository contextos) {
        this.usuarios = usuarios;
        this.contextos = contextos;
    }

    @Override
    protected void doFilterInternal(HttpServletRequest pedido, HttpServletResponse resposta, FilterChain cadeia)
            throws ServletException, IOException {
        Authentication autenticacao = contexto.getContext().getAuthentication();
        if (autenticacao != null && autenticacao.getPrincipal() instanceof UsuarioAutenticado usuario) {
            Optional<Usuarios.Situacao> situacao = usuarios.situacao(usuario.id());
            if (situacao.isEmpty() || !situacao.get().ativo() || !situacao.get().escritorio().equals(usuario.escritorio())) {
                encerrar(pedido);
            } else if (situacao.get().papel() != usuario.papel()) {
                trocarPapel(usuario.comPapel(situacao.get().papel()), pedido, resposta);
            }
        }
        cadeia.doFilter(pedido, resposta);
    }

    private void encerrar(HttpServletRequest pedido) {
        contexto.clearContext();
        HttpSession sessao = pedido.getSession(false);
        if (sessao != null) {
            sessao.invalidate();
        }
    }

    private void trocarPapel(UsuarioAutenticado atualizado, HttpServletRequest pedido, HttpServletResponse resposta) {
        SecurityContext novo = contexto.createEmptyContext();
        novo.setAuthentication(UsernamePasswordAuthenticationToken.authenticated(atualizado, null,
                atualizado.getAuthorities()));
        contexto.setContext(novo);
        contextos.saveContext(novo, pedido, resposta);
    }
}
