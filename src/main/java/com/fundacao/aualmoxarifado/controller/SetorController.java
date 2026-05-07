package com.fundacao.aualmoxarifado.controller;

import com.fundacao.aualmoxarifado.domain.Setor;
import com.fundacao.aualmoxarifado.repository.SetorRepository;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.validation.BindingResult;
import org.springframework.web.bind.annotation.*;

@Controller
@RequestMapping("/setores")
@RequiredArgsConstructor
public class SetorController {

    private final SetorRepository setorRepository;

    @GetMapping
    public String listar(Model model) {
        model.addAttribute("setores", setorRepository.findAll());
        model.addAttribute("setor", new Setor());
        return "setores/lista";
    }

    @PostMapping
    public String salvar(@Valid @ModelAttribute("setor") Setor setor,
                         BindingResult br, Model model) {
        if (br.hasErrors()) {
            model.addAttribute("setores", setorRepository.findAll());
            return "setores/lista";
        }
        setorRepository.save(setor);
        return "redirect:/setores";
    }
}
