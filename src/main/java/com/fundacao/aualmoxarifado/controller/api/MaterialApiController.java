package com.fundacao.aualmoxarifado.controller.api;

import com.fundacao.aualmoxarifado.dto.MaterialResumoDTO;
import com.fundacao.aualmoxarifado.dto.PageResponse;
import com.fundacao.aualmoxarifado.exception.RecursoNaoEncontradoException;
import com.fundacao.aualmoxarifado.repository.MaterialRepository;
import com.fundacao.aualmoxarifado.service.MaterialService;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.web.PageableDefault;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * Listagem paginada e filtrável de materiais via REST API.
 *
 * <p>Endpoints:</p>
 * <ul>
 *   <li>{@code GET /api/materiais} — listagem paginada com filtros</li>
 *   <li>{@code GET /api/materiais/{id}} — detalhe</li>
 *   <li>{@code GET /api/materiais/sku/{codigoSku}} — lookup pelo SKU bipado</li>
 * </ul>
 */
@RestController
@RequestMapping("/api/materiais")
@RequiredArgsConstructor
public class MaterialApiController {

    private final MaterialRepository repository;
    private final MaterialService service;

    @GetMapping
    public PageResponse<MaterialResumoDTO> listar(
            @RequestParam(required = false) String nome,
            @RequestParam(required = false) String sku,
            @RequestParam(required = false) Long subcategoriaId,
            @RequestParam(required = false) Long areaId,
            @RequestParam(required = false) Boolean emAlerta,
            @PageableDefault(size = 20, sort = "nome", direction = Sort.Direction.ASC)
            Pageable pageable) {

        Page<MaterialResumoDTO> page = service
                .listar(nome, sku, subcategoriaId, areaId, emAlerta, pageable)
                .map(MaterialResumoDTO::from);

        return PageResponse.of(page);
    }

    @GetMapping("/{id}")
    public MaterialResumoDTO buscar(@PathVariable Long id) {
        return repository.findById(id)
                .map(MaterialResumoDTO::from)
                .orElseThrow(() -> new RecursoNaoEncontradoException("Material", id));
    }

    @GetMapping("/sku/{codigoSku}")
    public MaterialResumoDTO buscarPorSku(@PathVariable String codigoSku) {
        return repository.findByCodigoSku(codigoSku.trim().toUpperCase())
                .map(MaterialResumoDTO::from)
                .orElseThrow(() -> new RecursoNaoEncontradoException(
                        "Material para o SKU '" + codigoSku + "' não encontrado."));
    }
}
