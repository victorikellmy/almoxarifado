package com.fundacao.aualmoxarifado.domain;

/**
 * Perfis de acesso do sistema.
 *
 * ADMIN  — acesso total, incluindo o cadastro de usuários.
 * PADRAO — uso operacional do almoxarifado (catálogo, movimentações, compras).
 */
public enum PerfilUsuario {

    ADMIN("Administrador"),
    PADRAO("Padrão");

    private final String rotulo;

    PerfilUsuario(String rotulo) {
        this.rotulo = rotulo;
    }

    public String getRotulo() {
        return rotulo;
    }

    /** Nome da authority no Spring Security: ROLE_ADMIN / ROLE_PADRAO. */
    public String getAuthority() {
        return "ROLE_" + name();
    }
}
