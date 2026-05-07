package com.fundacao.aualmoxarifado.controller;

import com.fundacao.aualmoxarifado.domain.Categoria;
import com.fundacao.aualmoxarifado.service.CategoriaService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.validation.BindingResult;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

@Controller
@RequestMapping("/categorias")
@RequiredArgsConstructor
public class CategoriaController {

    private final CategoriaService categoriaService;

    @GetMapping
    public String listar(Model model) {
        model.addAttribute("categorias", categoriaService.listar());
        if (!model.containsAttribute("categoria")) {
            model.addAttribute("categoria", new Categoria());
        }
        return "categorias/lista";
    }

    @PostMapping
    public String salvar(@Valid @ModelAttribute("categoria") Categoria categoria,
                         BindingResult br, Model model) {
        if (br.hasErrors()) {
            model.addAttribute("categorias", categoriaService.listar());
            return "categorias/lista";
        }
        categoriaService.salvar(categoria);
        return "redirect:/categorias";
    }

    /** RN07 - exclusão protegida. */
    @PostMapping("/{id}/excluir")
    public String excluir(@PathVariable Long id, RedirectAttributes ra) {
        try {
            categoriaService.excluir(id);
        } catch (RuntimeException ex) {
            ra.addFlashAttribute("erro", ex.getMessage());
        }
        return "redirect:/categorias";
    }
}
