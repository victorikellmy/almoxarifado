package com.fundacao.aualmoxarifado.controller;

import com.fundacao.aualmoxarifado.domain.Organizacao;
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
        model.addAttribute("setores", setorRepository.listarOrdenados());
        model.addAttribute("setor", new Setor());
        model.addAttribute("organizacoes", Organizacao.values());
        return "setores/lista";
    }

    @PostMapping
    public String salvar(@Valid @ModelAttribute("setor") Setor setor,
                         BindingResult br, Model model) {
        if (!br.hasErrors() && setor.getOrganizacao() != null
                && setorRepository.existsByNomeIgnoreCaseAndOrganizacao(setor.getNome().trim(), setor.getOrganizacao())) {
            br.rejectValue("nome", "duplicado",
                    "Já existe o setor \"" + setor.getNome().trim() + "\" em " + setor.getOrganizacao().getRotulo() + ".");
        }
        if (br.hasErrors()) {
            model.addAttribute("setores", setorRepository.listarOrdenados());
            model.addAttribute("organizacoes", Organizacao.values());
            return "setores/lista";
        }
        setor.setNome(setor.getNome().trim());
        if (setor.getResponsavel() != null && setor.getResponsavel().isBlank()) setor.setResponsavel(null);
        if (setor.getCodigoCentroCusto() != null && setor.getCodigoCentroCusto().isBlank()) setor.setCodigoCentroCusto(null);
        setorRepository.save(setor);
        return "redirect:/setores";
    }
}
