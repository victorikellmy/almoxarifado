package com.fundacao.aualmoxarifado.controller;

import com.fundacao.aualmoxarifado.domain.Material;
import com.fundacao.aualmoxarifado.repository.CategoriaRepository;
import com.fundacao.aualmoxarifado.service.MaterialService;
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
    private final CategoriaRepository categoriaRepository;

    @GetMapping
    public String listar(Model model) {
        model.addAttribute("materiais", materialService.listar());
        model.addAttribute("alertas", materialService.alertasDeEstoque()); // RN06
        return "materiais/lista";
    }

    @GetMapping("/novo")
    public String formNovo(Model model) {
        model.addAttribute("material", new Material());
        model.addAttribute("categorias", categoriaRepository.findAll());
        return "materiais/form";
    }

    @GetMapping("/{id}/editar")
    public String editar(@PathVariable Long id, Model model) {
        model.addAttribute("material", materialService.buscar(id));
        model.addAttribute("categorias", categoriaRepository.findAll());
        return "materiais/form";
    }

    @PostMapping
    public String salvar(@Valid @ModelAttribute("material") Material material,
                         BindingResult br, Model model) {
        if (br.hasErrors()) {
            model.addAttribute("categorias", categoriaRepository.findAll());
            return "materiais/form";
        }
        materialService.salvar(material);
        return "redirect:/materiais";
    }
}
