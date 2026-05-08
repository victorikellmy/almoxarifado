package com.fundacao.aualmoxarifado.domain;

/**
 * RF14/RF15 - Status do ciclo de vida de uma Compra.
 *
 * AGUARDANDO_COMPRA  : pré-compra lançada; equipe de compras ainda não recebeu
 *                      mercadoria nem nota fiscal.
 * COMPRA_REALIZADA   : mercadoria recebida, NF anexada e baixa efetuada
 *                      (estoque incrementado ou repasse ao setor registrado).
 * CANCELADA          : a pré-compra foi cancelada antes da baixa.
 */
public enum StatusCompra {
    AGUARDANDO_COMPRA,
    COMPRA_REALIZADA,
    CANCELADA
}
