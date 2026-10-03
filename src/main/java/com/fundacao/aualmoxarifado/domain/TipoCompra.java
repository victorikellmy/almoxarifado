package com.fundacao.aualmoxarifado.domain;

/**
 * RF14 - Tipos de Compra suportados pelo módulo.
 *
 * DIRETA  : aquisição sob demanda específica para repassar diretamente ao
 *           setor solicitante (não fica armazenada no estoque geral).
 * ESTOQUE : compra pré-programada para reabastecer o estoque geral do
 *           almoxarifado; ao dar baixa, o saldo dos materiais é incrementado.
 * PATRIMONIAL : bem permanente (equipamento, mobiliário...) comprado para uma
 *           unidade/setor. NÃO movimenta estoque nem consumo: na baixa, com a NF
 *           anexada, o recebimento é enviado ao sistema de Patrimônio para o bem
 *           ser tombado. Os itens podem ser descritos livremente (sem material do
 *           catálogo de consumo).
 */
public enum TipoCompra {
    DIRETA("Direta"),
    ESTOQUE("Estoque"),
    PATRIMONIAL("Patrimonial");

    private final String rotulo;

    TipoCompra(String rotulo) { this.rotulo = rotulo; }

    public String getRotulo() { return rotulo; }
}
