package com.fundacao.aualmoxarifado.controller;

import com.fundacao.aualmoxarifado.domain.Material;
import com.fundacao.aualmoxarifado.repository.AreaRepository;
import com.fundacao.aualmoxarifado.repository.MaterialRepository;
import com.fundacao.aualmoxarifado.repository.SubcategoriaRepository;
import com.fundacao.aualmoxarifado.service.MaterialService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.validation.BindingResult;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

@Controller
@RequestMapping("/materiais")
@RequiredArgsConstructor
public class MaterialController {

    private final MaterialService materialService;
    private final AreaRepository areaRepository;
    private final SubcategoriaRepository subcategoriaRepository;
    private final MaterialRepository materialRepository;

    @GetMapping
    public String listar(Model model) {
        model.addAttribute("materiais", materialService.listar());
        model.addAttribute("alertas", materialService.alertasDeEstoque()); // RN06
        return "materiais/lista";
    }

    /**
     * Autocomplete usado pelos formulários de Pré-compra e Saída de estoque:
     * o catálogo tem milhares de materiais, então nunca carregamos a lista
     * inteira num {@code <select>} — o front pede aqui conforme o usuário
     * digita, e a resposta já vem limitada a 20 resultados.
     */
    @GetMapping("/buscar")
    @ResponseBody
    public List<Map<String, Object>> buscar(@RequestParam(defaultValue = "") String q) {
        if (q.isBlank()) {
            return List.of();
        }
        return materialRepository.findTop20ByNomeContainingIgnoreCaseOrderByNomeAsc(q.trim())
                .stream()
                .map(m -> Map.<String, Object>of(
                        "id", m.getId(),
                        "nome", m.getNome(),
                        "unidadeMedida", m.getUnidadeMedida() != null ? m.getUnidadeMedida() : "",
                        "estoqueAtual", m.getEstoqueAtual()))
                .toList();
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
