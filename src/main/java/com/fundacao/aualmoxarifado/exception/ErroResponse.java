package com.fundacao.aualmoxarifado.exception;

import java.time.LocalDateTime;
import java.util.List;

/**
 * Payload padronizado de erro. Segue estilo RFC 7807 (problem details) simplificado.
 */
public record ErroResponse(
        LocalDateTime timestamp,
        int status,
        String erro,
        String mensagem,
        String path,
        List<String> detalhes
) {
    public static ErroResponse of(int status, String erro, String mensagem, String path) {
        return new ErroResponse(LocalDateTime.now(), status, erro, mensagem, path, List.of());
    }

    public static ErroResponse of(int status, String erro, String mensagem, String path, List<String> detalhes) {
        return new ErroResponse(LocalDateTime.now(), status, erro, mensagem, path, detalhes);
    }
}
