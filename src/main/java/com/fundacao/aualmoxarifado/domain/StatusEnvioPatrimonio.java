package com.fundacao.aualmoxarifado.domain;

/** Situação do envio de um recebimento para o sistema de Patrimônio (outbox). */
public enum StatusEnvioPatrimonio {
    PENDENTE("Aguardando envio"),
    ENVIADO("Enviado ao Patrimônio"),
    ERRO("Falha no envio (será reenviado)");

    private final String rotulo;

    StatusEnvioPatrimonio(String rotulo) { this.rotulo = rotulo; }

    public String getRotulo() { return rotulo; }
}
