package br.com.debeatur.api.calculo;

import br.com.debeatur.core.regime.RegimeAtualizacao;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/regimes")
public class RegimeController {

    /** Regime aplicado quando o cálculo não informa outro; serve de ponto de partida para editar. */
    @GetMapping("/padrao")
    public RegimeDto padrao() {
        return RegimeDto.de(RegimeAtualizacao.civelPadrao());
    }

    /** Regime do título omisso ou de "juros legais" pelo Tema 1368/STJ, enviado como critério do cálculo. */
    @GetMapping("/tema-1368")
    public RegimeDto tema1368() {
        return RegimeDto.de(RegimeAtualizacao.tema1368());
    }
}
