package br.com.debeatur.api.acesso;

import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.SimpleGrantedAuthority;

/**
 * O perfil do usuário no escritório (#5). O admin convida e gerencia os usuários e vê todos os
 * cálculos do escritório; o membro calcula e vê só os cálculos que ele mesmo salvou.
 */
public enum Papel {
    ADMIN,
    MEMBRO;

    /** A autoridade do Spring Security, para o {@code hasRole} de {@link Seguranca}. */
    GrantedAuthority autoridade() {
        return new SimpleGrantedAuthority("ROLE_" + name());
    }
}
