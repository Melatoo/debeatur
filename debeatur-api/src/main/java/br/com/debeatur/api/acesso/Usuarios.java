package br.com.debeatur.api.acesso;

import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.stereotype.Repository;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * Tabela {@code usuario}. O e-mail é comparado sem diferenciar maiúsculas. O usuário desativado
 * não entra: para o login ele é um e-mail sem conta.
 */
@Repository
class Usuarios implements UserDetailsService {

    /** O usuário como o admin o vê na lista do escritório. */
    record Usuario(UUID id, String email, Papel papel, boolean ativo, Instant criadoEm) {
    }

    /** O que a sessão confere a cada pedido. */
    record Situacao(UUID escritorio, Papel papel, boolean ativo) {
    }

    private final JdbcClient jdbc;

    Usuarios(JdbcClient jdbc) {
        this.jdbc = jdbc;
    }

    @Override
    public UsuarioAutenticado loadUserByUsername(String email) {
        return buscar(email).orElseThrow(() -> new UsernameNotFoundException("Usuário não encontrado"));
    }

    Optional<UsuarioAutenticado> buscar(String email) {
        return jdbc.sql("SELECT id, escritorio_id, email, papel, senha FROM usuario WHERE lower(email) = lower(?) AND ativo")
                .param(email.strip())
                .query((rs, linha) -> new UsuarioAutenticado(rs.getObject("id", UUID.class),
                        rs.getObject("escritorio_id", UUID.class), rs.getString("email"),
                        Papel.valueOf(rs.getString("papel")), rs.getString("senha")))
                .optional();
    }

    Optional<Situacao> situacao(UUID id) {
        return jdbc.sql("SELECT escritorio_id, papel, ativo FROM usuario WHERE id = ?")
                .param(id)
                .query((rs, linha) -> new Situacao(rs.getObject("escritorio_id", UUID.class),
                        Papel.valueOf(rs.getString("papel")), rs.getBoolean("ativo")))
                .optional();
    }

    boolean escritorioTemUsuario(UUID escritorio) {
        return jdbc.sql("SELECT EXISTS (SELECT 1 FROM usuario WHERE escritorio_id = ?)")
                .param(escritorio)
                .query(Boolean.class)
                .single();
    }

    /** Se o e-mail já tem conta, ativa ou não, em qualquer escritório. */
    boolean emailCadastrado(String email) {
        return jdbc.sql("SELECT EXISTS (SELECT 1 FROM usuario WHERE lower(email) = lower(?))")
                .param(email.strip())
                .query(Boolean.class)
                .single();
    }

    /**
     * @param hash a senha já codificada pelo {@code PasswordEncoder}
     * @return o id do usuário criado
     */
    UUID inserir(UUID escritorio, String email, Papel papel, String hash) {
        UUID id = UUID.randomUUID();
        jdbc.sql("INSERT INTO usuario (id, escritorio_id, email, papel, senha) VALUES (?, ?, ?, ?, ?)")
                .params(id, escritorio, email.strip(), papel.name(), hash)
                .update();
        return id;
    }

    /** Os cálculos salvos antes de o escritório ter usuário passam a ser do primeiro admin dele. */
    void adotarCalculosSemAutor(UUID escritorio, UUID autor) {
        jdbc.sql("UPDATE calculo SET autor_id = ? WHERE escritorio_id = ? AND autor_id IS NULL")
                .params(autor, escritorio)
                .update();
    }

    /** Os usuários do escritório, na ordem em que entraram. */
    List<Usuario> listar(UUID escritorio) {
        return jdbc.sql("SELECT id, email, papel, ativo, criado_em FROM usuario WHERE escritorio_id = ? ORDER BY criado_em, email")
                .param(escritorio)
                .query(Usuarios::usuario)
                .list();
    }

    /**
     * Trava o escritório até o fim da transação: as mudanças de papel e de ativo passam uma de
     * cada vez, para que dois admins não se rebaixem ao mesmo tempo e deixem o escritório sem admin.
     */
    void travarEscritorio(UUID escritorio) {
        jdbc.sql("SELECT id FROM escritorio WHERE id = ? FOR UPDATE").param(escritorio).query(UUID.class).single();
    }

    Optional<Usuario> buscar(UUID escritorio, UUID id) {
        return jdbc.sql("SELECT id, email, papel, ativo, criado_em FROM usuario WHERE escritorio_id = ? AND id = ?")
                .params(escritorio, id)
                .query(Usuarios::usuario)
                .optional();
    }

    void alterar(UUID id, Papel papel, boolean ativo) {
        jdbc.sql("UPDATE usuario SET papel = ?, ativo = ? WHERE id = ?").params(papel.name(), ativo, id).update();
    }

    /** Apaga os convites ainda não aceitos que o usuário fez. */
    void cancelarConvitesDe(UUID convidante) {
        jdbc.sql("DELETE FROM convite WHERE convidado_por = ? AND aceito_em IS NULL").param(convidante).update();
    }

    private static Usuario usuario(ResultSet rs, int linha) throws SQLException {
        return new Usuario(rs.getObject("id", UUID.class), rs.getString("email"), Papel.valueOf(rs.getString("papel")),
                rs.getBoolean("ativo"), rs.getObject("criado_em", OffsetDateTime.class).toInstant());
    }
}
