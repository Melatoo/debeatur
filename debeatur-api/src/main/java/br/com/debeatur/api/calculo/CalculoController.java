package br.com.debeatur.api.calculo;

import br.com.debeatur.core.calculo.ParametrosCalculo;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/calculos")
public class CalculoController {

    private final CalculoService service;

    public CalculoController(CalculoService service) {
        this.service = service;
    }

    @PostMapping
    public DemonstrativoResponse calcular(@Valid @RequestBody CalculoRequest request) {
        ParametrosCalculo parametros = request.paraDominio();
        return DemonstrativoResponse.de(service.calcular(parametros), parametros.regime());
    }
}
