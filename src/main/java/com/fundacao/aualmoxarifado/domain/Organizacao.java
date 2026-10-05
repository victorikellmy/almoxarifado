package com.fundacao.aualmoxarifado.domain;

/**
 * Organização a que um {@link Setor} pertence.
 *
 * FPTO     — Fundação Pró-Tocantins (domínio \\192.168.13.25).
 * FA_SAUDE — FA-Saúde, fundo de saúde (compartilhamento \\192.168.1.25\Setores).
 *
 * As duas têm setores com o mesmo nome (ex.: "Financeiro"); a organização é
 * o que os diferencia em listas, relatórios e no app.
 */
public enum Organizacao {

    FPTO("Fundação Pró-Tocantins", "FPTO"),
    FA_SAUDE("FA-Saúde", "FA-Saúde");

    private final String rotulo;
    private final String sigla;

    Organizacao(String rotulo, String sigla) {
        this.rotulo = rotulo;
        this.sigla = sigla;
    }

    public String getRotulo() {
        return rotulo;
    }

    /** Forma curta para badges e sufixo de nomes ("Financeiro — FA-Saúde"). */
    public String getSigla() {
        return sigla;
    }
}
