package com.fundacao.aualmoxarifado.controller;

import com.fundacao.aualmoxarifado.domain.Perfil;
import com.fundacao.aualmoxarifado.dto.request.UsuarioRequest;
import com.fundacao.aualmoxarifado.service.UsuarioService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.validation.BindingResult;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

@Controller
@RequestMapping("/usuarios")
@RequiredArgsConstructor
public class UsuarioController {

    private final UsuarioService usuarioService;

    @GetMapping
    public String listar(Model model) {
        model.addAttribute("usuarios", usuarioService.listar());
        return "usuarios/lista";
    }

    @GetMapping("/novo")
    public String formNovo(Model model) {
        if (!model.containsAttribute("form")) {
            model.addAttribute("form", new UsuarioRequest("", "", "", Perfil.OPERADOR, true));
        }
        model.addAttribute("perfis", Perfil.values());
        return "usuarios/form";
    }

    @PostMapping
    public String salvar(@Valid @ModelAttribute("form") UsuarioRequest form,
                         BindingResult br, RedirectAttributes ra, Model model) {
        if (br.hasErrors()) {
            model.addAttribute("perfis", Perfil.values());
            return "usuarios/form";
        }
        usuarioService.criar(form);
        ra.addFlashAttribute("sucesso", "Usuário cadastrado.");
        return "redirect:/usuarios";
    }

    @GetMapping("/{id}/editar")
    public String editar(@PathVariable Long id, Model model) {
        var u = usuarioService.buscar(id);
        model.addAttribute("usuario", u);
        model.addAttribute("form", new UsuarioRequest(u.getNomeCompleto(), u.getLogin(), "",
                u.getPerfil(), u.isAtivo()));
        model.addAttribute("perfis", Perfil.values());
        return "usuarios/form";
    }

    @PostMapping("/{id}")
    public String atualizar(@PathVariable Long id,
                            @Valid @ModelAttribute("form") UsuarioRequest form,
                            BindingResult br, RedirectAttributes ra, Model model) {
        if (br.hasErrors()) {
            model.addAttribute("perfis", Perfil.values());
            return "usuarios/form";
        }
        usuarioService.atualizar(id, form);
        ra.addFlashAttribute("sucesso", "Usuário atualizado.");
        return "redirect:/usuarios";
    }
}
