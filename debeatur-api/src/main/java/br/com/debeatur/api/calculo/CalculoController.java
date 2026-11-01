package br.com.debeatur.api.calculo;

import br.com.debeatur.core.alimentos.DemonstrativoPorRito;
import br.com.debeatur.core.calculo.Demonstrativo;
import br.com.debeatur.core.calculo.ParametrosCalculo;
import br.com.debeatur.core.calculo.Parcela;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/api/calculos")
public class CalculoController {

    private final CalculoService service;

    public CalculoController(CalculoService service) {
        this.service = service;
    }

    @PostMapping
    public DemonstrativoResponse calcular(@Valid @RequestBody CalculoRequest request) {
        List<Parcela> parcelasDaPensao = request.pensao() == null ? List.of() : service.parcelas(request.pensao(), request.dataBase());
        ParametrosCalculo parametros = request.paraDominio(parcelasDaPensao);
        Demonstrativo demonstrativo = service.calcular(parametros);
        if (!request.separaPorRito()) {
            return DemonstrativoResponse.de(demonstrativo, parametros.regime());
        }
        DemonstrativoPorRito porRito = DemonstrativoPorRito.separar(demonstrativo, request.pensao().ajuizamento(),
                request.aplicarArt523());
        return DemonstrativoResponse.de(demonstrativo, parametros.regime(), porRito);
    }
}
