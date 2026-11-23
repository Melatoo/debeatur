package br.com.debeatur.api.acesso;

import jakarta.validation.Valid;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import java.net.URI;
import java.util.List;
import java.util.UUID;

/**
 * Os convites do escritório. Convidar, listar e cancelar são do admin; {@code consulta} e
 * {@code aceite} ficam abertos, para quem recebeu o link e ainda não tem conta ({@link Seguranca}).
 * O token vai no corpo, e não na URL, para não ficar no log de acesso.
 */
@RestController
@RequestMapping(Seguranca.CONVITES)
class ConvitesController {

    /** O e-mail tem o teto da RFC 5321, o mesmo do login. */
    record ConviteRequest(@NotBlank @Email @Size(max = 254) String email, @NotNull Papel papel) {
    }

    record Token(@NotBlank @Size(max = 100) String token) {
    }

    record Aceite(@NotBlank @Size(max = 100) String token, @NotBlank String senha) {
    }

    private static final Logger LOG = LoggerFactory.getLogger(ConvitesController.class);

    private final Convites convites;

    ConvitesController(Convites convites) {
        this.convites = convites;
    }

    /** Envia o convite por e-mail; um convite pendente para o mesmo e-mail deixa de valer. */
    @PostMapping
    ResponseEntity<Convites.Pendente> convidar(@AuthenticationPrincipal UsuarioAutenticado admin,
                                               @Valid @RequestBody ConviteRequest request) {
        Convites.Pendente convite = convites.convidar(admin, request.email(), request.papel());
        return ResponseEntity.created(URI.create(Seguranca.CONVITES + "/" + convite.id())).body(convite);
    }

    @GetMapping
    List<Convites.Pendente> pendentes(@AuthenticationPrincipal UsuarioAutenticado admin) {
        return convites.pendentes(admin.escritorio());
    }

    @DeleteMapping("/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    void cancelar(@AuthenticationPrincipal UsuarioAutenticado admin, @PathVariable UUID id) {
        if (!convites.cancelar(admin.escritorio(), id)) {
            throw new ConviteNaoEncontradoException(id);
        }
    }

    /** Para a tela do convite mostrar o e-mail e o escritório antes de pedir a senha. */
    @PostMapping("/consulta")
    Convites.Aberto consultar(@Valid @RequestBody Token token) {
        return convites.abrir(token.token()).orElseThrow(Convites.ConviteInvalidoException::new);
    }

    /** Cria o usuário; depois disso ele entra pelo login, com o e-mail do convite e a senha escolhida. */
    @PostMapping("/aceite")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    void aceitar(@Valid @RequestBody Aceite aceite) {
        convites.aceitar(aceite.token(), aceite.senha());
    }

    static class ConviteNaoEncontradoException extends RuntimeException {

        ConviteNaoEncontradoException(UUID id) {
            super("Convite " + id + " não encontrado");
        }
    }

    @ExceptionHandler(ConviteNaoEncontradoException.class)
    ProblemDetail naoEncontrado(ConviteNaoEncontradoException e) {
        return ProblemDetail.forStatusAndDetail(HttpStatus.NOT_FOUND, e.getMessage());
    }

    @ExceptionHandler(Convites.ConviteInvalidoException.class)
    ProblemDetail invalido(Convites.ConviteInvalidoException e) {
        return ProblemDetail.forStatusAndDetail(HttpStatus.NOT_FOUND, e.getMessage());
    }

    @ExceptionHandler(Convites.EmailJaCadastradoException.class)
    ProblemDetail jaCadastrado(Convites.EmailJaCadastradoException e) {
        return ProblemDetail.forStatusAndDetail(HttpStatus.CONFLICT, e.getMessage());
    }

    /** Dois convites, ou dois aceites, do mesmo e-mail ao mesmo tempo: o segundo esbarra no índice único. */
    @ExceptionHandler(DuplicateKeyException.class)
    ProblemDetail duplicado(DuplicateKeyException e) {
        return ProblemDetail.forStatusAndDetail(HttpStatus.CONFLICT,
                "Outro pedido para este e-mail chegou ao mesmo tempo. Atualize a página e confira.");
    }

    @ExceptionHandler(EnvioDeConvites.EnvioIndisponivelException.class)
    ProblemDetail envioIndisponivel(EnvioDeConvites.EnvioIndisponivelException e) {
        LOG.error(e.getMessage());
        return ProblemDetail.forStatusAndDetail(HttpStatus.SERVICE_UNAVAILABLE,
                "O envio de e-mail do Debeatur não está configurado. Fale com quem cuida da instalação.");
    }

    @ExceptionHandler(EnvioDeConvites.EnvioFalhouException.class)
    ProblemDetail envioFalhou(EnvioDeConvites.EnvioFalhouException e) {
        LOG.error("Falha ao enviar o convite", e);
        return ProblemDetail.forStatusAndDetail(HttpStatus.BAD_GATEWAY, e.getMessage());
    }
}
