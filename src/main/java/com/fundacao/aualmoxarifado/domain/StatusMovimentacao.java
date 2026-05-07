package com.fundacao.aualmoxarifado.domain;

/**
 * RN04 - Hierarquia de aprovação para saídas/movimentações.
 * O estoque só é decrementado quando o status passa para APROVADO ou ENTREGUE.
 */
public enum StatusMovimentacao {
    PENDENTE_APROVACAO,
    APROVADO,
    ENTREGUE,
    REJEITADO
}
