package com.fundacao.aualmoxarifado.controller;

import com.fundacao.aualmoxarifado.domain.AcaoAuditoria;
import com.fundacao.aualmoxarifado.exception.RecursoNaoEncontradoException;
import com.fundacao.aualmoxarifado.repository.AuditoriaLogRepository;
import com.fundacao.aualmoxarifado.repository.spec.AuditoriaLogSpecifications;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.web.PageableDefault;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;

/**
 * Tela web da trilha de auditoria — só administradores. O endpoint REST
 * equivalente é {@code /api/auditoria}, regrado pelo mesmo filtro de Basic Auth.
 */
@Controller
@RequestMapping("/auditoria")
@RequiredArgsConstructor
public class AuditoriaController {

    private final AuditoriaLogRepository repository;

    @GetMapping
    public String listar(
            @RequestParam(required = false) String usuario,
            @RequestParam(required = false) AcaoAuditoria acao,
            @RequestParam(required = false) String entidade,
            @RequestParam(required = false) Long entidadeId,
            @RequestParam(required = false)
            @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate inicio,
            @RequestParam(required = false)
            @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate fim,
            @PageableDefault(size = 50, sort = "timestamp", direction = Sort.Direction.DESC)
            Pageable pageable,
            Model model) {

        LocalDateTime inicioDt = inicio != null ? inicio.atStartOfDay() : null;
        LocalDateTime fimDt    = fim    != null ? fim.atTime(LocalTime.MAX) : null;

        var page = repository.findAll(
                AuditoriaLogSpecifications.filtrar(usuario, acao, entidade, entidadeId, inicioDt, fimDt),
                pageable);

        model.addAttribute("page", page);
        model.addAttribute("filtroUsuario", usuario);
        model.addAttribute("filtroAcao", acao);
        model.addAttribute("filtroEntidade", entidade);
        model.addAttribute("filtroEntidadeId", entidadeId);
        model.addAttribute("filtroInicio", inicio);
        model.addAttribute("filtroFim", fim);
        return "auditoria/lista";
    }

    /**
     * Tela de detalhe — mostra um único evento de auditoria com tudo que
     * temos: payload de detalhes integral, IP, idempotency-key, e link para
     * a entidade que foi afetada (se for Movimentacao).
     */
    @GetMapping("/{id}")
    public String detalhar(@PathVariable Long id, Model model) {
        var log = repository.findById(id)
                .orElseThrow(() -> new RecursoNaoEncontradoException("AuditoriaLog", id));
        model.addAttribute("log", log);
        return "auditoria/detalhe";
    }
}
