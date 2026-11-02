package br.com.debeatur.api.calculo;

import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.net.URI;
import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/calculos/salvos")
public class CalculoSalvoController {

    private final CalculosSalvos calculos;

    public CalculoSalvoController(CalculosSalvos calculos) {
        this.calculos = calculos;
    }

    @PostMapping
    ResponseEntity<CalculoSalvoDtos.CalculoSalvo> salvar(@Valid @RequestBody CalculoSalvoDtos.SalvarRequest request) {
        CalculoSalvoDtos.CalculoSalvo salvo = calculos.salvar(request);
        return ResponseEntity.created(URI.create("/api/calculos/salvos/" + salvo.id())).body(salvo);
    }

    /** Os cálculos do escritório, do mais recente para o mais antigo. */
    @GetMapping
    List<CalculoSalvoDtos.Resumo> listar() {
        return calculos.listar();
    }

    /** O pedido salvo e o demonstrativo refeito com o snapshot das séries. */
    @GetMapping("/{id}")
    CalculoSalvoDtos.CalculoSalvo reabrir(@PathVariable UUID id) {
        return calculos.reabrir(id);
    }

    @ExceptionHandler(CalculosSalvos.CalculoNaoEncontradoException.class)
    ProblemDetail naoEncontrado(CalculosSalvos.CalculoNaoEncontradoException e) {
        return ProblemDetail.forStatusAndDetail(HttpStatus.NOT_FOUND, e.getMessage());
    }
}
