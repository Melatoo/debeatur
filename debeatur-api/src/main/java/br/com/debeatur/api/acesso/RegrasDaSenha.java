package br.com.debeatur.api.acesso;

import java.nio.charset.StandardCharsets;
import java.util.Optional;

/** O que a senha precisa ter, a do usuário inicial e a de quem aceita um convite. */
final class RegrasDaSenha {

    /** O mínimo recomendado pelo NIST SP 800-63B para senha escolhida pelo usuário. */
    static final int TAMANHO_MINIMO = 8;

    /** O bcrypt só considera 72 bytes, e o encoder do Spring recusa senha maior. */
    static final int BYTES_MAXIMOS = 72;

    private RegrasDaSenha() {
    }

    /** @return o que falta à senha, ou vazio se ela serve */
    static Optional<String> problema(String senha) {
        if (senha.length() < TAMANHO_MINIMO) {
            return Optional.of("A senha precisa de ao menos " + TAMANHO_MINIMO + " caracteres.");
        }
        if (senha.getBytes(StandardCharsets.UTF_8).length > BYTES_MAXIMOS) {
            return Optional.of("A senha pode ter até " + BYTES_MAXIMOS
                    + " bytes em UTF-8, o limite do bcrypt (letra acentuada conta 2).");
        }
        return Optional.empty();
    }
}
