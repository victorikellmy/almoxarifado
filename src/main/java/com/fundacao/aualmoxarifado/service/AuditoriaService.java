package com.fundacao.aualmoxarifado.service;

import com.fundacao.aualmoxarifado.domain.AcaoAuditoria;
import com.fundacao.aualmoxarifado.domain.AuditoriaLog;
import com.fundacao.aualmoxarifado.domain.Movimentacao;
import com.fundacao.aualmoxarifado.domain.MovimentacaoItem;
import com.fundacao.aualmoxarifado.repository.AuditoriaLogRepository;
import jakarta.servlet.http.HttpServletRequest;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.stereotype.Service;


/**
 * Grava entradas em {@link AuditoriaLog}.
 *
 * <p>Roda na transação do chamador — não declara {@code @Transactional}
 * próprio. Isso garante atomicidade: a auditoria só persiste se a operação
 * de negócio também persistir.</p>
 *
 * <p>{@code HttpServletRequest} é injetado via {@link ObjectProvider} para
 * funcionar tanto em chamadas web (captura IP + Idempotency-Key) quanto em
 * jobs/seeders sem contexto HTTP (devolve nulo silenciosamente).</p>
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class AuditoriaService {

    private static final int MAX_DETALHES = 2000;

    private final AuditoriaLogRepository repository;
    private final ObjectProvider<HttpServletRequest> requestProvider;

    /** Audita o REGISTRO inicial de uma saída (status PENDENTE_APROVACAO). */
    public void registrarSaida(Movimentacao mov) {
        gravar(AcaoAuditoria.REGISTRAR_SAIDA, mov);
    }

    /** Audita mudança de status (aprovação, rejeição, entrega). */
    public void alterarStatus(Movimentacao mov) {
        gravar(AcaoAuditoria.ALTERAR_STATUS_MOVIMENTACAO, mov);
    }

    public void registrarEntrada(Movimentacao mov) {
        gravar(AcaoAuditoria.REGISTRAR_ENTRADA, mov);
    }

    public void registrarCompraDireta(Movimentacao mov) {
        gravar(AcaoAuditoria.REGISTRAR_COMPRA_DIRETA, mov);
    }

    private void gravar(AcaoAuditoria acao, Movimentacao mov) {
        try {
            HttpServletRequest req = requestProvider.getIfAvailable();
            AuditoriaLog log = AuditoriaLog.builder()
                    .acao(acao)
                    .entidade("Movimentacao")
                    .entidadeId(mov.getId())
                    .setor(mov.getSetorDestino() != null ? mov.getSetorDestino().getNome() : null)
                    .detalhes(resumirItens(mov))
                    .ipOrigem(req != null ? extrairIp(req) : null)
                    .idempotencyKey(req != null ? req.getHeader("Idempotency-Key") : null)
                    .build();
            repository.save(log);
        } catch (Exception ex) {
            // Auditoria não pode quebrar a operação de negócio — mas precisamos saber.
            log.error("Falha ao gravar AuditoriaLog para movimentacao={}, acao={}",
                    mov.getId(), acao, ex);
        }
    }

    /**
     * Resumo legível dos itens: "10× ODO-CON-00001 (Caneta), 5× FAR-MED-00042 (Aspirina)".
     * Trunca em {@value #MAX_DETALHES} caracteres para não estourar a coluna.
     */
    private String resumirItens(Movimentacao mov) {
        if (mov.getItens() == null || mov.getItens().isEmpty()) {
            return "(sem itens)";
        }
        // Acumula até o limite e para: a saída é limitada a MAX_DETALHES chars,
        // então não há motivo para formatar milhares de itens que serão truncados.
        StringBuilder sb = new StringBuilder(Math.min(MAX_DETALHES, 256));
        for (MovimentacaoItem item : mov.getItens()) {
            if (sb.length() > 0) sb.append(", ");
            sb.append(formatarItem(item));
            if (sb.length() > MAX_DETALHES) break;
        }
        if (sb.length() > MAX_DETALHES) {
            sb.setLength(MAX_DETALHES - 3);
            sb.append("...");
        }
        return sb.toString();
    }

    private String formatarItem(MovimentacaoItem item) {
        String sku = item.getMaterial() != null ? item.getMaterial().getCodigoSku() : "?";
        String nome = item.getMaterial() != null ? item.getMaterial().getNome() : "?";
        return item.getQuantidade() + "× " + sku + " (" + nome + ")";
    }

    /**
     * Honra X-Forwarded-For quando atrás de proxy — primeiro IP da lista é o
     * cliente real. Sem o header, cai no remoteAddr.
     */
    private String extrairIp(HttpServletRequest req) {
        String forwarded = req.getHeader("X-Forwarded-For");
        if (forwarded != null && !forwarded.isBlank()) {
            int virgula = forwarded.indexOf(',');
            return (virgula > 0 ? forwarded.substring(0, virgula) : forwarded).trim();
        }
        return req.getRemoteAddr();
    }
}
