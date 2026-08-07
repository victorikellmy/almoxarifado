package com.fundacao.aualmoxarifado.dto;

import java.util.List;
import java.util.Map;

/**
 * Colunas reconhecidas pelo importador de materiais (RF12 - carga em massa).
 *
 * Cada coluna tem um nome CANÔNICO (o que aparece na planilha-modelo) e uma
 * lista de APELIDOS aceitos. Isso permite que o usuário suba a planilha que
 * ele já usa no dia a dia — "QTD", "Quantidade", "Saldo" e "Estoque Atual"
 * caem todos em {@link #ESTOQUE_ATUAL} — sem precisar renomear colunas à mão.
 *
 * O casamento é feito sobre o cabeçalho JÁ NORMALIZADO
 * (ver {@code LeitorPlanilhaService.normalizarCabecalho}): minúsculas, sem
 * acentos e com separadores virando "_". Ou seja, "Valor Unitário (R$)"
 * chega aqui como "valor_unitario_r".
 */
public enum ColunaMaterial {

    NOME("nome",
            "nome", "material", "produto", "item", "descricao", "nome_material", "nome_do_material"),

    AREA("area",
            "area", "area_nome", "nome_area", "categoria", "grupo"),

    AREA_SIGLA("area_sigla",
            "area_sigla", "sigla_area", "sigla_da_area"),

    SUBCATEGORIA("subcategoria",
            "subcategoria", "sub_categoria", "subcategoria_nome", "nome_subcategoria", "sub", "subgrupo"),

    SUBCATEGORIA_SIGLA("subcategoria_sigla",
            "subcategoria_sigla", "sigla_subcategoria", "sub_sigla", "sigla_sub"),

    UNIDADE("unidade",
            "unidade", "unidade_medida", "unidade_de_medida", "und", "un", "medida"),

    ESTOQUE_ATUAL("estoque_atual",
            "estoque_atual", "estoque", "quantidade", "qtd", "qtde", "saldo"),

    ESTOQUE_MINIMO("estoque_minimo",
            "estoque_minimo", "minimo", "estoque_min", "qtd_minima", "quantidade_minima", "ponto_reposicao"),

    VALOR_UNITARIO("valor_unitario",
            "valor_unitario", "valor", "preco", "preco_unitario", "custo", "valor_unitario_r"),

    SKU("sku",
            "sku", "codigo_sku", "codigo", "cod");

    private final String canonico;
    private final List<String> apelidos;

    ColunaMaterial(String canonico, String... apelidos) {
        this.canonico = canonico;
        this.apelidos = List.of(apelidos);
    }

    /** Nome usado na planilha-modelo gerada pelo sistema. */
    public String getCanonico() {
        return canonico;
    }

    public List<String> getApelidos() {
        return apelidos;
    }

    /**
     * Extrai o valor desta coluna de uma linha já normalizada.
     * Percorre os apelidos em ordem e devolve o primeiro preenchido —
     * assim uma planilha com "codigo" e "sku" ao mesmo tempo não quebra.
     *
     * @return valor sem espaços nas pontas, ou {@code null} se ausente/vazio
     */
    public String valorDe(Map<String, String> linhaNormalizada) {
        for (String apelido : apelidos) {
            String v = linhaNormalizada.get(apelido);
            if (v != null && !v.isBlank()) {
                return v.trim();
            }
        }
        return null;
    }

    /** Indica se o cabeçalho informado corresponde a esta coluna. */
    public boolean reconhece(String cabecalhoNormalizado) {
        return apelidos.contains(cabecalhoNormalizado);
    }
}
