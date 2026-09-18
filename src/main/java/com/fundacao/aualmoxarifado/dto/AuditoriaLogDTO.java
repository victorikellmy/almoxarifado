package com.fundacao.aualmoxarifado.dto;

import com.fundacao.aualmoxarifado.domain.AcaoAuditoria;
import com.fundacao.aualmoxarifado.domain.AuditoriaLog;

import java.time.LocalDateTime;

/** Projeção da trilha de auditoria para a REST API. */
public record AuditoriaLogDTO(
        Long id,
        LocalDateTime timestamp,
        String usuario,
        AcaoAuditoria acao,
        String entidade,
        Long entidadeId,
        String setor,
        String detalhes,
        String ipOrigem,
        String idempotencyKey
) {
    public static AuditoriaLogDTO from(AuditoriaLog log) {
        return new AuditoriaLogDTO(
                log.getId(),
                log.getTimestamp(),
                log.getUsuario(),
                log.getAcao(),
                log.getEntidade(),
                log.getEntidadeId(),
                log.getSetor(),
                log.getDetalhes(),
                log.getIpOrigem(),
                log.getIdempotencyKey()
        );
    }
}
