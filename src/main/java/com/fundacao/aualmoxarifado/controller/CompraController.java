package com.fundacao.aualmoxarifado.controller;

import com.fundacao.aualmoxarifado.domain.*;
import com.fundacao.aualmoxarifado.repository.MaterialRepository;
import com.fundacao.aualmoxarifado.repository.SetorRepository;
import com.fundacao.aualmoxarifado.service.AnexoStorageService;
import com.fundacao.aualmoxarifado.service.CompraService;
import lombok.RequiredArgsConstructor;
import org.springframework.core.io.Resource;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.web.PageableDefault;
import org.springframework.http.ContentDisposition;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;

/**
 * Controller MVC do Módulo de Compras (RF14/RF15/RF16).
 *
 * Mapeia 3 telas principais:
 *   - GET  /compras                 → listagem geral
 *   - GET  /compras/aguardando      → fila "Aguardando Compra" (RF15)
 *   - GET  /compras/nova            → formulário de pré-compra
 *   - POST /compras                 → cria a pré-compra (RF14)
 *   - GET  /compras/{id}            → detalhes
 *   - GET  /compras/{id}/baixa      → tela de recebimento da NF
 *   - POST /compras/{id}/baixa      → efetiva a baixa (RF15/RN09/RN10)
 *   - POST /compras/{id}/cancelar   → cancela uma pré-compra
 *   - GET  /compras/{id}/anexos/{anexoId}/download → serve o PDF
 */
@Controller
@RequestMapping("/compras")
@RequiredArgsConstructor
public class CompraController {

    private final CompraService compraService;
    private final MaterialRepository materialRepository;
    private final SetorRepository setorRepository;
    private final AnexoStorageService anexoStorageService;

    // =====================================================================
    // LISTAGENS
    // =====================================================================

    @GetMapping
    public String listar(@PageableDefault(size = 20, sort = "dataSolicitacao",
                                          direction = Sort.Direction.DESC) Pageable pageable,
                         Model model) {
        Page<Compra> page = compraService.listarTodas(pageable);
        model.addAttribute("page", page);
        model.addAttribute("compras", page.getContent());
        model.addAttribute("titulo", "Histórico de Compras");
        model.addAttribute("filtro", "todas");
        return "compras/lista";
    }

    /** RF15 - tela da fila de pré-compras pendentes (FIFO). */
    @GetMapping("/aguardando")
    public String listarAguardando(@PageableDefault(size = 20, sort = "dataSolicitacao",
                                                    direction = Sort.Direction.ASC) Pageable pageable,
                                   Model model) {
        Page<Compra> page = compraService.listarAguardandoCompra(pageable);
        model.addAttribute("page", page);
        model.addAttribute("compras", page.getContent());
        model.addAttribute("titulo", "Fila — Aguardando Compra");
        model.addAttribute("filtro", "aguardando");
        return "compras/lista";
    }

    // =====================================================================
    // PRÉ-COMPRA (criação)
    // =====================================================================

    @GetMapping("/nova")
    public String novaPreCompra(Model model) {
        prepararFormulario(model, new Compra());
        return "compras/form";
    }

    /**
     * Recebe o submit do form. Os itens vêm como arrays paralelos
     * (itemMaterialId[i], itemQuantidade[i], itemValorUnitario[i]) — formato
     * mais simples para o Thymeleaf gerenciar inputs adicionados via JS.
     */
    @PostMapping
    public String salvarPreCompra(@ModelAttribute Compra compra,
                                  @RequestParam(required = false) Long setorSolicitanteId,
                                  @RequestParam(name = "itemMaterialId", required = false) List<Long> materiaisIds,
                                  @RequestParam(name = "itemQuantidade", required = false) List<Integer> quantidades,
                                  @RequestParam(name = "itemValorUnitario", required = false) List<BigDecimal> valoresUnit,
                                  @RequestParam(name = "pdfSolicitacao", required = false) MultipartFile pdfSolicitacao,
                                  Model model) {
        try {
            // Wiring do setor: o form envia o id; transformamos numa referência.
            if (setorSolicitanteId != null) {
                Setor s = new Setor();
                s.setId(setorSolicitanteId);
                compra.setSetorSolicitante(s);
            }

            List<ItemCompra> itens = montarItens(materiaisIds, quantidades, valoresUnit);
            compraService.criarPreCompra(compra, itens, pdfSolicitacao);
            return "redirect:/compras/aguardando";
        } catch (RuntimeException ex) {
            model.addAttribute("erro", ex.getMessage());
            prepararFormulario(model, compra);
            return "compras/form";
        }
    }

    // =====================================================================
    // BAIXA (recebimento da NF)
    // =====================================================================

    /** Tela de recebimento — abre os campos de NF + upload do PDF. */
    @GetMapping("/{id}/baixa")
    public String telaBaixa(@PathVariable Long id, Model model) {
        Compra compra = compraService.buscarPorId(id);
        model.addAttribute("compra", compra);
        return "compras/recebimento";
    }

    @PostMapping("/{id}/baixa")
    public String efetivarBaixa(@PathVariable Long id,
                                @RequestParam String numeroNF,
                                @RequestParam BigDecimal valorRealFinal,
                                @RequestParam(required = false) MultipartFile pdfNotaFiscal,
                                Model model) {
        try {
            compraService.darBaixa(id, numeroNF, valorRealFinal, pdfNotaFiscal);
            return "redirect:/compras/" + id;
        } catch (RuntimeException ex) {
            model.addAttribute("erro", ex.getMessage());
            model.addAttribute("compra", compraService.buscarPorId(id));
            return "compras/recebimento";
        }
    }

    // =====================================================================
    // DETALHES / AÇÕES
    // =====================================================================

    @GetMapping("/{id}")
    public String detalhes(@PathVariable Long id, Model model) {
        model.addAttribute("compra", compraService.buscarPorId(id));
        return "compras/detalhes";
    }

    @PostMapping("/{id}/cancelar")
    public String cancelar(@PathVariable Long id) {
        compraService.cancelar(id);
        return "redirect:/compras";
    }

    /**
     * RF16 - download do PDF anexado. Aplica os headers corretos e devolve
     * o arquivo via streaming (sem carregá-lo todo em memória).
     */
    @GetMapping("/{id}/anexos/{anexoId}/download")
    public ResponseEntity<Resource> baixarAnexo(@PathVariable Long id, @PathVariable Long anexoId) {
        Compra compra = compraService.buscarPorId(id);
        AnexoCompra anexo = compra.getAnexos().stream()
                .filter(a -> a.getId().equals(anexoId))
                .findFirst()
                .orElseThrow(() -> new IllegalArgumentException("Anexo não encontrado nesta compra."));

        Resource recurso = anexoStorageService.carregar(anexo.getCaminhoArmazenado());
        ContentDisposition disposition = ContentDisposition.inline()
                .filename(anexo.getNomeOriginal(), StandardCharsets.UTF_8)
                .build();

        return ResponseEntity.ok()
                .contentType(anexo.getContentType() != null
                        ? MediaType.parseMediaType(anexo.getContentType())
                        : MediaType.APPLICATION_PDF)
                .header(HttpHeaders.CONTENT_DISPOSITION, disposition.toString())
                .body(recurso);
    }

    // =====================================================================
    // HELPERS PRIVADOS
    // =====================================================================

    private void prepararFormulario(Model model, Compra compra) {
        model.addAttribute("compra", compra);
        model.addAttribute("materiais", materialRepository.findAll());
        model.addAttribute("setores", setorRepository.findAll());
        model.addAttribute("tipos", TipoCompra.values());
    }

    /**
     * Converte os arrays paralelos do form (id, qtd, valor) em uma lista de
     * {@link ItemCompra} ainda não persistidos. A validação fina (qtd > 0,
     * material existente) acontece no Service.
     */
    private List<ItemCompra> montarItens(List<Long> materiaisIds,
                                         List<Integer> quantidades,
                                         List<BigDecimal> valoresUnit) {
        List<ItemCompra> itens = new ArrayList<>();
        if (materiaisIds == null || materiaisIds.isEmpty()) {
            return itens;
        }
        for (int i = 0; i < materiaisIds.size(); i++) {
            Long matId = materiaisIds.get(i);
            if (matId == null) {
                continue; // linha vazia — usuário removeu
            }
            Material m = new Material();
            m.setId(matId);

            Integer qtd = (quantidades != null && i < quantidades.size()) ? quantidades.get(i) : null;
            BigDecimal valor = (valoresUnit != null && i < valoresUnit.size())
                    ? valoresUnit.get(i)
                    : BigDecimal.ZERO;

            itens.add(ItemCompra.builder()
                    .material(m)
                    .quantidade(qtd)
                    .valorUnitario(valor != null ? valor : BigDecimal.ZERO)
                    .build());
        }
        return itens;
    }
}
