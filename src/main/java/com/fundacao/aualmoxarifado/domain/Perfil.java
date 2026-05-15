package com.fundacao.aualmoxarifado.domain;

/**
 * Perfis de acesso (RBAC). Consumidos pelo Spring Security
 * no formato {@code ROLE_ADMINISTRADOR} / {@code ROLE_OPERADOR}.
 */
public enum Perfil {
    /** Acesso total: cadastrar, editar, movimentar, aprovar, excluir, gerir usuários. */
    ADMINISTRADOR,

    /** Acesso restrito: registrar entradas/saídas; NÃO pode excluir nem gerir usuários. */
    OPERADOR
}
