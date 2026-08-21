package com.fundacao.aualmoxarifado.dto;

import com.fundacao.aualmoxarifado.domain.Setor;

/**
 * Shape do setor exposto ao app mobile: só o que o dropdown precisa.
 * {@code codigoCentroCusto} é opcional no domínio e sai como {@code null}
 * mesmo — o contrato do app prevê isso explicitamente.
 */
public record SetorApiDTO(Long id, String nome, String codigoCentroCusto) {

    public static SetorApiDTO from(Setor setor) {
        return new SetorApiDTO(setor.getId(), setor.getNome(), setor.getCodigoCentroCusto());
    }
}
