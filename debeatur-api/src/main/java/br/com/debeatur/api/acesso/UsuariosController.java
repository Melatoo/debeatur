package br.com.debeatur.api.acesso;

import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.UUID;

/** Os usuários do escritório, para o admin ({@link Seguranca} exige o papel). */
@RestController
@RequestMapping(Seguranca.USUARIOS)
class UsuariosController {

    /** Os campos ausentes ficam como estão. {@code ativo: false} é como o admin remove alguém. */
    record Alteracao(Papel papel, Boolean ativo) {
    }

    private final GestaoDeUsuarios gestao;

    UsuariosController(GestaoDeUsuarios gestao) {
        this.gestao = gestao;
    }

    /** Ativos e desativados, na ordem em que entraram. */
    @GetMapping
    List<Usuarios.Usuario> listar(@AuthenticationPrincipal UsuarioAutenticado admin) {
        return gestao.listar(admin);
    }

    @PatchMapping("/{id}")
    Usuarios.Usuario alterar(@AuthenticationPrincipal UsuarioAutenticado admin, @PathVariable UUID id,
                             @RequestBody Alteracao alteracao) {
        return gestao.alterar(admin, id, alteracao.papel(), alteracao.ativo());
    }

    @ExceptionHandler(GestaoDeUsuarios.UsuarioNaoEncontradoException.class)
    ProblemDetail naoEncontrado(GestaoDeUsuarios.UsuarioNaoEncontradoException e) {
        return ProblemDetail.forStatusAndDetail(HttpStatus.NOT_FOUND, e.getMessage());
    }

    @ExceptionHandler(GestaoDeUsuarios.ProprioAcessoException.class)
    ProblemDetail proprioAcesso(GestaoDeUsuarios.ProprioAcessoException e) {
        return ProblemDetail.forStatusAndDetail(HttpStatus.CONFLICT, e.getMessage());
    }

    @ExceptionHandler(GestaoDeUsuarios.SemPermissaoException.class)
    ProblemDetail semPermissao(GestaoDeUsuarios.SemPermissaoException e) {
        return ProblemDetail.forStatusAndDetail(HttpStatus.FORBIDDEN, e.getMessage());
    }
}
