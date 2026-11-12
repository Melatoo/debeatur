package br.com.debeatur.api.calculo;

import br.com.debeatur.api.acesso.UsuarioAutenticado;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ProblemDetail;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.net.URI;
import java.time.Instant;
import java.time.InstantSource;
import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/calculos/salvos")
public class CalculoSalvoController {

    private final CalculosSalvos calculos;
    private final InstantSource relogio;

    public CalculoSalvoController(CalculosSalvos calculos, InstantSource relogio) {
        this.calculos = calculos;
        this.relogio = relogio;
    }

    @PostMapping
    ResponseEntity<CalculoSalvoDtos.CalculoSalvo> salvar(@AuthenticationPrincipal UsuarioAutenticado usuario,
                                                         @Valid @RequestBody CalculoSalvoDtos.SalvarRequest request) {
        CalculoSalvoDtos.CalculoSalvo salvo = calculos.salvar(usuario.escritorio(), request);
        return ResponseEntity.created(URI.create("/api/calculos/salvos/" + salvo.id())).body(salvo);
    }

    /** Os cálculos do escritório, do mais recente para o mais antigo. */
    @GetMapping
    List<CalculoSalvoDtos.Resumo> listar(@AuthenticationPrincipal UsuarioAutenticado usuario) {
        return calculos.listar(usuario.escritorio());
    }

    /** O pedido salvo e o demonstrativo refeito com o snapshot das séries. */
    @GetMapping("/{id}")
    CalculoSalvoDtos.CalculoSalvo reabrir(@AuthenticationPrincipal UsuarioAutenticado usuario, @PathVariable UUID id) {
        return calculos.reabrir(usuario.escritorio(), id);
    }

    /**
     * O demonstrativo do cálculo salvo em PDF, refeito com o snapshot das séries: sai igual ao
     * do dia em que foi salvo, ainda que o BCB tenha revisado uma série depois.
     */
    @GetMapping(value = "/{id}/pdf", produces = MediaType.APPLICATION_PDF_VALUE)
    ResponseEntity<byte[]> pdf(@AuthenticationPrincipal UsuarioAutenticado usuario, @PathVariable UUID id) {
        CalculoSalvoDtos.CalculoSalvo salvo = calculos.reabrir(usuario.escritorio(), id);
        byte[] pdf = DemonstrativoPdf.gerarSalvo(salvo.calculo(), salvo.demonstrativo(), emBrasilia(relogio.instant()),
                emBrasilia(salvo.criadoEm()));
        return CalculoController.anexo(salvo.calculo(), pdf);
    }

    private static LocalDateTime emBrasilia(Instant instante) {
        return LocalDateTime.ofInstant(instante, CalculoController.FUSO_DA_EMISSAO);
    }

    @ExceptionHandler(CalculosSalvos.CalculoNaoEncontradoException.class)
    ProblemDetail naoEncontrado(CalculosSalvos.CalculoNaoEncontradoException e) {
        return ProblemDetail.forStatusAndDetail(HttpStatus.NOT_FOUND, e.getMessage());
    }
}
