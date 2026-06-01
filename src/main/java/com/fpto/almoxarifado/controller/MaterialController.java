package com.fpto.almoxarifado.controller;

import com.fpto.almoxarifado.domain.Material;
import com.fpto.almoxarifado.repository.AreaRepository;
import com.fpto.almoxarifado.repository.SubcategoriaRepository;
import com.fpto.almoxarifado.service.MaterialService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
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
    public String listar(Model model) {
        model.addAttribute("materiais", materialService.listar());
        model.addAttribute("alertas", materialService.alertasDeEstoque()); // RN06
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
