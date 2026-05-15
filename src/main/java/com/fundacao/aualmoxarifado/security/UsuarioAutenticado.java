package com.fundacao.aualmoxarifado.security;

import com.fundacao.aualmoxarifado.domain.Usuario;
import lombok.Getter;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.userdetails.UserDetails;

import java.util.Collection;
import java.util.List;

/**
 * Adapter que expõe um {@link Usuario} JPA como {@link UserDetails} do Spring Security.
 */
@Getter
public class UsuarioAutenticado implements UserDetails {

    private final Usuario usuario;
    private final List<GrantedAuthority> autoridades;

    public UsuarioAutenticado(Usuario usuario) {
        this.usuario = usuario;
        this.autoridades = List.of(
                new SimpleGrantedAuthority("ROLE_" + usuario.getPerfil().name())
        );
    }

    @Override public Collection<? extends GrantedAuthority> getAuthorities() { return autoridades; }
    @Override public String getPassword() { return usuario.getSenhaHash(); }
    @Override public String getUsername() { return usuario.getLogin(); }

    @Override public boolean isAccountNonExpired()     { return true; }
    @Override public boolean isAccountNonLocked()      { return true; }
    @Override public boolean isCredentialsNonExpired() { return true; }
    @Override public boolean isEnabled()               { return usuario.isAtivo(); }

    public Long getId() { return usuario.getId(); }
    public String getNomeCompleto() { return usuario.getNomeCompleto(); }
}
