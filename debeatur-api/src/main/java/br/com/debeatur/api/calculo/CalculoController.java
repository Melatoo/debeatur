package br.com.debeatur.api.calculo;

import br.com.debeatur.core.alimentos.DemonstrativoPorRito;
import br.com.debeatur.core.calculo.Demonstrativo;
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
        CalculoService.ParcelasDaPensao pensao = request.pensao() == null
                ? CalculoService.ParcelasDaPensao.vazia()
                : service.parcelas(request.pensao(), request.dataBase());
        ParametrosCalculo parametros = request.paraDominio(pensao.parcelas());
        Demonstrativo demonstrativo = service.calcular(parametros);
        if (!request.separaPorRito()) {
            return DemonstrativoResponse.de(demonstrativo, parametros.regime(), pensao.memoria());
        }
        DemonstrativoPorRito porRito = DemonstrativoPorRito.separar(demonstrativo, request.pensao().ajuizamento(),
                request.aplicarArt523());
        return DemonstrativoResponse.de(demonstrativo, parametros.regime(), pensao.memoria(), porRito);
    }
}
