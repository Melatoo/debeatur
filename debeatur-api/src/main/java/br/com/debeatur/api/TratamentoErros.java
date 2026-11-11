package br.com.debeatur.api;

import br.com.debeatur.core.indice.IndiceIndisponivelException;
import br.com.debeatur.core.indice.SalarioMinimoIndisponivelException;
import br.com.debeatur.indices.SgsException;
import org.springframework.http.HttpStatus;
import org.springframework.http.HttpStatusCode;
import org.springframework.http.ProblemDetail;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

import java.net.URI;
import java.time.YearMonth;
import java.time.format.DateTimeFormatter;

@RestControllerAdvice
public class TratamentoErros {

    /**
     * Tipo do problema de uma série sem o valor do mês. Vem com as propriedades {@code serie} (o
     * {@code Indice} ou {@code SALARIO_MINIMO}), {@code descricao} e {@code mes} (AAAA-MM), para o
     * frontend dizer se o índice só não saiu ainda e até que data-base dá para calcular.
     */
    static final URI SERIE_INDISPONIVEL = URI.create("/problemas/serie-indisponivel");

    private static final DateTimeFormatter MES = DateTimeFormatter.ofPattern("MM/yyyy");

    @ExceptionHandler(IllegalArgumentException.class)
    ProblemDetail parametroInvalido(IllegalArgumentException e) {
        return ProblemDetail.forStatusAndDetail(HttpStatus.BAD_REQUEST, e.getMessage());
    }

    @ExceptionHandler(IndiceIndisponivelException.class)
    ProblemDetail indiceIndisponivel(IndiceIndisponivelException e) {
        return serieIndisponivel(e.indice().name(), e.indice().descricao(), e.mes());
    }

    @ExceptionHandler(SalarioMinimoIndisponivelException.class)
    ProblemDetail salarioMinimoIndisponivel(SalarioMinimoIndisponivelException e) {
        return serieIndisponivel("SALARIO_MINIMO", "Salário mínimo", e.mes());
    }

    @ExceptionHandler(SgsException.class)
    ProblemDetail sgsIndisponivel(SgsException e) {
        return ProblemDetail.forStatusAndDetail(HttpStatus.BAD_GATEWAY, e.getMessage());
    }

    private static ProblemDetail serieIndisponivel(String serie, String descricao, YearMonth mes) {
        ProblemDetail problema = ProblemDetail.forStatusAndDetail(HttpStatusCode.valueOf(422),
                descricao + " indisponível para " + mes.format(MES) + ".");
        problema.setType(SERIE_INDISPONIVEL);
        problema.setTitle("Série indisponível");
        problema.setProperty("serie", serie);
        problema.setProperty("descricao", descricao);
        problema.setProperty("mes", mes.toString());
        return problema;
    }
}
