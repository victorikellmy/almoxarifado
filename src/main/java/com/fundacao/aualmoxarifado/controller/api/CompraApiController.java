package com.fundacao.aualmoxarifado.controller.api;

import com.fundacao.aualmoxarifado.domain.StatusCompra;
import com.fundacao.aualmoxarifado.dto.CompraDetalheDTO;
import com.fundacao.aualmoxarifado.dto.CompraResumoDTO;
import com.fundacao.aualmoxarifado.dto.PageResponse;
import com.fundacao.aualmoxarifado.service.CompraService;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.web.PageableDefault;
import org.springframework.web.bind.annotation.*;

/**
 * Acompanhamento de compras pelo app mobile — somente leitura.
 *
 * <ul>
 *   <li>{@code GET /api/compras?status=&page=&size=&sort=} — listagem paginada
 *       ({@link PageResponse}); ordenação padrão {@code dataSolicitacao,desc}.</li>
 *   <li>{@code GET /api/compras/{id}} — detalhe com itens e metadados dos anexos.</li>
 * </ul>
 *
 * <p>O mapeamento entidade → DTO acontece no service, dentro da transação
 * read-only (itens, materiais, setor e anexos já carregados).</p>
 */
@RestController
@RequestMapping("/api/compras")
@RequiredArgsConstructor
public class CompraApiController {

    private final CompraService compraService;

    @GetMapping
    public PageResponse<CompraResumoDTO> listar(
            @RequestParam(required = false) StatusCompra status,
            @PageableDefault(size = 20, sort = "dataSolicitacao", direction = Sort.Direction.DESC)
            Pageable pageable) {
        return PageResponse.of(compraService.listarResumo(status, pageable));
    }

    @GetMapping("/{id}")
    public CompraDetalheDTO buscar(@PathVariable Long id) {
        return compraService.buscarDetalhe(id);
    }
}
