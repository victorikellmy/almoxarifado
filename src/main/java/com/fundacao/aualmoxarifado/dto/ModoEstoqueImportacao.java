package com.fundacao.aualmoxarifado.dto;

/**
 * Como o importador deve tratar a coluna "estoque_atual" da planilha.
 *
 * A distinção é importante porque o almoxarifado tem duas necessidades
 * bem diferentes:
 *
 *  - CARGA INICIAL / INVENTÁRIO: a planilha representa a VERDADE do que está
 *    na prateleira hoje. Usa-se {@link #DEFINIR}.
 *
 *  - REABASTECIMENTO RECORRENTE: a planilha representa o que ACABOU DE CHEGAR.
 *    Usa-se {@link #SOMAR}, que gera movimentação de ENTRADA e preserva o
 *    histórico (RF13/RN05).
 */
public enum ModoEstoqueImportacao {

    /**
     * Soma a quantidade da planilha ao saldo existente e registra uma
     * movimentação de ENTRADA para cada material — o histórico fica auditável
     * (RF13/RN05). É o modo padrão por ser o mais seguro.
     */
    SOMAR("Somar ao saldo (gera entrada)"),

    /**
     * Grava o saldo exatamente como está na planilha, sobrescrevendo o valor
     * anterior. NÃO gera movimentação — use para carga inicial ou correção de
     * inventário, quando o histórico anterior não se aplica.
     */
    DEFINIR("Definir saldo exato (inventário)"),

    /** Ignora a coluna de estoque: importa/atualiza apenas o cadastro. */
    IGNORAR("Não alterar o estoque");

    private final String rotulo;

    ModoEstoqueImportacao(String rotulo) {
        this.rotulo = rotulo;
    }

    public String getRotulo() {
        return rotulo;
    }
}
