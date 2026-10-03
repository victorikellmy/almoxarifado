package com.fundacao.aualmoxarifado.service.integracao;

import com.fundacao.aualmoxarifado.dto.integracao.RecebimentoPatrimonioPayload;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatusCode;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientResponseException;

import java.util.Map;

/**
 * Cliente HTTP do Gerenciador Patrimonial.
 *
 * Configuração (variáveis de ambiente):
 * <ul>
 *   <li>{@code PATRIMONIO_URL} — base, ex.: {@code https://patrimonio.fasaudefpto.com.br}</li>
 *   <li>{@code PATRIMONIO_USUARIO} / {@code PATRIMONIO_SENHA} — usuário de integração
 *       (HTTP Basic, mesma autenticação da API {@code /api/**} do Patrimônio)</li>
 * </ul>
 * Sem {@code PATRIMONIO_URL} a integração fica desligada: os recebimentos ficam na
 * fila (outbox) e são enviados assim que a URL for configurada.
 */
@Component
@Slf4j
public class PatrimonioClient {

    public static final String CAMINHO_RECEBIMENTOS = "/api/integracao/almoxarifado/recebimentos";

    private final String baseUrl;
    private final String usuario;
    private final String senha;
    private volatile RestClient client;

    public PatrimonioClient(@Value("${patrimonio.url:}") String baseUrl,
                            @Value("${patrimonio.usuario:}") String usuario,
                            @Value("${patrimonio.senha:}") String senha) {
        this.baseUrl = baseUrl == null ? "" : baseUrl.trim().replaceAll("/+$", "");
        this.usuario = usuario;
        this.senha = senha;
    }

    public boolean isConfigurado() {
        return !baseUrl.isBlank();
    }

    public String getBaseUrl() {
        return baseUrl;
    }

    /** Resultado de um envio: id da pendência criada no Patrimônio (ou nulo se não informado). */
    public record Resultado(String idExterno, boolean jaExistia) {}

    /**
     * Envia o recebimento. Devolve o id da pendência no Patrimônio. Um 409 (compra já
     * enviada antes) é tratado como sucesso, para o reenvio ser idempotente.
     *
     * @throws IllegalStateException se a integração não estiver configurada
     * @throws RuntimeException em falhas de rede/HTTP (o chamador decide reenviar)
     */
    public Resultado enviarRecebimento(RecebimentoPatrimonioPayload payload) {
        if (!isConfigurado()) {
            throw new IllegalStateException("Integração com o Patrimônio não configurada (PATRIMONIO_URL).");
        }
        try {
            Map<?, ?> corpo = cliente().post()
                    .uri(CAMINHO_RECEBIMENTOS)
                    .contentType(MediaType.APPLICATION_JSON)
                    .body(payload)
                    .retrieve()
                    .onStatus(HttpStatusCode::isError, (req, res) -> {
                        throw new RestClientResponseException(
                                "Patrimônio respondeu " + res.getStatusCode(),
                                res.getStatusCode(), res.getStatusText(), res.getHeaders(), null, null);
                    })
                    .body(Map.class);
            String id = corpo != null && corpo.get("id") != null ? String.valueOf(corpo.get("id")) : null;
            return new Resultado(id, false);
        } catch (RestClientResponseException e) {
            if (e.getStatusCode().value() == 409) {
                log.info("[Patrimônio] compra {} já existia no Patrimônio (409) — tratado como enviado.", payload.compraId());
                return new Resultado(null, true);
            }
            throw e;
        }
    }

    private RestClient cliente() {
        RestClient c = client;
        if (c == null) {
            synchronized (this) {
                if (client == null) {
                    RestClient.Builder b = RestClient.builder().baseUrl(baseUrl);
                    if (usuario != null && !usuario.isBlank()) {
                        b.defaultHeaders(h -> h.setBasicAuth(usuario, senha == null ? "" : senha));
                    }
                    client = b.build();
                }
                c = client;
            }
        }
        return c;
    }
}
