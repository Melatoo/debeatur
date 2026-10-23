package br.com.debeatur.indices;

public class SgsException extends RuntimeException {

    public SgsException(String mensagem) {
        super(mensagem);
    }

    public SgsException(String mensagem, Throwable causa) {
        super(mensagem, causa);
    }
}
