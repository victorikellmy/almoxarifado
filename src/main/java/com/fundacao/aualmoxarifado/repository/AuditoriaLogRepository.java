package com.fundacao.aualmoxarifado.repository;

import com.fundacao.aualmoxarifado.domain.AcaoAuditoria;
import com.fundacao.aualmoxarifado.domain.AuditoriaLog;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;

import java.time.LocalDateTime;
import java.util.List;

public interface AuditoriaLogRepository extends JpaRepository<AuditoriaLog, Long>,
                                                 JpaSpecificationExecutor<AuditoriaLog> {

    /** Histórico de uma entidade específica — útil para a tela "ver auditoria deste item". */
    List<AuditoriaLog> findByEntidadeAndEntidadeIdOrderByTimestampDesc(String entidade, Long entidadeId);

    /** Todas as ações de um usuário num intervalo — base do relatório "o que o operador X bipou hoje". */
    Page<AuditoriaLog> findByUsuarioAndTimestampBetween(String usuario,
                                                        LocalDateTime inicio,
                                                        LocalDateTime fim,
                                                        Pageable pageable);

    Page<AuditoriaLog> findByAcaoAndTimestampBetween(AcaoAuditoria acao,
                                                     LocalDateTime inicio,
                                                     LocalDateTime fim,
                                                     Pageable pageable);
}
