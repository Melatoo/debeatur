package br.com.debeatur.api.acesso;

import org.junit.jupiter.api.Test;
import org.springframework.boot.DefaultApplicationArguments;
import org.springframework.security.crypto.factory.PasswordEncoderFactories;
import org.springframework.security.crypto.password.PasswordEncoder;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatIllegalStateException;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/** As regras da subida, sem banco; a criação de ponta a ponta está em {@code SessaoTest}. */
class UsuarioInicialTest {

    private final Usuarios usuarios = mock(Usuarios.class);
    private final PasswordEncoder senhas = PasswordEncoderFactories.createDelegatingPasswordEncoder();

    private void subir(String email, String senha) {
        new UsuarioInicial(usuarios, senhas, email, senha).run(new DefaultApplicationArguments());
    }

    @Test
    void naoRedefineASenhaDeQuemJaExiste() {
        when(usuarios.escritorioTemUsuario(UsuarioInicial.ESCRITORIO_PADRAO)).thenReturn(true);

        subir("ana@escritorio.adv.br", "outra senha qualquer");

        verify(usuarios, never()).inserir(any(), anyString(), anyString());
    }

    @Test
    void semAsVariaveisSobeSemCriarNinguem() {
        assertThatCode(() -> subir("", "")).doesNotThrowAnyException();

        verify(usuarios, never()).inserir(any(), anyString(), anyString());
    }

    @Test
    void recusaSenhaCurta() {
        assertThatIllegalStateException().isThrownBy(() -> subir("ana@escritorio.adv.br", "curta"))
                .withMessageContaining("ao menos 8 caracteres");
    }

    /** 40 letras acentuadas são 80 bytes: o bcrypt recusaria, e a API não subiria com erro obscuro. */
    @Test
    void recusaSenhaAlemDos72BytesDoBcrypt() {
        assertThatIllegalStateException().isThrownBy(() -> subir("ana@escritorio.adv.br", "ç".repeat(40)))
                .withMessageContaining("72 bytes");
        verify(usuarios, never()).inserir(any(), anyString(), anyString());
    }

    @Test
    void aceitaSenhaDeExatos72Bytes() {
        subir("ana@escritorio.adv.br", "a".repeat(72));

        verify(usuarios).inserir(eq(UsuarioInicial.ESCRITORIO_PADRAO), eq("ana@escritorio.adv.br"), anyString());
    }
}
