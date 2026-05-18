package com.fundacao.aualmoxarifado.domain;

/**
 * Catálogo de ações auditáveis no fluxo de estoque.
 *
 * <p>Mantido como enum (e não String livre) para evitar drift entre o que é
 * gravado e o que aparece nos relatórios. Acrescentar valores aqui requer
 * apenas uma alteração — o banco armazena como String via {@code @Enumerated(STRING)}.</p>
 */
public enum AcaoAuditoria {

    /** Registro de nova saída de material via bipagem (mobile) ou web. */
    REGISTRAR_SAIDA,

    /** Mudança de status da movimentação (aprovação, rejeição, entrega). */
    ALTERAR_STATUS_MOVIMENTACAO,

    /** Registro de entrada de material (NF). */
    REGISTRAR_ENTRADA,

    /** Registro de compra direta para setor. */
    REGISTRAR_COMPRA_DIRETA
}
