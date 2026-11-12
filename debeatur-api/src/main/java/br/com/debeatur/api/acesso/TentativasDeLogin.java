package br.com.debeatur.api.acesso;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import java.net.Inet6Address;
import java.net.InetAddress;
import java.net.UnknownHostException;
import java.text.Normalizer;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Limita as tentativas de login (#69): {@value #FALHAS} falhas em {@link #JANELA} bloqueiam
 * novas tentativas por {@link #BLOQUEIO}, contadas por e-mail (contra quem testa senhas numa
 * conta) e por IP (contra quem testa uma senha em várias contas). IPv6 conta pelo prefixo /64,
 * que é o que um cliente doméstico recebe inteiro.
 *
 * <p>Cada pedido {@linkplain #reservar reserva} a tentativa antes de conferir a senha, e a
 * reserva conta como falha até o resultado sair: sem isso, pedidos em paralelo passariam todos
 * pela verificação antes de a primeira falha ser registrada.
 *
 * <p>Fica em memória, como as sessões, enquanto a API roda numa instância só; reiniciar a API
 * zera a contagem. Conta toda falha, exista o e-mail ou não, para que o bloqueio não revele quem
 * tem conta. O acerto zera só as falhas do e-mail: zerar as do IP deixaria quem tem uma conta
 * alternar entre ela e as senhas que testa nas outras.
 *
 * <p>Risco aceito: quem sabe o e-mail de um escritório consegue bloquear o login dele com
 * {@value #FALHAS} pedidos a cada {@link #BLOQUEIO}.
 */
@Component
class TentativasDeLogin {

    static final int FALHAS = 5;
    static final Duration JANELA = Duration.ofMinutes(15);
    static final Duration BLOQUEIO = Duration.ofMinutes(15);

    /** Teto de chaves em memória; cheio, quem ainda não tem registro espera a próxima limpeza. */
    static final int MAXIMO_DE_CHAVES = 100_000;
    private static final Duration INTERVALO_DE_LIMPEZA = Duration.ofMinutes(1);
    /** Cheio de reservas sem bloqueio: os pedidos em andamento decidem em instantes. */
    private static final Duration ESPERA_CURTA = Duration.ofMinutes(1);

    /** As falhas ainda dentro da janela, as reservas em andamento e até quando a chave está bloqueada. */
    private record Registro(List<Instant> falhas, int emAndamento, Instant bloqueadoAte) {

        static final Registro VAZIO = new Registro(List.of(), 0, null);

        boolean bloqueado(Instant agora) {
            return bloqueadoAte != null && bloqueadoAte.isAfter(agora);
        }

        List<Instant> recentes(Instant agora) {
            Instant inicio = agora.minus(JANELA);
            return falhas.stream().filter(falha -> falha.isAfter(inicio)).toList();
        }

        /** Sem nada que ainda valha: o registro pode sair do mapa. */
        boolean expirado(Instant agora) {
            return emAndamento == 0 && !bloqueado(agora) && recentes(agora).isEmpty();
        }

        boolean cheio(Instant agora) {
            return recentes(agora).size() + emAndamento >= FALHAS;
        }

        Registro reservado() {
            return new Registro(falhas, emAndamento + 1, bloqueadoAte);
        }

        Registro liberado() {
            return new Registro(falhas, Math.max(0, emAndamento - 1), bloqueadoAte);
        }

        /** Uma falha durante o bloqueio não o desfaz nem o estende. */
        Registro comFalha(Instant agora) {
            Registro liberado = liberado();
            if (bloqueado(agora)) {
                return liberado;
            }
            List<Instant> recentes = new ArrayList<>(recentes(agora));
            recentes.add(agora);
            return recentes.size() >= FALHAS
                    ? new Registro(List.of(), liberado.emAndamento(), agora.plus(BLOQUEIO))
                    : new Registro(List.copyOf(recentes), liberado.emAndamento(), null);
        }

        /** O acerto apaga as falhas, mas não um bloqueio que pedidos em paralelo já criaram. */
        Registro comAcerto() {
            return new Registro(List.of(), liberado().emAndamento(), bloqueadoAte);
        }
    }

    /** Pedido recusado enquanto o e-mail ou o IP está bloqueado. */
    static final class Bloqueado extends RuntimeException {

        private final Duration restante;

        Bloqueado(Duration restante) {
            super(null, null, false, false);
            this.restante = restante;
        }

        Duration restante() {
            return restante;
        }
    }

    /**
     * Uma tentativa em andamento. Termina com {@link #falhou()}, {@link #entrou()} ou, se a
     * conferência não chegou a uma resposta (o banco fora do ar), com {@link #close()}.
     */
    final class Tentativa implements AutoCloseable {

        private final List<String> chaves;
        private final String chaveDoEmail;
        private boolean encerrada;

        private Tentativa(List<String> chaves, String chaveDoEmail) {
            this.chaves = chaves;
            this.chaveDoEmail = chaveDoEmail;
        }

        void falhou() {
            if (encerrar()) {
                Instant agora = relogio.instant();
                chaves.forEach(chave -> atualizar(chave, registro -> registro.comFalha(agora)));
            }
        }

        void entrou() {
            if (encerrar()) {
                chaves.forEach(chave -> atualizar(chave,
                        chave.equals(chaveDoEmail) ? Registro::comAcerto : Registro::liberado));
            }
        }

        @Override
        public void close() {
            if (encerrar()) {
                chaves.forEach(TentativasDeLogin.this::liberar);
            }
        }

        private boolean encerrar() {
            boolean antes = encerrada;
            encerrada = true;
            return !antes;
        }
    }

    private final Map<String, Registro> registros = new ConcurrentHashMap<>();
    private final Clock relogio;
    private volatile Instant proximaLimpeza = Instant.MIN;

    @Autowired
    TentativasDeLogin() {
        this(Clock.systemUTC());
    }

    TentativasDeLogin(Clock relogio) {
        this.relogio = relogio;
    }

    /** @throws Bloqueado se o e-mail ou o IP está bloqueado, com o tempo que falta */
    Tentativa reservar(String email, String ip) {
        Instant agora = relogio.instant();
        limparSePreciso(agora);
        String chaveDoEmail = chaveDoEmail(email);
        List<String> chaves = List.of(chaveDoEmail, chaveDoIp(ip));
        List<String> reservadas = new ArrayList<>();
        try {
            for (String chave : chaves) {
                reservar(chave, agora);
                reservadas.add(chave);
            }
        } catch (Bloqueado e) {
            reservadas.forEach(this::liberar);
            throw e;
        }
        return new Tentativa(chaves, chaveDoEmail);
    }

    private void reservar(String chave, Instant agora) {
        Duration[] recusa = new Duration[1];
        registros.compute(chave, (k, registro) -> {
            if (registro == null && registros.size() >= MAXIMO_DE_CHAVES) {
                recusa[0] = ESPERA_CURTA;
                return null;
            }
            Registro atual = registro == null ? Registro.VAZIO : registro;
            if (atual.bloqueado(agora)) {
                recusa[0] = Duration.between(agora, atual.bloqueadoAte());
                return registro;
            }
            if (atual.cheio(agora)) {
                recusa[0] = ESPERA_CURTA;
                return registro;
            }
            return atual.reservado();
        });
        if (recusa[0] != null) {
            throw new Bloqueado(recusa[0]);
        }
    }

    private void liberar(String chave) {
        atualizar(chave, Registro::liberado);
    }

    private void atualizar(String chave, java.util.function.UnaryOperator<Registro> mudanca) {
        Instant agora = relogio.instant();
        registros.computeIfPresent(chave, (k, registro) -> {
            Registro novo = mudanca.apply(registro);
            return novo.expirado(agora) ? null : novo;
        });
    }

    /** Uma passada pelo mapa por minuto, e não uma a cada pedido, nem com ele cheio. */
    private void limparSePreciso(Instant agora) {
        if (agora.isAfter(proximaLimpeza)) {
            proximaLimpeza = agora.plus(INTERVALO_DE_LIMPEZA);
            registros.values().removeIf(registro -> registro.expirado(agora));
        }
    }

    int chavesEmMemoria() {
        return registros.size();
    }

    /**
     * Sem diferenciar maiúsculas, acentos nem espaços nas pontas. Junta mais do que o
     * {@code lower()} do banco: variações do mesmo e-mail caem na mesma contagem, e e-mails
     * diferentes que se juntam aqui só dividem o limite.
     */
    static String chaveDoEmail(String email) {
        String semAcentos = Normalizer.normalize(email.strip(), Normalizer.Form.NFKD).replaceAll("\\p{M}", "");
        return "email:" + semAcentos.toLowerCase(Locale.ROOT).replace('ı', 'i');
    }

    static String chaveDoIp(String ip) {
        try {
            InetAddress endereco = InetAddress.ofLiteral(ip);
            if (endereco instanceof Inet6Address) {
                byte[] bytes = endereco.getAddress();
                Arrays.fill(bytes, 8, 16, (byte) 0);
                return "ip:" + InetAddress.getByAddress(bytes).getHostAddress() + "/64";
            }
            return "ip:" + endereco.getHostAddress();
        } catch (IllegalArgumentException | UnknownHostException e) {
            return "ip:" + ip;
        }
    }
}
