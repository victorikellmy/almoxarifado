package com.fundacao.aualmoxarifado.domain;

/**
 * Decisão da Diretoria sobre uma pré-compra, registrada no sistema pelo setor de
 * Compras/Almoxarifado (chefe ou auxiliares) depois que a solicitação é apreciada.
 *
 * A baixa (recebimento) de uma compra só é permitida quando AUTORIZADA.
 * NAO_AUTORIZADA cancela a pré-compra, guardando o parecer.
 */
public enum AutorizacaoDiretoria {
    PENDENTE("Aguardando diretoria"),
    AUTORIZADA("Autorizada pela diretoria"),
    NAO_AUTORIZADA("Não autorizada");

    private final String rotulo;

    AutorizacaoDiretoria(String rotulo) { this.rotulo = rotulo; }

    public String getRotulo() { return rotulo; }
}
