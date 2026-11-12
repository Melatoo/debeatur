package br.com.debeatur.api.acesso;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;

import java.util.UUID;

/**
 * Cria, na subida, o usuário do escritório padrão a partir de {@code DEBEATUR_USUARIO_EMAIL} e
 * {@code DEBEATUR_USUARIO_SENHA}. O escritório padrão é o da migração V2, dono dos cálculos
 * salvos antes do login. Se o escritório já tem usuário, nada muda: a variável não redefine a
 * senha de quem já existe.
 */
@Component
class UsuarioInicial implements ApplicationRunner {

    static final UUID ESCRITORIO_PADRAO = UUID.fromString("00000000-0000-0000-0000-000000000001");

    /** O mínimo recomendado pelo NIST SP 800-63B para senha escolhida pelo usuário. */
    static final int TAMANHO_MINIMO_DA_SENHA = 8;

    private static final Logger LOG = LoggerFactory.getLogger(UsuarioInicial.class);

    private final Usuarios usuarios;
    private final PasswordEncoder senhas;
    private final String email;
    private final String senha;

    UsuarioInicial(Usuarios usuarios, PasswordEncoder senhas,
                   @Value("${debeatur.usuario-inicial.email:}") String email,
                   @Value("${debeatur.usuario-inicial.senha:}") String senha) {
        this.usuarios = usuarios;
        this.senhas = senhas;
        this.email = email;
        this.senha = senha;
    }

    @Override
    public void run(ApplicationArguments argumentos) {
        if (usuarios.escritorioTemUsuario(ESCRITORIO_PADRAO)) {
            return;
        }
        if (email.isBlank() || senha.isBlank()) {
            LOG.warn("Nenhum usuário cadastrado: defina DEBEATUR_USUARIO_EMAIL e DEBEATUR_USUARIO_SENHA para entrar.");
            return;
        }
        if (senha.length() < TAMANHO_MINIMO_DA_SENHA) {
            throw new IllegalStateException(
                    "DEBEATUR_USUARIO_SENHA precisa de ao menos " + TAMANHO_MINIMO_DA_SENHA + " caracteres.");
        }
        usuarios.inserir(ESCRITORIO_PADRAO, email, senhas.encode(senha));
        LOG.info("Usuário {} criado para o escritório padrão.", email.strip());
    }
}
