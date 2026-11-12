package br.com.debeatur.api.acesso;

import org.junit.jupiter.api.Test;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/** A contagem e o tempo do bloqueio, com um relógio que o teste avança. */
class TentativasDeLoginTest {

    private static final String EMAIL = "ana@escritorio.adv.br";
    private static final String IP = "203.0.113.7";

    private static final class Relogio extends Clock {

        private Instant agora = Instant.parse("2026-11-12T12:00:00Z");

        void avancar(Duration tempo) {
            agora = agora.plus(tempo);
        }

        @Override
        public ZoneId getZone() {
            return ZoneOffset.UTC;
        }

        @Override
        public Clock withZone(ZoneId zona) {
            throw new UnsupportedOperationException();
        }

        @Override
        public Instant instant() {
            return agora;
        }
    }

    private final Relogio relogio = new Relogio();
    private final TentativasDeLogin tentativas = new TentativasDeLogin(relogio);

    private void falhar(int vezes, String email, String ip) {
        for (int i = 0; i < vezes; i++) {
            tentativas.reservar(email, ip).falhou();
        }
    }

    private void assertBloqueado(String email, String ip, Duration restante) {
        assertThatThrownBy(() -> tentativas.reservar(email, ip)).isInstanceOfSatisfying(TentativasDeLogin.Bloqueado.class,
                bloqueado -> assertThat(bloqueado.restante()).isEqualTo(restante));
    }

    /** Liberado: reserva e devolve a vaga sem contar falha. */
    private void assertLiberado(String email, String ip) {
        assertThatCode(() -> tentativas.reservar(email, ip).close()).doesNotThrowAnyException();
    }

    @Test
    void aQuintaFalhaBloqueiaOEmailEOIpPorQuinzeMinutos() {
        falhar(4, EMAIL, IP);
        assertLiberado(EMAIL, IP);

        falhar(1, EMAIL, IP);

        assertBloqueado(EMAIL, "198.51.100.1", Duration.ofMinutes(15));
        assertBloqueado("outra@escritorio.adv.br", IP, Duration.ofMinutes(15));
        assertLiberado("outra@escritorio.adv.br", "198.51.100.1");
    }

    @Test
    void oBloqueioAcabaDepoisDeQuinzeMinutos() {
        falhar(5, EMAIL, IP);
        relogio.avancar(Duration.ofMinutes(14));
        assertBloqueado(EMAIL, IP, Duration.ofMinutes(1));

        relogio.avancar(Duration.ofMinutes(1));

        assertLiberado(EMAIL, IP);
        // a contagem recomeça do zero
        falhar(4, EMAIL, IP);
        assertLiberado(EMAIL, IP);
    }

    @Test
    void falhasForaDaJanelaNaoContam() {
        falhar(4, EMAIL, IP);
        relogio.avancar(Duration.ofMinutes(15));

        falhar(1, EMAIL, IP);

        assertLiberado(EMAIL, IP);
    }

    /** Sem a reserva, os seis passariam pela verificação antes de a primeira falha ser registrada. */
    @Test
    void pedidosEmParaleloContamAntesDoResultado() {
        List<TentativasDeLogin.Tentativa> emAndamento = new ArrayList<>();
        for (int i = 1; i <= TentativasDeLogin.FALHAS; i++) {
            emAndamento.add(tentativas.reservar(EMAIL, "198.51.100." + i));
        }

        assertThatThrownBy(() -> tentativas.reservar(EMAIL, "198.51.100.99"))
                .isInstanceOf(TentativasDeLogin.Bloqueado.class);

        emAndamento.forEach(TentativasDeLogin.Tentativa::falhou);
        assertBloqueado(EMAIL, "198.51.100.99", Duration.ofMinutes(15));
    }

    @Test
    void reservaRecusadaPeloIpDevolveAVagaDoEmail() {
        falhar(5, "outra@escritorio.adv.br", IP);
        for (int i = 0; i < TentativasDeLogin.FALHAS; i++) {
            assertBloqueado(EMAIL, IP, Duration.ofMinutes(15));
        }

        assertLiberado(EMAIL, "198.51.100.1");
    }

    /** O banco fora do ar não é senha errada: a reserva volta sem contar. */
    @Test
    void tentativaSemResultadoNaoConta() {
        for (int i = 0; i < TentativasDeLogin.FALHAS + 1; i++) {
            tentativas.reservar(EMAIL, IP).close();
        }

        assertLiberado(EMAIL, IP);
        assertThat(tentativas.chavesEmMemoria()).isZero();
    }

    @Test
    void depoisDeFalharOuEntrarOCloseNaoMexeEmNada() {
        TentativasDeLogin.Tentativa tentativa = tentativas.reservar(EMAIL, IP);
        tentativa.falhou();
        tentativa.close();
        falhar(4, EMAIL, IP);

        assertBloqueado(EMAIL, IP, Duration.ofMinutes(15));
    }

    @Test
    void oEmailContaSemDiferenciarMaiusculasAcentosNemEspacos() {
        falhar(2, EMAIL, "198.51.100.1");
        falhar(1, " Ana@Escritorio.ADV.br ", "198.51.100.2");
        falhar(1, "ana@escrİtorio.adv.br", "198.51.100.3");
        falhar(1, "Âna@escritório.adv.br", "198.51.100.4");

        assertBloqueado("ANA@escritorio.adv.br", "198.51.100.5", Duration.ofMinutes(15));
    }

    @Test
    void ipv6ContaPeloPrefixo64() {
        for (int i = 1; i <= TentativasDeLogin.FALHAS; i++) {
            falhar(1, "conta" + i + "@escritorio.adv.br", "2001:db8:1:2::" + i);
        }

        assertBloqueado(EMAIL, "2001:db8:1:2:ffff:ffff:ffff:ffff", Duration.ofMinutes(15));
        assertLiberado(EMAIL, "2001:db8:1:3::1");
    }

    @Test
    void entrarZeraAsFalhasDoEmailMasNaoAsDoIp() {
        falhar(4, EMAIL, IP);
        tentativas.reservar(EMAIL, IP).entrou();
        falhar(1, EMAIL, "198.51.100.1");
        assertLiberado(EMAIL, "198.51.100.1");

        falhar(1, "outra@escritorio.adv.br", IP);

        assertBloqueado("terceira@escritorio.adv.br", IP, Duration.ofMinutes(15));
    }

    @Test
    void entrarNaoDesfazOBloqueioDoIp() {
        falhar(4, "outra@escritorio.adv.br", IP);
        tentativas.reservar(EMAIL, "198.51.100.1").entrou();
        falhar(1, "outra@escritorio.adv.br", IP);

        assertBloqueado(EMAIL, IP, Duration.ofMinutes(15));
    }

    @Test
    void entrarSemFalhasNaoGuardaNada() {
        tentativas.reservar(EMAIL, IP).entrou();

        assertThat(tentativas.chavesEmMemoria()).isZero();
    }

    @Test
    void aLimpezaTiraOQueJaExpirou() {
        falhar(1, EMAIL, IP);
        assertThat(tentativas.chavesEmMemoria()).isEqualTo(2);

        relogio.avancar(Duration.ofMinutes(16));
        assertLiberado("outra@escritorio.adv.br", "198.51.100.1");

        assertThat(tentativas.chavesEmMemoria()).isZero();
    }

    /** Cheio, recusa quem ainda não tem registro em vez de crescer sem limite. */
    @Test
    void naoPassaDoTetoDeChaves() {
        for (int i = 0; i < TentativasDeLogin.MAXIMO_DE_CHAVES / 2; i++) {
            falhar(1, "conta" + i + "@escritorio.adv.br", "10." + (i >> 16) + "." + ((i >> 8) & 255) + "." + (i & 255));
        }

        assertThatThrownBy(() -> tentativas.reservar(EMAIL, IP)).isInstanceOf(TentativasDeLogin.Bloqueado.class);
        assertThat(tentativas.chavesEmMemoria()).isEqualTo(TentativasDeLogin.MAXIMO_DE_CHAVES);

        relogio.avancar(Duration.ofMinutes(16));
        assertLiberado(EMAIL, IP);
    }
}
