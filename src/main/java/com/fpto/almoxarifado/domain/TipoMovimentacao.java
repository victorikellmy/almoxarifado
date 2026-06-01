package com.fpto.almoxarifado.domain;

/**
 * Tipos possíveis de movimentação de material.
 *
 * ENTRADA       : abastecimento do estoque geral (RF13).
 *                 INCREMENTA o saldo do material.
 *
 * SAIDA         : retirada do estoque para um setor (RF06).
 *                 Após aprovação/entrega, DECREMENTA o saldo do material (RN04).
 *
 * COMPRA_DIRETA : compra adquirida sob demanda específica para um setor (RF14/RN10).
 *                 NÃO PASSA PELO ESTOQUE GERAL — não incrementa nem decrementa o
 *                 saldo. Serve apenas como registro do que foi comprado e
 *                 entregue diretamente ao setor solicitante. Aparece no RF10
 *                 (consumo por setor) porque, do ponto de vista do setor, a
 *                 mercadoria foi consumida via almoxarifado.
 */
public enum TipoMovimentacao {
    ENTRADA,
    SAIDA,
    COMPRA_DIRETA
}
