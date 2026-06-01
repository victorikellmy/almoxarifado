package com.fpto.almoxarifado.controller;

import com.fpto.almoxarifado.domain.Area;
import com.fpto.almoxarifado.service.AreaService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.validation.BindingResult;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

@Controller
@RequestMapping("/areas")
@RequiredArgsConstructor
public class AreaController {

    private final AreaService areaService;

    /** Tela combinada: form de cadastro acima + tabela das áreas existentes. */
    @GetMapping
    public String listar(Model model) {
        model.addAttribute("areas", areaService.listar());
        if (!model.containsAttribute("area")) {
            model.addAttribute("area", new Area());
        }
        return "areas/lista";
    }

    @PostMapping
    public String salvar(@Valid @ModelAttribute("area") Area area,
                         BindingResult br, Model model) {
        if (br.hasErrors()) {
            model.addAttribute("areas", areaService.listar());
            return "areas/lista";
        }
        try {
            areaService.salvar(area);
        } catch (RuntimeException ex) {
            model.addAttribute("erro", ex.getMessage());
            model.addAttribute("areas", areaService.listar());
            return "areas/lista";
        }
        return "redirect:/areas";
    }

    @PostMapping("/{id}/excluir")
    public String excluir(@PathVariable Long id, RedirectAttributes ra) {
        try {
            areaService.excluir(id);
        } catch (RuntimeException ex) {
            ra.addFlashAttribute("erro", ex.getMessage());
        }
        return "redirect:/areas";
    }
}
