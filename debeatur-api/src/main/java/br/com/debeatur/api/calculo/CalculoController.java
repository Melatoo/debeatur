package br.com.debeatur.api.calculo;

import br.com.debeatur.core.alimentos.DemonstrativoPorRito;
import br.com.debeatur.core.calculo.Demonstrativo;
import br.com.debeatur.core.calculo.ParametrosCalculo;
import jakarta.validation.Valid;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ContentDisposition;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.nio.charset.StandardCharsets;
import java.time.InstantSource;
import java.time.LocalDateTime;
import java.time.ZoneId;


@RestController
@RequestMapping("/api/calculos")
public class CalculoController {

    /** Fuso da emissão do PDF: o do servidor (UTC no contêiner) poria a emissão da noite no dia seguinte. */
    static final ZoneId FUSO_DA_EMISSAO = ZoneId.of("America/Sao_Paulo");

    private final CalculoService service;
    private final InstantSource relogio;

    @Autowired
    public CalculoController(CalculoService service) {
        this(service, InstantSource.system());
    }

    /** @param relogio instante de emissão do PDF, impresso sempre em {@link #FUSO_DA_EMISSAO} */
    CalculoController(CalculoService service, InstantSource relogio) {
        this.service = service;
        this.relogio = relogio;
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

    /**
     * O mesmo cálculo de {@link #calcular}, como demonstrativo em PDF para juntar aos autos.
     * <p>
     * Recebe o pedido, e não um cálculo salvo, enquanto os cálculos não são persistidos (#4).
     */
    @PostMapping(value = "/pdf", produces = MediaType.APPLICATION_PDF_VALUE)
    public ResponseEntity<byte[]> pdf(@Valid @RequestBody CalculoRequest request) {
        DemonstrativoResponse resultado = calcular(request);
        byte[] pdf = DemonstrativoPdf.gerar(request, resultado, LocalDateTime.ofInstant(relogio.instant(), FUSO_DA_EMISSAO));
        ContentDisposition anexo = ContentDisposition.attachment()
                .filename(DemonstrativoPdf.nomeDoArquivo(request), StandardCharsets.UTF_8)
                .build();
        return ResponseEntity.ok()
                .contentType(MediaType.APPLICATION_PDF)
                .header(HttpHeaders.CONTENT_DISPOSITION, anexo.toString())
                .body(pdf);
    }
}
