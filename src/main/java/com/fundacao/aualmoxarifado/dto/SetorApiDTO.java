package com.fundacao.aualmoxarifado.dto;

import com.fundacao.aualmoxarifado.domain.Organizacao;
import com.fundacao.aualmoxarifado.domain.Setor;

/**
 * Shape do setor exposto ao app mobile: só o que o dropdown precisa.
 * {@code codigoCentroCusto} é opcional no domínio e sai como {@code null}
 * mesmo — o contrato do app prevê isso explicitamente.
 *
 * <p>Campos adicionados (compatíveis): {@code organizacao} (FPTO | FA_SAUDE) e
 * {@code nomeCompleto} ("Financeiro — FA-Saúde"), porque as duas organizações têm
 * setores homônimos e o app precisa diferenciá-los no dropdown.</p>
 */
public record SetorApiDTO(Long id, String nome, String codigoCentroCusto,
                          Organizacao organizacao, String nomeCompleto) {

    public static SetorApiDTO from(Setor setor) {
        return new SetorApiDTO(setor.getId(), setor.getNome(), setor.getCodigoCentroCusto(),
                setor.getOrganizacao(), setor.getNomeCompleto());
    }
}
