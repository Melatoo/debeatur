package br.com.debeatur.api.acesso;

import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.mail.MailException;
import org.springframework.mail.SimpleMailMessage;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.stereotype.Component;

import java.time.Instant;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;

/**
 * O e-mail do convite, pelo SMTP de {@code DEBEATUR_SMTP_*}. O link aponta para
 * {@code DEBEATUR_ENDERECO} e leva o token no fragmento ({@code #}), que o navegador não manda
 * ao servidor: assim o token não aparece no log de acesso do nginx ao abrir a página.
 */
@Component
class EnvioDeConvites {

    private static final DateTimeFormatter DATA_E_HORA = DateTimeFormatter.ofPattern("dd/MM/yyyy 'às' HH:mm")
            .withZone(ZoneId.of("America/Sao_Paulo"));

    private final ObjectProvider<JavaMailSender> correio;
    private final String servidor;
    private final String remetente;
    private final String endereco;

    EnvioDeConvites(ObjectProvider<JavaMailSender> correio,
                    @Value("${spring.mail.host:}") String servidor,
                    @Value("${debeatur.convite.remetente:}") String remetente,
                    @Value("${debeatur.endereco}") String endereco) {
        this.correio = correio;
        this.servidor = servidor;
        this.remetente = remetente;
        this.endereco = endereco.replaceAll("/+$", "");
    }

    /**
     * @throws EnvioIndisponivelException se o SMTP ou o remetente não estão configurados
     * @throws EnvioFalhouException       se o servidor de e-mail recusou ou não respondeu
     */
    void enviar(String para, String escritorio, String convidadoPor, String token, Instant expiraEm) {
        JavaMailSender enviador = correio.getIfAvailable();
        if (servidor.isBlank() || remetente.isBlank() || enviador == null) {
            throw new EnvioIndisponivelException();
        }
        SimpleMailMessage mensagem = new SimpleMailMessage();
        mensagem.setFrom(remetente);
        mensagem.setTo(para);
        mensagem.setSubject("Convite para o Debeatur");
        mensagem.setText("""
                %s convidou você para o escritório %s no Debeatur.

                Para criar a sua senha e entrar, abra o link abaixo até %s:

                %s

                Se você não esperava este convite, ignore este e-mail.
                """.formatted(convidadoPor, escritorio, DATA_E_HORA.format(expiraEm), link(token)));
        try {
            enviador.send(mensagem);
        } catch (MailException e) {
            throw new EnvioFalhouException(e);
        }
    }

    String link(String token) {
        return endereco + "/convite#" + token;
    }

    static class EnvioIndisponivelException extends RuntimeException {

        EnvioIndisponivelException() {
            super("O envio de e-mail não está configurado. Defina DEBEATUR_SMTP_HOST e DEBEATUR_EMAIL_REMETENTE.");
        }
    }

    static class EnvioFalhouException extends RuntimeException {

        EnvioFalhouException(Throwable causa) {
            super("Não foi possível enviar o convite agora. Tente de novo em instantes.", causa);
        }
    }
}
