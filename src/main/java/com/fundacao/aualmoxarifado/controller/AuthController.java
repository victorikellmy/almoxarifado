package com.fundacao.aualmoxarifado.controller;

import com.fundacao.aualmoxarifado.dto.request.TrocarSenhaRequest;
import com.fundacao.aualmoxarifado.service.UsuarioService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.validation.BindingResult;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

/**
 * Conta do próprio usuário logado — hoje, apenas a troca de senha.
 *
 * A tela de login fica no {@link LoginController}; o POST de autenticação e o
 * logout são processados pelo próprio Spring Security.
 */
@Controller
@RequiredArgsConstructor
public class AuthController {

    private final UsuarioService usuarioService;

    @GetMapping("/minha-conta/senha")
    public String formTrocarSenha(Model model) {
        if (!model.containsAttribute("form")) {
            model.addAttribute("form", new TrocarSenhaRequest("", "", ""));
        }
        return "auth/trocar-senha";
    }

    /**
     * A senha atual é conferida contra o hash antes de gravar a nova — quem faz
     * isso é o {@code UsuarioService}; aqui só traduzimos a falha em erro de
     * formulário para o usuário continuar na mesma tela.
     */
    @PostMapping("/minha-conta/senha")
    public String trocarSenha(Authentication authentication,
                              @Valid @ModelAttribute("form") TrocarSenhaRequest form,
                              BindingResult br,
                              RedirectAttributes ra) {
        if (br.hasErrors()) {
            return "auth/trocar-senha";
        }
        try {
            usuarioService.trocarSenha(authentication.getName(), form);
            ra.addFlashAttribute("sucesso", "Senha alterada com sucesso.");
            return "redirect:/";
        } catch (RuntimeException ex) {
            br.reject("senha", ex.getMessage());
            return "auth/trocar-senha";
        }
    }
}
