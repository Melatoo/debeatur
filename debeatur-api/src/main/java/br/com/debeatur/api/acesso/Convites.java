package br.com.debeatur.api.acesso;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.Duration;
import java.time.Instant;
import java.time.InstantSource;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.time.temporal.ChronoUnit;
import java.util.Base64;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * Convites por e-mail para entrar num escritório (#5). O admin convida um e-mail com um papel; o
 * convidado recebe um link com um token e, ao abri-lo, escolhe a senha e passa a ser usuário do
 * escritório.
 *
 * <ul>
 *   <li>O token tem 256 bits aleatórios e só vale uma vez, até {@link #validade}. O banco guarda
 *       o SHA-256 dele, nunca o token.</li>
 *   <li>Convidar de novo o mesmo e-mail troca o convite pendente por um novo, e o link antigo
 *       deixa de valer.</li>
 *   <li>O e-mail que já tem conta, em qualquer escritório, não pode ser convidado: o login é
 *       pelo e-mail, e cada um pertence a um escritório só.</li>
 *   <li>O convite só vale enquanto quem convidou é admin ativo: ao ser rebaixado ou desativado,
 *       os convites pendentes dele caem ({@link GestaoDeUsuarios}), e o aceite confere isso de
 *       novo.</li>
 * </ul>
 */
@Service
class Convites {

    /** O convite pendente, como o admin o vê. */
    record Pendente(UUID id, String email, Papel papel, Instant criadoEm, Instant expiraEm) {
    }

    /** O que o convidado vê antes de escolher a senha. */
    record Aberto(String email, String escritorio) {
    }

    private static final SecureRandom ALEATORIO = new SecureRandom();

    private final JdbcClient jdbc;
    private final Usuarios usuarios;
    private final PasswordEncoder senhas;
    private final EnvioDeConvites envio;
    private final InstantSource relogio;
    private final Duration validade;

    Convites(JdbcClient jdbc, Usuarios usuarios, PasswordEncoder senhas, EnvioDeConvites envio, InstantSource relogio,
             @Value("${debeatur.convite.validade}") Duration validade) {
        this.jdbc = jdbc;
        this.usuarios = usuarios;
        this.senhas = senhas;
        this.envio = envio;
        this.relogio = relogio;
        this.validade = validade;
    }

    /**
     * Grava o convite e envia o e-mail; se o envio falha, o convite não fica gravado.
     *
     * @throws EmailJaCadastradoException se o e-mail já tem conta
     */
    @Transactional
    Pendente convidar(UsuarioAutenticado admin, String email, Papel papel) {
        String destinatario = email.strip();
        if (usuarios.emailCadastrado(destinatario)) {
            throw new EmailJaCadastradoException();
        }
        jdbc.sql("DELETE FROM convite WHERE escritorio_id = ? AND lower(email) = lower(?) AND aceito_em IS NULL")
                .params(admin.escritorio(), destinatario)
                .update();
        String token = novoToken();
        Instant agora = agora();
        Pendente convite = new Pendente(UUID.randomUUID(), destinatario, papel, agora, agora.plus(validade));
        jdbc.sql("""
                        INSERT INTO convite (id, escritorio_id, email, papel, token_hash, convidado_por, criado_em, expira_em)
                        VALUES (?, ?, ?, ?, ?, ?, ?, ?)
                        """)
                .params(convite.id(), admin.escritorio(), destinatario, papel.name(), hash(token), admin.id(),
                        convite.criadoEm().atOffset(ZoneOffset.UTC), convite.expiraEm().atOffset(ZoneOffset.UTC))
                .update();
        envio.enviar(destinatario, nomeDoEscritorio(admin.escritorio()), admin.email(), token, convite.expiraEm());
        return convite;
    }

    /** Os convites ainda não aceitos, vencidos ou não, do mais recente para o mais antigo. */
    List<Pendente> pendentes(UUID escritorio) {
        return jdbc.sql("""
                        SELECT id, email, papel, criado_em, expira_em FROM convite
                        WHERE escritorio_id = ? AND aceito_em IS NULL ORDER BY criado_em DESC, id
                        """)
                .param(escritorio)
                .query(Convites::pendente)
                .list();
    }

    /** @return se havia um convite pendente com esse id no escritório */
    boolean cancelar(UUID escritorio, UUID id) {
        return jdbc.sql("DELETE FROM convite WHERE escritorio_id = ? AND id = ? AND aceito_em IS NULL")
                .params(escritorio, id)
                .update() > 0;
    }

    /** O convite que o token abre, se ainda vale. */
    Optional<Aberto> abrir(String token) {
        return jdbc.sql("""
                        SELECT c.email, e.nome FROM convite c
                        JOIN escritorio e ON e.id = c.escritorio_id
                        JOIN usuario u ON u.id = c.convidado_por AND u.papel = 'ADMIN' AND u.ativo
                        WHERE c.token_hash = ? AND c.aceito_em IS NULL AND c.expira_em > ?
                        """)
                .params(hash(token), agora().atOffset(ZoneOffset.UTC))
                .query((rs, linha) -> new Aberto(rs.getString("email"), rs.getString("nome")))
                .optional();
    }

    /**
     * Gasta o convite e cria o usuário com a senha escolhida.
     *
     * @throws IllegalArgumentException     se a senha não serve
     * @throws ConviteInvalidoException     se o token não abre um convite que ainda vale
     * @throws EmailJaCadastradoException se o e-mail ganhou conta depois do convite
     */
    @Transactional
    void aceitar(String token, String senha) {
        RegrasDaSenha.problema(senha).ifPresent(problema -> {
            throw new IllegalArgumentException(problema);
        });
        record Aceito(UUID escritorio, String email, Papel papel) {
        }
        Aceito aceito = jdbc.sql("""
                        UPDATE convite c SET aceito_em = ?
                        FROM usuario u
                        WHERE u.id = c.convidado_por AND u.papel = 'ADMIN' AND u.ativo
                          AND c.token_hash = ? AND c.aceito_em IS NULL AND c.expira_em > ?
                        RETURNING c.escritorio_id, c.email, c.papel
                        """)
                .params(agora().atOffset(ZoneOffset.UTC), hash(token), agora().atOffset(ZoneOffset.UTC))
                .query((rs, linha) -> new Aceito(rs.getObject("escritorio_id", UUID.class), rs.getString("email"),
                        Papel.valueOf(rs.getString("papel"))))
                .optional()
                .orElseThrow(ConviteInvalidoException::new);
        if (usuarios.emailCadastrado(aceito.email())) {
            throw new EmailJaCadastradoException();
        }
        usuarios.inserir(aceito.escritorio(), aceito.email(), aceito.papel(), senhas.encode(senha));
    }

    private String nomeDoEscritorio(UUID escritorio) {
        return jdbc.sql("SELECT nome FROM escritorio WHERE id = ?").param(escritorio).query(String.class).single();
    }

    /** 32 bytes em Base64 sem preenchimento, que cabe na URL sem escape. */
    private static String novoToken() {
        byte[] bytes = new byte[32];
        ALEATORIO.nextBytes(bytes);
        return Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
    }

    static byte[] hash(String token) {
        try {
            return MessageDigest.getInstance("SHA-256").digest(token.getBytes(StandardCharsets.UTF_8));
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("Toda JVM tem SHA-256", e);
        }
    }

    /** Truncado ao microssegundo, a precisão do TIMESTAMPTZ. */
    private Instant agora() {
        return relogio.instant().truncatedTo(ChronoUnit.MICROS);
    }

    private static Pendente pendente(ResultSet rs, int linha) throws SQLException {
        return new Pendente(rs.getObject("id", UUID.class), rs.getString("email"), Papel.valueOf(rs.getString("papel")),
                rs.getObject("criado_em", OffsetDateTime.class).toInstant(),
                rs.getObject("expira_em", OffsetDateTime.class).toInstant());
    }

    static class EmailJaCadastradoException extends RuntimeException {

        EmailJaCadastradoException() {
            super("Este e-mail já tem conta no Debeatur.");
        }
    }

    /** O mesmo erro para token inexistente, vencido ou já usado: o convidado só precisa de um novo. */
    static class ConviteInvalidoException extends RuntimeException {

        ConviteInvalidoException() {
            super("Este convite não vale mais: ele venceu, já foi usado ou foi trocado por outro. Peça um novo ao admin do escritório.");
        }
    }
}
