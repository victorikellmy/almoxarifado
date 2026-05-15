package com.fundacao.aualmoxarifado.controller;

import com.fundacao.aualmoxarifado.dto.request.TrocarSenhaRequest;
import com.fundacao.aualmoxarifado.exception.RegraDeNegocioException;
import com.fundacao.aualmoxarifado.security.UsuarioAutenticado;
import com.fundacao.aualmoxarifado.service.UsuarioService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.validation.BindingResult;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

/** Páginas de autenticação (login/logout) e troca de senha. */
@Controller
@RequiredArgsConstructor
public class AuthController {

    private final UsuarioService usuarioService;

    @GetMapping("/login")
    public String login(@RequestParam(required = false) String erro,
                        @RequestParam(required = false) String desconectado,
                        Model model) {
        if (erro != null)         model.addAttribute("erro", "Login ou senha inválidos.");
        if (desconectado != null) model.addAttribute("info", "Sessão encerrada.");
        return "auth/login";
    }

    @GetMapping("/minha-conta/senha")
    public String formTrocarSenha(Model model) {
        if (!model.containsAttribute("form")) {
            model.addAttribute("form", new TrocarSenhaRequest("", "", ""));
        }
        return "auth/trocar-senha";
    }

    @PostMapping("/minha-conta/senha")
    public String trocarSenha(@AuthenticationPrincipal UsuarioAutenticado principal,
                              @Valid @ModelAttribute("form") TrocarSenhaRequest form,
                              BindingResult br,
                              RedirectAttributes ra) {
        if (br.hasErrors()) return "auth/trocar-senha";
        try {
            usuarioService.trocarSenha(principal.getUsername(), form);
            ra.addFlashAttribute("sucesso", "Senha alterada com sucesso.");
            return "redirect:/";
        } catch (RegraDeNegocioException ex) {
            br.reject("senha", ex.getMessage());
            return "auth/trocar-senha";
        }
    }
}
