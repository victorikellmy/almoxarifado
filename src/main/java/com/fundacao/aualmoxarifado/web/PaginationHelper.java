package com.fundacao.aualmoxarifado.web;

import jakarta.servlet.http.HttpServletRequest;
import org.springframework.stereotype.Component;

import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.Map;

/**
 * Helper para o fragmento {@code fragments/pagination.html}.
 *
 * <p>Lê os query params da requisição atual, descarta {@code page} e
 * {@code size}, e devolve o restante como query string já codificada. O
 * fragmento usa esse pedaço para reconstruir links de página preservando
 * os filtros que o usuário aplicou.</p>
 *
 * <p>Exposto a Thymeleaf via {@code ${@paginationHelper.extraQueryString()}}.</p>
 */
@Component("paginationHelper")
public class PaginationHelper {

    /**
     * Devolve algo como {@code "&tipo=SAIDA&status=APROVADO"} (com & no
     * início) — pronto pra concatenar após {@code ?page=X&size=Y}.
     * Vazio se não houver outros parâmetros.
     */
    public String extraQueryString(HttpServletRequest request) {
        if (request == null) return "";
        Map<String, String[]> params = request.getParameterMap();
        if (params.isEmpty()) return "";

        StringBuilder sb = new StringBuilder();
        params.forEach((key, values) -> {
            if ("page".equals(key) || "size".equals(key)) return;
            if (values == null) return;
            for (String value : values) {
                if (value == null || value.isBlank()) continue;
                sb.append('&')
                  .append(URLEncoder.encode(key, StandardCharsets.UTF_8))
                  .append('=')
                  .append(URLEncoder.encode(value, StandardCharsets.UTF_8));
            }
        });
        return sb.toString();
    }
}
