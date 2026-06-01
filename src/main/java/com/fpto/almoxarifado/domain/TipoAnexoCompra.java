package com.fpto.almoxarifado.domain;

/**
 * RF16 - Identifica o papel de cada anexo dentro de uma Compra.
 *
 * SOLICITACAO  : PDF da solicitação física entregue pelo setor (entra na
 *                etapa de pré-compra).
 * NOTA_FISCAL  : PDF da NF emitida pelo fornecedor (entra na baixa).
 */
public enum TipoAnexoCompra {
    SOLICITACAO,
    NOTA_FISCAL
}
