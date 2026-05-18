package com.fundacao.aualmoxarifado.controller.api;

import com.fundacao.aualmoxarifado.domain.StatusMovimentacao;
import com.fundacao.aualmoxarifado.domain.TipoMovimentacao;
import com.fundacao.aualmoxarifado.dto.MovimentacaoResumoDTO;
import com.fundacao.aualmoxarifado.dto.PageResponse;
import com.fundacao.aualmoxarifado.service.MovimentacaoService;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.web.PageableDefault;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.time.LocalDateTime;

/**
 * Listagem paginada e filtrável de movimentações via REST API.
 *
 * <p>Padrão de paginação seguido pelo restante da API:</p>
 * <ul>
 *   <li>{@code page} — número da página, 0-based</li>
 *   <li>{@code size} — itens por página (padrão 20)</li>
 *   <li>{@code sort} — campo,direção (ex. {@code sort=data,desc})</li>
 * </ul>
 *
 * <p>Filtros opcionais — qualquer combinação. Vazios são ignorados.</p>
 */
@RestController
@RequestMapping("/api/movimentacoes")
@RequiredArgsConstructor
public class MovimentacaoApiController {

    private final MovimentacaoService service;

    @GetMapping
    public PageResponse<MovimentacaoResumoDTO> listar(
            @RequestParam(required = false) TipoMovimentacao tipo,
            @RequestParam(required = false) StatusMovimentacao status,
            @RequestParam(required = false) Long materialId,
            @RequestParam(required = false) Long setorId,
            @RequestParam(required = false)
            @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) LocalDateTime inicio,
            @RequestParam(required = false)
            @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) LocalDateTime fim,
            @PageableDefault(size = 20, sort = "data", direction = Sort.Direction.DESC)
            Pageable pageable) {

        Page<MovimentacaoResumoDTO> page = service
                .listar(tipo, status, materialId, setorId, inicio, fim, pageable)
                .map(MovimentacaoResumoDTO::from);

        return PageResponse.of(page);
    }
}
