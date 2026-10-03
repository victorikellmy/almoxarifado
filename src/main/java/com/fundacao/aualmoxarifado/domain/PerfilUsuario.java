package com.fundacao.aualmoxarifado.domain;

/**
 * Perfis de acesso do sistema.
 *
 * ADMIN   — acesso total, incluindo o cadastro de usuários.
 * COMPRAS — setor de Compras/Almoxarifado (chefe e auxiliares): cadastra pré-compras,
 *           registra a decisão da Diretoria, dá baixa e envia bens ao Patrimônio.
 * PADRAO  — uso operacional do almoxarifado (catálogo, movimentações, consulta de compras).
 */
public enum PerfilUsuario {

    ADMIN("Administrador"),
    COMPRAS("Compras/Almoxarifado"),
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
