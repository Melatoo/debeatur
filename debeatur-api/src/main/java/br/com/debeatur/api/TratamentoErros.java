package br.com.debeatur.api;

import br.com.debeatur.core.indice.IndiceIndisponivelException;
import br.com.debeatur.indices.SgsException;
import org.springframework.http.HttpStatus;
import org.springframework.http.HttpStatusCode;
import org.springframework.http.ProblemDetail;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

@RestControllerAdvice
public class TratamentoErros {

    @ExceptionHandler(IllegalArgumentException.class)
    ProblemDetail parametroInvalido(IllegalArgumentException e) {
        return ProblemDetail.forStatusAndDetail(HttpStatus.BAD_REQUEST, e.getMessage());
    }

    @ExceptionHandler(IndiceIndisponivelException.class)
    ProblemDetail indiceIndisponivel(IndiceIndisponivelException e) {
        return ProblemDetail.forStatusAndDetail(HttpStatusCode.valueOf(422), e.getMessage());
    }

    @ExceptionHandler(SgsException.class)
    ProblemDetail sgsIndisponivel(SgsException e) {
        return ProblemDetail.forStatusAndDetail(HttpStatus.BAD_GATEWAY, e.getMessage());
    }
}
