package br.com.debeatur.api.acesso;

import org.springframework.security.core.CredentialsContainer;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.userdetails.UserDetails;

import java.io.Serial;
import java.util.Collection;
import java.util.List;
import java.util.UUID;

/**
 * O usuário da sessão. Todo dado do escritório é lido e gravado pelo {@link #escritorio()} dele;
 * no MVP não há papéis nem permissões (#5). O hash da senha só existe até a autenticação: o
 * {@code ProviderManager} o apaga antes de a sessão guardar o usuário.
 */
public final class UsuarioAutenticado implements UserDetails, CredentialsContainer {

    @Serial
    private static final long serialVersionUID = 1L;

    private final UUID id;
    private final UUID escritorio;
    private final String email;
    private String senha;

    public UsuarioAutenticado(UUID id, UUID escritorio, String email, String senha) {
        this.id = id;
        this.escritorio = escritorio;
        this.email = email;
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
        return List.of();
    }

    @Override
    public void eraseCredentials() {
        senha = null;
    }
}
