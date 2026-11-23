package br.com.debeatur.api.acesso;

import br.com.debeatur.api.PostgresDeTeste;
import com.jayway.jsonpath.JsonPath;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.mail.MailSendException;
import org.springframework.mock.web.MockHttpSession;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.assertj.MockMvcTester;
import org.springframework.test.web.servlet.assertj.MvcTestResult;
import org.springframework.test.web.servlet.request.RequestPostProcessor;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import org.testcontainers.junit.jupiter.Testcontainers;

import java.nio.charset.StandardCharsets;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatExceptionOfType;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;

/**
 * Vários usuários por escritório (#5): os convites, a gestão dos usuários pelo admin e a sessão
 * que acompanha a mudança de papel e a desativação. O e-mail é simulado; o token sai do que a
 * API mandaria nele. Cada teste usa um escritório novo e desfaz o que gravou.
 */
@SpringBootTest(properties = "debeatur.series.atualizacao=-")
@AutoConfigureMockMvc
@Import(PostgresDeTeste.class)
@Testcontainers(disabledWithoutDocker = true)
@Transactional
class UsuariosEConvitesTest {

    private static final String SENHA = "uma senha longa o bastante";

    /** Cada login de um IP próprio, para as falhas de um teste não bloquearem outro. */
    private static final AtomicInteger IPS = new AtomicInteger();

    @Autowired
    private MockMvcTester mvc;

    @Autowired
    private JdbcClient jdbc;

    @Autowired
    private GestaoDeUsuarios gestao;

    @MockitoBean
    private EnvioDeConvites envio;

    private UUID escritorio;
    private RequestPostProcessor comoAdmin;

    @BeforeEach
    void escritorioNovo() {
        escritorio = UUID.randomUUID();
        jdbc.sql("INSERT INTO escritorio (id, nome) VALUES (?, 'Silva Advogados')").param(escritorio).update();
        ComUsuarioLogado.cadastrar(jdbc, escritorio);
        comoAdmin = ComUsuarioLogado.logadoNo(escritorio);
    }

    private MvcTestResult convidar(String email, Papel papel) {
        return mvc.post().uri("/api/convites").with(comoAdmin).with(csrf()).contentType(MediaType.APPLICATION_JSON)
                .content("{\"email\": \"%s\", \"papel\": \"%s\"}".formatted(email, papel)).exchange();
    }

    /** O token do último e-mail de convite para o endereço. */
    private String tokenEnviadoPara(String email) {
        ArgumentCaptor<String> token = ArgumentCaptor.forClass(String.class);
        verify(envio, org.mockito.Mockito.atLeastOnce()).enviar(eq(email), anyString(), anyString(), token.capture(), any());
        return token.getValue();
    }

    private MvcTestResult consultar(String token) {
        return mvc.post().uri("/api/convites/consulta").with(csrf()).contentType(MediaType.APPLICATION_JSON)
                .content("{\"token\": \"%s\"}".formatted(token)).exchange();
    }

    private MvcTestResult aceitar(String token, String senha) {
        return mvc.post().uri("/api/convites/aceite").with(csrf()).contentType(MediaType.APPLICATION_JSON)
                .content("{\"token\": \"%s\", \"senha\": \"%s\"}".formatted(token, senha)).exchange();
    }

    private MvcTestResult entrar(String email, String senha) {
        int n = IPS.incrementAndGet();
        return mvc.post().uri("/api/sessao").with(csrf()).with(pedido -> {
                    pedido.setRemoteAddr("198.51.100." + n);
                    return pedido;
                })
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"email\": \"%s\", \"senha\": \"%s\"}".formatted(email, senha)).exchange();
    }

    /** Uma sessão de verdade, que a {@link ConferenciaDaSessao} confere a cada pedido. */
    private MockHttpSession sessaoDe(String email, String senha) {
        MvcTestResult login = entrar(email, senha);
        assertThat(login).hasStatusOk();
        return (MockHttpSession) login.getRequest().getSession(false);
    }

    private MvcTestResult alterar(UUID usuario, String corpo) {
        return mvc.patch().uri("/api/usuarios/" + usuario).with(comoAdmin).with(csrf())
                .contentType(MediaType.APPLICATION_JSON).content(corpo).exchange();
    }

    @Test
    void oConvidadoAceitaEscolheASenhaEEntraComOPapelDoConvite() throws Exception {
        assertThat(convidar("Bia@Silva.adv.br", Papel.MEMBRO)).hasStatus(201).bodyJson()
                .hasPathSatisfying("$.email", v -> v.assertThat().isEqualTo("Bia@Silva.adv.br"))
                .hasPathSatisfying("$.papel", v -> v.assertThat().isEqualTo("MEMBRO"));
        String token = tokenEnviadoPara("Bia@Silva.adv.br");

        assertThat(consultar(token)).hasStatusOk().bodyJson()
                .isLenientlyEqualTo("{\"email\": \"Bia@Silva.adv.br\", \"escritorio\": \"Silva Advogados\"}");
        assertThat(aceitar(token, SENHA)).hasStatus(204);

        assertThat(entrar("bia@silva.adv.br", SENHA)).hasStatusOk().bodyJson()
                .isLenientlyEqualTo("{\"email\": \"Bia@Silva.adv.br\", \"papel\": \"MEMBRO\"}");
        assertThat(jdbc.sql("SELECT escritorio_id FROM usuario WHERE email = 'Bia@Silva.adv.br'").query(UUID.class).single())
                .isEqualTo(escritorio);
        // o convite só vale uma vez, e some da lista do admin
        assertThat(aceitar(token, "outra senha qualquer")).hasStatus(404);
        assertThat(consultar(token)).hasStatus(404);
        assertThat(mvc.get().uri("/api/convites").with(comoAdmin)).hasStatusOk().bodyJson().isLenientlyEqualTo("[]");
    }

    @Test
    void oBancoGuardaSoOHashDoToken() {
        convidar("bia@silva.adv.br", Papel.ADMIN);
        String token = tokenEnviadoPara("bia@silva.adv.br");

        byte[] guardado = jdbc.sql("SELECT token_hash FROM convite WHERE escritorio_id = ?").param(escritorio)
                .query(byte[].class).single();
        assertThat(guardado).isEqualTo(Convites.hash(token)).isNotEqualTo(token.getBytes(StandardCharsets.UTF_8));
        assertThat(token).matches("[A-Za-z0-9_-]{43}");
    }

    @Test
    void convidarDeNovoTrocaOLink() {
        convidar("bia@silva.adv.br", Papel.MEMBRO);
        String primeiro = tokenEnviadoPara("bia@silva.adv.br");
        convidar("BIA@silva.adv.br", Papel.ADMIN);
        String segundo = tokenEnviadoPara("BIA@silva.adv.br");

        assertThat(consultar(primeiro)).hasStatus(404);
        assertThat(consultar(segundo)).hasStatusOk();
        assertThat(mvc.get().uri("/api/convites").with(comoAdmin)).hasStatusOk().bodyJson()
                .hasPathSatisfying("$", v -> v.assertThat().asArray().hasSize(1))
                .hasPathSatisfying("$[0].papel", v -> v.assertThat().isEqualTo("ADMIN"));
    }

    @Test
    void conviteVencidoNaoVale() {
        convidar("bia@silva.adv.br", Papel.MEMBRO);
        String token = tokenEnviadoPara("bia@silva.adv.br");
        jdbc.sql("UPDATE convite SET expira_em = now() - interval '1 minute' WHERE escritorio_id = ?").param(escritorio).update();

        assertThat(consultar(token)).hasStatus(404);
        assertThat(aceitar(token, SENHA)).hasStatus(404);
        assertThat(jdbc.sql("SELECT count(*) FROM usuario WHERE email = 'bia@silva.adv.br'").query(Long.class).single()).isZero();
    }

    @Test
    void tokenInventadoNaoAbreConvite() {
        assertThat(consultar("token-que-ninguem-gerou")).hasStatus(404);
        assertThat(aceitar("token-que-ninguem-gerou", SENHA)).hasStatus(404);
    }

    @Test
    void senhaCurtaNaoGastaOConvite() {
        convidar("bia@silva.adv.br", Papel.MEMBRO);
        String token = tokenEnviadoPara("bia@silva.adv.br");

        assertThat(aceitar(token, "curta")).hasStatus(400).bodyJson()
                .hasPathSatisfying("$.detail", v -> v.assertThat().asString().contains("ao menos 8 caracteres"));
        assertThat(aceitar(token, SENHA)).hasStatus(204);
    }

    @Test
    void emailQueJaTemContaNaoPodeSerConvidado() {
        UUID outro = UUID.randomUUID();
        jdbc.sql("INSERT INTO escritorio (id, nome) VALUES (?, 'Outro')").param(outro).update();
        ComUsuarioLogado.cadastrar(jdbc, outro, "Ana@Outro.adv.br", Papel.ADMIN);

        assertThat(convidar("ana@outro.adv.br", Papel.MEMBRO)).hasStatus(409);
        verify(envio, never()).enviar(anyString(), anyString(), anyString(), anyString(), any());
    }

    /**
     * Fora da transação do teste, que seguraria o rollback do convite: o escritório do
     * {@link #escritorioNovo} fica gravado de verdade, e o teste o apaga no fim.
     */
    @Test
    @Transactional(propagation = Propagation.NOT_SUPPORTED)
    void falhaNoEnvioNaoDeixaOConviteGravado() {
        doThrow(new EnvioDeConvites.EnvioFalhouException(new MailSendException("SMTP fora do ar")))
                .when(envio).enviar(anyString(), anyString(), anyString(), anyString(), any());
        try {
            assertThat(convidar("bia@silva.adv.br", Papel.MEMBRO)).hasStatus(502);
            assertThat(jdbc.sql("SELECT count(*) FROM convite WHERE escritorio_id = ?").param(escritorio)
                    .query(Long.class).single()).isZero();
        } finally {
            jdbc.sql("DELETE FROM convite WHERE escritorio_id = ?").param(escritorio).update();
            jdbc.sql("DELETE FROM usuario WHERE escritorio_id = ?").param(escritorio).update();
            jdbc.sql("DELETE FROM escritorio WHERE id = ?").param(escritorio).update();
        }
    }

    @Test
    void semSmtpConvidarResponde503() {
        doThrow(new EnvioDeConvites.EnvioIndisponivelException())
                .when(envio).enviar(anyString(), anyString(), anyString(), anyString(), any());

        assertThat(convidar("bia@silva.adv.br", Papel.MEMBRO)).hasStatus(503);
    }

    @Test
    void oAdminCancelaOConvite() throws Exception {
        String id = JsonPath.read(convidar("bia@silva.adv.br", Papel.MEMBRO).getResponse().getContentAsString(), "$.id");
        String token = tokenEnviadoPara("bia@silva.adv.br");

        assertThat(mvc.delete().uri("/api/convites/" + id).with(comoAdmin).with(csrf())).hasStatus(204);
        assertThat(consultar(token)).hasStatus(404);
        assertThat(mvc.delete().uri("/api/convites/" + id).with(comoAdmin).with(csrf())).hasStatus(404);
    }

    @Test
    void oMembroNaoGerenciaUsuariosNemConvites() {
        UUID membro = ComUsuarioLogado.cadastrar(jdbc, escritorio, "membro@silva.adv.br", Papel.MEMBRO);
        RequestPostProcessor comoMembro = ComUsuarioLogado.logadoComo(membro, escritorio, Papel.MEMBRO);

        assertThat(mvc.get().uri("/api/usuarios").with(comoMembro)).hasStatus(403);
        assertThat(mvc.patch().uri("/api/usuarios/" + ComUsuarioLogado.adminDe(escritorio)).with(comoMembro).with(csrf())
                .contentType(MediaType.APPLICATION_JSON).content("{\"ativo\": false}")).hasStatus(403);
        assertThat(mvc.get().uri("/api/convites").with(comoMembro)).hasStatus(403);
        assertThat(mvc.post().uri("/api/convites").with(comoMembro).with(csrf()).contentType(MediaType.APPLICATION_JSON)
                .content("{\"email\": \"x@silva.adv.br\", \"papel\": \"ADMIN\"}")).hasStatus(403);
    }

    @Test
    void semLoginAGestaoResponde401() {
        assertThat(mvc.get().uri("/api/usuarios")).hasStatus(401);
        assertThat(mvc.get().uri("/api/convites")).hasStatus(401);
    }

    @Test
    void oAdminListaEMudaOPapelDosUsuariosDoEscritorio() {
        UUID membro = ComUsuarioLogado.cadastrar(jdbc, escritorio, "membro@silva.adv.br", Papel.MEMBRO);

        assertThat(mvc.get().uri("/api/usuarios").with(comoAdmin)).hasStatusOk().bodyJson()
                .hasPathSatisfying("$", v -> v.assertThat().asArray().hasSize(2));
        assertThat(alterar(membro, "{\"papel\": \"ADMIN\"}")).hasStatusOk().bodyJson()
                .isLenientlyEqualTo("{\"email\": \"membro@silva.adv.br\", \"papel\": \"ADMIN\", \"ativo\": true}");
        assertThat(alterar(membro, "{\"ativo\": false}")).hasStatusOk().bodyJson()
                .isLenientlyEqualTo("{\"papel\": \"ADMIN\", \"ativo\": false}");
    }

    @Test
    void oConviteDeQuemPerdeuOAdminNaoValeMais() {
        UUID colega = ComUsuarioLogado.cadastrar(jdbc, escritorio, "colega@silva.adv.br", Papel.ADMIN);
        RequestPostProcessor comoColega = ComUsuarioLogado.logadoComo(colega, escritorio, Papel.ADMIN);
        assertThat(mvc.post().uri("/api/convites").with(comoColega).with(csrf()).contentType(MediaType.APPLICATION_JSON)
                .content("{\"email\": \"x@fora.com\", \"papel\": \"ADMIN\"}")).hasStatus(201);
        String token = tokenEnviadoPara("x@fora.com");

        assertThat(alterar(colega, "{\"ativo\": false}")).hasStatusOk();

        assertThat(consultar(token)).hasStatus(404);
        assertThat(aceitar(token, SENHA)).hasStatus(404);
        assertThat(mvc.get().uri("/api/convites").with(comoAdmin)).hasStatusOk().bodyJson().isLenientlyEqualTo("[]");
    }

    /** O aceite confere quem convidou mesmo que o convite não tenha caído na mudança de papel. */
    @Test
    void oAceiteConfereQueQuemConvidouAindaEAdmin() {
        UUID colega = ComUsuarioLogado.cadastrar(jdbc, escritorio, "colega@silva.adv.br", Papel.ADMIN);
        RequestPostProcessor comoColega = ComUsuarioLogado.logadoComo(colega, escritorio, Papel.ADMIN);
        mvc.post().uri("/api/convites").with(comoColega).with(csrf()).contentType(MediaType.APPLICATION_JSON)
                .content("{\"email\": \"x@fora.com\", \"papel\": \"ADMIN\"}").exchange();
        String token = tokenEnviadoPara("x@fora.com");
        jdbc.sql("UPDATE usuario SET papel = 'MEMBRO' WHERE id = ?").param(colega).update();

        assertThat(consultar(token)).hasStatus(404);
        assertThat(aceitar(token, SENHA)).hasStatus(404);
        assertThat(jdbc.sql("SELECT count(*) FROM usuario WHERE email = 'x@fora.com'").query(Long.class).single()).isZero();
    }

    @Test
    void ninguemMudaOProprioAcesso() {
        assertThat(alterar(ComUsuarioLogado.adminDe(escritorio), "{\"papel\": \"MEMBRO\"}")).hasStatus(409);
        assertThat(alterar(ComUsuarioLogado.adminDe(escritorio), "{\"ativo\": false}")).hasStatus(409);
    }

    @Test
    void oAdminNaoMexeEmUsuarioDeOutroEscritorio() {
        UUID outro = UUID.randomUUID();
        jdbc.sql("INSERT INTO escritorio (id, nome) VALUES (?, 'Outro')").param(outro).update();
        UUID deFora = ComUsuarioLogado.cadastrar(jdbc, outro, "de-fora@outro.adv.br", Papel.MEMBRO);

        assertThat(alterar(deFora, "{\"ativo\": false}")).hasStatus(404);
        assertThat(jdbc.sql("SELECT ativo FROM usuario WHERE id = ?").param(deFora).query(Boolean.class).single()).isTrue();
        assertThat(mvc.get().uri("/api/usuarios").with(comoAdmin)).hasStatusOk().bodyJson()
                .hasPathSatisfying("$", v -> v.assertThat().asArray().hasSize(1));
    }

    @Test
    void oDesativadoPerdeASessaoNaHoraENaoEntraMais() {
        UUID membro = ComUsuarioLogado.cadastrar(jdbc, escritorio, "membro@silva.adv.br", Papel.MEMBRO);
        MockHttpSession sessao = sessaoDe("membro@silva.adv.br", "x");
        assertThat(mvc.get().uri("/api/calculos/salvos").session(sessao)).hasStatusOk();

        assertThat(alterar(membro, "{\"ativo\": false}")).hasStatusOk();

        assertThat(mvc.get().uri("/api/calculos/salvos").session(sessao)).hasStatus(401);
        assertThat(entrar("membro@silva.adv.br", "x")).hasStatus(401);
    }

    @Test
    void oRebaixadoPerdeAGestaoNoPedidoSeguinte() {
        UUID colega = ComUsuarioLogado.cadastrar(jdbc, escritorio, "colega@silva.adv.br", Papel.ADMIN);
        MockHttpSession sessao = sessaoDe("colega@silva.adv.br", "x");
        assertThat(mvc.get().uri("/api/usuarios").session(sessao)).hasStatusOk();

        assertThat(alterar(colega, "{\"papel\": \"MEMBRO\"}")).hasStatusOk();

        assertThat(mvc.get().uri("/api/usuarios").session(sessao)).hasStatus(403);
        assertThat(mvc.get().uri("/api/sessao").session(sessao)).hasStatusOk().bodyJson()
                .hasPathSatisfying("$.papel", v -> v.assertThat().isEqualTo("MEMBRO"));
    }

    /** Dois admins se rebaixando ao mesmo tempo: o segundo a gravar já não é admin e é recusado. */
    @Test
    void quemDeixouDeSerAdminAntesDeGravarNaoMudaNinguem() {
        UUID colega = ComUsuarioLogado.cadastrar(jdbc, escritorio, "colega@silva.adv.br", Papel.ADMIN);
        UsuarioAutenticado admin = new UsuarioAutenticado(ComUsuarioLogado.adminDe(escritorio), escritorio,
                "admin@silva.adv.br", Papel.ADMIN, null);
        UsuarioAutenticado colegaAindaComoAdmin = new UsuarioAutenticado(colega, escritorio, "colega@silva.adv.br",
                Papel.ADMIN, null);

        gestao.alterar(admin, colega, Papel.MEMBRO, null);

        assertThatExceptionOfType(GestaoDeUsuarios.SemPermissaoException.class)
                .isThrownBy(() -> gestao.alterar(colegaAindaComoAdmin, admin.id(), Papel.MEMBRO, null));
        assertThat(jdbc.sql("SELECT papel FROM usuario WHERE id = ?").param(admin.id()).query(String.class).single())
                .isEqualTo("ADMIN");
    }
}
