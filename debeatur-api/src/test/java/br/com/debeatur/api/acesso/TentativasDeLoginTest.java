package br.com.debeatur.api.acesso;

import org.junit.jupiter.api.Test;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneId;
import java.time.ZoneOffset;

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
            tentativas.falhou(email, ip);
        }
    }

    private static void assertBloqueado(Runnable verificacao, Duration restante) {
        assertThatThrownBy(verificacao::run).isInstanceOfSatisfying(TentativasDeLogin.Bloqueado.class,
                bloqueado -> assertThat(bloqueado.restante()).isEqualTo(restante));
    }

    private static void assertLiberado(Runnable verificacao) {
        assertThatCode(verificacao::run).doesNotThrowAnyException();
    }

    @Test
    void aQuintaFalhaBloqueiaOEmailEOIpPorQuinzeMinutos() {
        falhar(4, EMAIL, IP);
        assertLiberado(() -> tentativas.verificar(EMAIL, IP));

        tentativas.falhou(EMAIL, IP);

        assertBloqueado(() -> tentativas.verificar(EMAIL, "198.51.100.1"), Duration.ofMinutes(15));
        assertBloqueado(() -> tentativas.verificar("outra@escritorio.adv.br", IP), Duration.ofMinutes(15));
        assertLiberado(() -> tentativas.verificar("outra@escritorio.adv.br", "198.51.100.1"));
    }

    @Test
    void oBloqueioAcabaDepoisDeQuinzeMinutos() {
        falhar(5, EMAIL, IP);
        relogio.avancar(Duration.ofMinutes(14));
        assertBloqueado(() -> tentativas.verificar(EMAIL, IP), Duration.ofMinutes(1));

        relogio.avancar(Duration.ofMinutes(1));

        assertLiberado(() -> tentativas.verificar(EMAIL, IP));
        // a contagem recomeça do zero
        falhar(4, EMAIL, IP);
        assertLiberado(() -> tentativas.verificar(EMAIL, IP));
    }

    @Test
    void falhasForaDaJanelaNaoContam() {
        falhar(4, EMAIL, IP);
        relogio.avancar(Duration.ofMinutes(15));

        tentativas.falhou(EMAIL, IP);

        assertLiberado(() -> tentativas.verificar(EMAIL, IP));
    }

    @Test
    void oEmailContaSemDiferenciarMaiusculasNemEspacos() {
        falhar(3, EMAIL, "198.51.100.1");
        falhar(2, " Ana@Escritorio.ADV.br ", "198.51.100.2");

        assertBloqueado(() -> tentativas.verificar("ANA@escritorio.adv.br", "198.51.100.3"), Duration.ofMinutes(15));
    }

    @Test
    void entrarZeraAContagemDoEmailMasNaoADoIp() {
        falhar(4, EMAIL, IP);
        tentativas.entrou(EMAIL);
        tentativas.falhou(EMAIL, "198.51.100.1");
        assertLiberado(() -> tentativas.verificar(EMAIL, "198.51.100.1"));

        tentativas.falhou("outra@escritorio.adv.br", IP);

        assertBloqueado(() -> tentativas.verificar("terceira@escritorio.adv.br", IP), Duration.ofMinutes(15));
    }

    @Test
    void entrarNaoDesfazOBloqueioDoIp() {
        falhar(5, "outra@escritorio.adv.br", IP);
        tentativas.entrou("outra@escritorio.adv.br");

        assertBloqueado(() -> tentativas.verificar(EMAIL, IP), Duration.ofMinutes(15));
    }
}
