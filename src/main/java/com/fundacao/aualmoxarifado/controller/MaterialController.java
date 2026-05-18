package com.fundacao.aualmoxarifado.controller;

import com.fundacao.aualmoxarifado.domain.Material;
import com.fundacao.aualmoxarifado.repository.AreaRepository;
import com.fundacao.aualmoxarifado.repository.SubcategoriaRepository;
import com.fundacao.aualmoxarifado.service.MaterialService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.web.PageableDefault;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.validation.BindingResult;
import org.springframework.web.bind.annotation.*;

@Controller
@RequestMapping("/materiais")
@RequiredArgsConstructor
public class MaterialController {

    private final MaterialService materialService;
    private final AreaRepository areaRepository;
    private final SubcategoriaRepository subcategoriaRepository;

    @GetMapping
    public String listar(
            @RequestParam(required = false) String nome,
            @RequestParam(required = false) String sku,
            @RequestParam(required = false) Long areaId,
            @RequestParam(required = false) Long subcategoriaId,
            @RequestParam(required = false) Boolean emAlerta,
            @PageableDefault(size = 20, sort = "nome", direction = Sort.Direction.ASC)
            Pageable pageable,
            Model model) {

        var page = materialService.listar(nome, sku, subcategoriaId, areaId, emAlerta, pageable);

        model.addAttribute("page", page);
        model.addAttribute("alertas", materialService.alertasDeEstoque()); // RN06
        model.addAttribute("areas", areaRepository.findAllByOrderByNomeAsc());
        // Subcategorias só fazem sentido quando uma área está selecionada — evita
        // dropdown de centenas de itens. O JS do template recarrega via AJAX
        // quando a área muda (endpoint já existente: /subcategorias/api/por-area/{areaId}).
        if (areaId != null) {
            model.addAttribute("subcategorias",
                    subcategoriaRepository.findByAreaIdOrderByNomeAsc(areaId));
        }
        // Filtros selecionados para a UI manter o estado.
        model.addAttribute("filtroNome", nome);
        model.addAttribute("filtroSku", sku);
        model.addAttribute("filtroAreaId", areaId);
        model.addAttribute("filtroSubcategoriaId", subcategoriaId);
        model.addAttribute("filtroEmAlerta", emAlerta);
        return "materiais/lista";
    }

    @GetMapping("/novo")
    public String formNovo(Model model) {
        model.addAttribute("material", new Material());
        prepararListasDoForm(model);
        return "materiais/form";
    }

    @GetMapping("/{id}/editar")
    public String editar(@PathVariable Long id, Model model) {
        model.addAttribute("material", materialService.buscar(id));
        prepararListasDoForm(model);
        return "materiais/form";
    }

    /**
     * Salva o material. Em caso de cadastro novo, redireciona direto para a
     * tela de etiqueta para que o usuário possa imprimir o código de barras
     * recém-gerado (RF18).
     */
    @PostMapping
    public String salvar(@Valid @ModelAttribute("material") Material material,
                         BindingResult br, Model model) {
        if (br.hasErrors()) {
            prepararListasDoForm(model);
            return "materiais/form";
        }
        try {
            boolean ehNovo = material.getId() == null;
            Material salvo = materialService.salvar(material);
            return ehNovo
                    ? "redirect:/materiais/" + salvo.getId() + "/etiqueta"
                    : "redirect:/materiais";
        } catch (RuntimeException ex) {
            model.addAttribute("erro", ex.getMessage());
            prepararListasDoForm(model);
            return "materiais/form";
        }
    }

    /**
     * RF18 - Página de etiqueta: exibe nome, SKU e o código de barras renderizado
     * via JsBarcode no navegador. O usuário pode imprimir/colar a etiqueta
     * no produto físico para futura "bipagem".
     */
    @GetMapping("/{id}/etiqueta")
    public String etiqueta(@PathVariable Long id, Model model) {
        model.addAttribute("material", materialService.buscar(id));
        return "materiais/etiqueta";
    }

    /**
     * Carrega áreas (para o primeiro select) e, se o material já tem
     * subcategoria definida (caso de edição), as subcategorias da área dele —
     * assim o segundo dropdown já vem preenchido sem precisar de AJAX.
     */
    private void prepararListasDoForm(Model model) {
        model.addAttribute("areas", areaRepository.findAllByOrderByNomeAsc());
        Material atual = (Material) model.asMap().get("material");
        if (atual != null && atual.getSubcategoria() != null
                && atual.getSubcategoria().getArea() != null) {
            model.addAttribute("subcategorias",
                    subcategoriaRepository.findByAreaIdOrderByNomeAsc(
                            atual.getSubcategoria().getArea().getId()));
        }
    }
}
