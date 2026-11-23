package br.com.debeatur.api.acesso;

import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.mail.MailSendException;
import org.springframework.mail.SimpleMailMessage;
import org.springframework.mail.javamail.JavaMailSender;

import java.time.Instant;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatExceptionOfType;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/** O e-mail do convite, com o servidor SMTP simulado. */
class EnvioDeConvitesTest {

    private final JavaMailSender correio = mock(JavaMailSender.class);

    @SuppressWarnings("unchecked")
    private EnvioDeConvites envio(String servidor, String remetente) {
        ObjectProvider<JavaMailSender> provedor = mock(ObjectProvider.class);
        when(provedor.getIfAvailable()).thenReturn(correio);
        return new EnvioDeConvites(provedor, servidor, remetente, "https://debeatur.silva.adv.br/");
    }

    @Test
    void mandaOLinkComOTokenNoFragmentoEOPrazoNoHorarioDeBrasilia() {
        envio("smtp.silva.adv.br", "debeatur@silva.adv.br").enviar("bia@silva.adv.br", "Silva Advogados",
                "ana@silva.adv.br", "abc_123-XYZ", Instant.parse("2026-11-29T15:30:00Z"));

        ArgumentCaptor<SimpleMailMessage> mensagem = ArgumentCaptor.forClass(SimpleMailMessage.class);
        verify(correio).send(mensagem.capture());
        assertThat(mensagem.getValue().getFrom()).isEqualTo("debeatur@silva.adv.br");
        assertThat(mensagem.getValue().getTo()).containsExactly("bia@silva.adv.br");
        assertThat(mensagem.getValue().getText())
                .contains("ana@silva.adv.br convidou você para o escritório Silva Advogados")
                .contains("https://debeatur.silva.adv.br/convite#abc_123-XYZ")
                .contains("29/11/2026 às 12:30");
    }

    @Test
    void semServidorOuRemetenteNaoTentaEnviar() {
        assertThatExceptionOfType(EnvioDeConvites.EnvioIndisponivelException.class)
                .isThrownBy(() -> envio("", "debeatur@silva.adv.br").enviar("bia@silva.adv.br", "S", "a", "t", Instant.now()));
        assertThatExceptionOfType(EnvioDeConvites.EnvioIndisponivelException.class)
                .isThrownBy(() -> envio("smtp.silva.adv.br", "").enviar("bia@silva.adv.br", "S", "a", "t", Instant.now()));
    }

    @Test
    void falhaDoServidorViraEnvioFalhou() {
        doThrow(new MailSendException("recusado")).when(correio).send(any(SimpleMailMessage.class));

        assertThatExceptionOfType(EnvioDeConvites.EnvioFalhouException.class)
                .isThrownBy(() -> envio("smtp.silva.adv.br", "debeatur@silva.adv.br")
                        .enviar("bia@silva.adv.br", "S", "a", "t", Instant.now()));
    }
}
