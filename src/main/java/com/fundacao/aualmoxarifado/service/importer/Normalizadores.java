package com.fundacao.aualmoxarifado.service.importer;

import java.text.Normalizer;

/**
 * Pequenas funções de normalização usadas pelo importador
 * (nomes de categoria, SKUs, comparações case-insensitive).
 */
public final class Normalizadores {

    private Normalizadores() {}

    /** Maiúsculas, sem acentos, espaços colapsados. */
    public static String upperSemAcento(String s) {
        if (s == null) return null;
        String semAcento = Normalizer.normalize(s, Normalizer.Form.NFD)
                .replaceAll("\\p{InCombiningDiacriticalMarks}+", "");
        return semAcento.trim().toUpperCase().replaceAll("\\s+", " ");
    }

    public static String normalSku(String s) {
        if (s == null) return null;
        return s.trim().toUpperCase().replaceAll("\\s+", "");
    }
}
