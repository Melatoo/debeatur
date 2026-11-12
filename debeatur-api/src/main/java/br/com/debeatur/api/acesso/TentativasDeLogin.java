package br.com.debeatur.api.acesso;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Limita as tentativas de login (#69): {@value #FALHAS} falhas em {@link #JANELA} bloqueiam
 * novas tentativas por {@link #BLOQUEIO}, contadas por e-mail (contra quem testa senhas numa
 * conta) e por IP (contra quem testa uma senha em várias contas).
 *
 * <p>Fica em memória, como as sessões, enquanto a API roda numa instância só. Conta toda
 * falha, exista o e-mail ou não, para que o bloqueio não revele quem tem conta. O acerto zera
 * só a contagem do e-mail: zerar a do IP deixaria quem tem uma conta alternar entre ela e as
 * senhas que testa nas outras.
 */
@Component
class TentativasDeLogin {

    static final int FALHAS = 5;
    static final Duration JANELA = Duration.ofMinutes(15);
    static final Duration BLOQUEIO = Duration.ofMinutes(15);

    /** As falhas ainda dentro da janela, e até quando a chave está bloqueada. */
    private record Registro(List<Instant> falhas, Instant bloqueadoAte) {

        boolean bloqueado(Instant agora) {
            return bloqueadoAte != null && bloqueadoAte.isAfter(agora);
        }

        boolean expirado(Instant agora) {
            return !bloqueado(agora) && falhas.stream().noneMatch(falha -> falha.isAfter(agora.minus(JANELA)));
        }

        Registro comFalha(Instant agora) {
            List<Instant> recentes = new ArrayList<>(falhas.stream().filter(falha -> falha.isAfter(agora.minus(JANELA))).toList());
            recentes.add(agora);
            return recentes.size() >= FALHAS
                    ? new Registro(List.of(), agora.plus(BLOQUEIO))
                    : new Registro(List.copyOf(recentes), null);
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

    private final Map<String, Registro> registros = new ConcurrentHashMap<>();
    private final Clock relogio;

    @Autowired
    TentativasDeLogin() {
        this(Clock.systemUTC());
    }

    TentativasDeLogin(Clock relogio) {
        this.relogio = relogio;
    }

    /** @throws Bloqueado se o e-mail ou o IP está bloqueado, com o tempo que falta do maior bloqueio */
    void verificar(String email, String ip) {
        Instant agora = relogio.instant();
        Optional<Instant> ate = chaves(email, ip).stream()
                .map(registros::get)
                .filter(registro -> registro != null && registro.bloqueado(agora))
                .map(Registro::bloqueadoAte)
                .max(Instant::compareTo);
        if (ate.isPresent()) {
            throw new Bloqueado(Duration.between(agora, ate.get()));
        }
    }

    void falhou(String email, String ip) {
        Instant agora = relogio.instant();
        registros.values().removeIf(registro -> registro.expirado(agora));
        for (String chave : chaves(email, ip)) {
            registros.compute(chave, (k, registro) ->
                    (registro == null ? new Registro(List.of(), null) : registro).comFalha(agora));
        }
    }

    void entrou(String email) {
        registros.remove(chaveDoEmail(email));
    }

    private static List<String> chaves(String email, String ip) {
        return List.of(chaveDoEmail(email), "ip:" + ip);
    }

    /** O login não diferencia maiúsculas nem espaços nas pontas; a contagem também não. */
    private static String chaveDoEmail(String email) {
        return "email:" + email.strip().toLowerCase(Locale.ROOT);
    }
}
