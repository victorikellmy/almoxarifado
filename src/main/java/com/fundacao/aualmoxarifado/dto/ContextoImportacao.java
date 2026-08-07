package com.fundacao.aualmoxarifado.dto;

import com.fundacao.aualmoxarifado.domain.Area;
import com.fundacao.aualmoxarifado.domain.Subcategoria;
import lombok.Getter;
import lombok.RequiredArgsConstructor;

import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

/**
 * Estado compartilhado por TODAS as linhas de uma mesma importação.
 *
 * Existe por dois motivos:
 *
 *  1. SIMULAÇÃO — como nada é gravado, a linha 2 não encontraria no banco a
 *     área que a linha 1 "criaria". Guardamos aqui as entidades ainda
 *     transientes para que a prévia seja coerente (e para não repetir o aviso
 *     "área será criada" cinquenta vezes).
 *
 *  2. DUPLICATAS DENTRO DO PRÓPRIO ARQUIVO — duas linhas com o mesmo material
 *     geram um aviso, em vez de o usuário descobrir depois que o estoque foi
 *     somado duas vezes.
 */
@Getter
@RequiredArgsConstructor
public class ContextoImportacao {

    private final boolean simular;
    private final boolean criarAusentes;
    private final ModoEstoqueImportacao modoEstoque;

    /** Áreas "criadas" durante uma simulação, indexadas pela chave normalizada. */
    private final Map<String, Area> areasPendentes = new HashMap<>();

    /** Subcategorias "criadas" durante uma simulação: chave = areaChave + "|" + subChave. */
    private final Map<String, Subcategoria> subcategoriasPendentes = new HashMap<>();

    /** Siglas já reservadas nesta execução — impede duas áreas novas com a mesma sigla. */
    private final Set<String> siglasAreaReservadas = new HashSet<>();
    private final Set<String> siglasSubcategoriaReservadas = new HashSet<>();

    /** Chaves de material já processadas, para detectar repetição no arquivo. */
    private final Set<String> materiaisVistos = new HashSet<>();

    /** @return true se esta chave já apareceu antes no mesmo arquivo. */
    public boolean registrarEDetectarDuplicata(String chave) {
        return !materiaisVistos.add(chave);
    }
}
