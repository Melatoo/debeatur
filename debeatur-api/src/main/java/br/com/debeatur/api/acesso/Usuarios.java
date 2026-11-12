package br.com.debeatur.api.acesso;

import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.stereotype.Repository;

import java.util.Optional;
import java.util.UUID;

/** Tabela {@code usuario}. O e-mail é comparado sem diferenciar maiúsculas. */
@Repository
class Usuarios implements UserDetailsService {

    private final JdbcClient jdbc;

    Usuarios(JdbcClient jdbc) {
        this.jdbc = jdbc;
    }

    @Override
    public UsuarioAutenticado loadUserByUsername(String email) {
        return buscar(email).orElseThrow(() -> new UsernameNotFoundException("Usuário não encontrado"));
    }

    Optional<UsuarioAutenticado> buscar(String email) {
        return jdbc.sql("SELECT id, escritorio_id, email, senha FROM usuario WHERE lower(email) = lower(?)")
                .param(email.strip())
                .query((rs, linha) -> new UsuarioAutenticado(rs.getObject("id", UUID.class),
                        rs.getObject("escritorio_id", UUID.class), rs.getString("email"), rs.getString("senha")))
                .optional();
    }

    boolean escritorioTemUsuario(UUID escritorio) {
        return jdbc.sql("SELECT EXISTS (SELECT 1 FROM usuario WHERE escritorio_id = ?)")
                .param(escritorio)
                .query(Boolean.class)
                .single();
    }

    /** @param hash a senha já codificada pelo {@code PasswordEncoder} */
    void inserir(UUID escritorio, String email, String hash) {
        jdbc.sql("INSERT INTO usuario (id, escritorio_id, email, senha) VALUES (?, ?, ?, ?)")
                .params(UUID.randomUUID(), escritorio, email.strip(), hash)
                .update();
    }
}
