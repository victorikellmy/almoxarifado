package com.fpto.almoxarifado.domain;

/**
 * RF14 - Tipos de Compra suportados pelo módulo.
 *
 * DIRETA  : aquisição sob demanda específica para repassar diretamente ao
 *           setor solicitante (não fica armazenada no estoque geral).
 * ESTOQUE : compra pré-programada para reabastecer o estoque geral do
 *           almoxarifado; ao dar baixa, o saldo dos materiais é incrementado.
 */
public enum TipoCompra {
    DIRETA,
    ESTOQUE
}
