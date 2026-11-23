package br.com.debeatur.api.acesso;

import org.springframework.security.core.CredentialsContainer;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.userdetails.UserDetails;

import java.io.Serial;
import java.util.Collection;
import java.util.List;
import java.util.UUID;

/**
 * O usuário da sessão. Todo dado do escritório é lido e gravado pelo {@link #escritorio()} dele,
 * e o {@link #papel()} decide o que ele vê e gerencia ({@link Papel}). O hash da senha só existe
 * até a autenticação: o {@code ProviderManager} o apaga antes de a sessão guardar o usuário.
 */
public final class UsuarioAutenticado implements UserDetails, CredentialsContainer {

    @Serial
    private static final long serialVersionUID = 2L;

    private final UUID id;
    private final UUID escritorio;
    private final String email;
    private final Papel papel;
    private String senha;

    public UsuarioAutenticado(UUID id, UUID escritorio, String email, Papel papel, String senha) {
        this.id = id;
        this.escritorio = escritorio;
        this.email = email;
        this.papel = papel;
        this.senha = senha;
    }

    public UUID id() {
        return id;
    }

    public UUID escritorio() {
        return escritorio;
    }

    public String email() {
        return email;
    }

    public Papel papel() {
        return papel;
    }

    public boolean admin() {
        return papel == Papel.ADMIN;
    }

    /** O mesmo usuário com outro papel, para a sessão acompanhar a mudança feita pelo admin. */
    UsuarioAutenticado comPapel(Papel novo) {
        return new UsuarioAutenticado(id, escritorio, email, novo, null);
    }

    @Override
    public String getUsername() {
        return email;
    }

    @Override
    public String getPassword() {
        return senha;
    }

    @Override
    public Collection<? extends GrantedAuthority> getAuthorities() {
        return List.of(papel.autoridade());
    }

    @Override
    public void eraseCredentials() {
        senha = null;
    }
}
