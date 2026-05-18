package com.fundacao.aualmoxarifado.controller.api;

import com.fundacao.aualmoxarifado.domain.AcaoAuditoria;
import com.fundacao.aualmoxarifado.dto.AuditoriaLogDTO;
import com.fundacao.aualmoxarifado.dto.PageResponse;
import com.fundacao.aualmoxarifado.repository.AuditoriaLogRepository;
import com.fundacao.aualmoxarifado.repository.spec.AuditoriaLogSpecifications;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.web.PageableDefault;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.time.LocalDateTime;
import java.util.List;

/**
 * Consulta da trilha de auditoria.
 *
 * <p>Endpoints:</p>
 * <ul>
 *   <li>{@code GET /api/auditoria} — listagem paginada com filtros</li>
 *   <li>{@code GET /api/auditoria/entidade/{tipo}/{id}} — histórico completo
 *       de uma entidade específica (ex.: todas as alterações da Movimentacao 42)</li>
 * </ul>
 *
 * <p>Filtros: {@code usuario}, {@code acao}, {@code entidade},
 * {@code entidadeId}, {@code inicio}, {@code fim} (ISO date-time).</p>
 */
@RestController
@RequestMapping("/api/auditoria")
@RequiredArgsConstructor
public class AuditoriaApiController {

    private final AuditoriaLogRepository repository;

    @GetMapping
    public PageResponse<AuditoriaLogDTO> listar(
            @RequestParam(required = false) String usuario,
            @RequestParam(required = false) AcaoAuditoria acao,
            @RequestParam(required = false) String entidade,
            @RequestParam(required = false) Long entidadeId,
            @RequestParam(required = false)
            @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) LocalDateTime inicio,
            @RequestParam(required = false)
            @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) LocalDateTime fim,
            @PageableDefault(size = 50, sort = "timestamp", direction = Sort.Direction.DESC)
            Pageable pageable) {

        Page<AuditoriaLogDTO> page = repository
                .findAll(AuditoriaLogSpecifications.filtrar(
                        usuario, acao, entidade, entidadeId, inicio, fim), pageable)
                .map(AuditoriaLogDTO::from);

        return PageResponse.of(page);
    }

    /**
     * Histórico completo (sem paginação) de uma entidade — útil para a tela
     * "ver auditoria deste item". Limita-se ao que cabe na tela; se um item
     * tiver mais de algumas centenas de eventos, usar o endpoint paginado.
     */
    @GetMapping("/entidade/{entidade}/{id}")
    public List<AuditoriaLogDTO> historicoEntidade(
            @PathVariable String entidade,
            @PathVariable Long id) {
        return repository
                .findByEntidadeAndEntidadeIdOrderByTimestampDesc(entidade, id)
                .stream()
                .map(AuditoriaLogDTO::from)
                .toList();
    }
}
