package com.fundacao.aualmoxarifado.controller;

import com.fundacao.aualmoxarifado.domain.PerfilUsuario;
import com.fundacao.aualmoxarifado.domain.Usuario;
import com.fundacao.aualmoxarifado.service.UsuarioService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.validation.BindingResult;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

/**
 * Administração de usuários (restrito a ADMIN — ver {@code SecurityConfig}).
 *
 * A tela de login em si fica no {@link LoginController}; aqui é só o CRUD.
 */
@Controller
@RequestMapping("/usuarios")
@RequiredArgsConstructor
public class UsuarioController {

    private final UsuarioService usuarioService;

    /**
     * Segurança do binding: impede que um POST malicioso injete senhaHash,
     * ativo ou criadoEm por parâmetro de formulário. Esses campos são
     * controlados exclusivamente pelo service.
     */
    @InitBinder("usuario")
    void bloquearCamposSensiveis(org.springframework.web.bind.WebDataBinder binder) {
        binder.setDisallowedFields("senhaHash", "ativo", "criadoEm");
    }

    @GetMapping
    public String listar(Model model) {
        model.addAttribute("usuarios", usuarioService.listar());
        return "usuarios/lista";
    }

    @GetMapping("/novo")
    public String formNovo(Model model) {
        model.addAttribute("usuario", new Usuario());
        prepararForm(model, true);
        return "usuarios/form";
    }

    @GetMapping("/{id}/editar")
    public String editar(@PathVariable Long id, Model model) {
        model.addAttribute("usuario", usuarioService.buscar(id));
        prepararForm(model, false);
        return "usuarios/form";
    }

    /**
     * A senha chega em um parâmetro separado ({@code senhaNova}) e nunca passa
     * pelo binding da entidade — o campo {@code senhaHash} não é exposto no
     * formulário. Em edição, senha em branco = manter a atual.
     */
    @PostMapping
    public String salvar(@Valid @ModelAttribute("usuario") Usuario usuario,
                         BindingResult br,
                         @RequestParam(required = false) String senhaNova,
                         Model model,
                         RedirectAttributes ra) {
        boolean ehNovo = usuario.getId() == null;

        if (br.hasErrors()) {
            prepararForm(model, ehNovo);
            return "usuarios/form";
        }

        try {
            usuarioService.salvar(usuario, senhaNova);
            ra.addFlashAttribute("sucesso", ehNovo
                    ? "Usuário \"" + usuario.getUsername() + "\" criado."
                    : "Usuário \"" + usuario.getUsername() + "\" atualizado.");
            return "redirect:/usuarios";
        } catch (RuntimeException ex) {
            model.addAttribute("erro", ex.getMessage());
            prepararForm(model, ehNovo);
            return "usuarios/form";
        }
    }

    @PostMapping("/{id}/alternar-ativo")
    public String alternarAtivo(@PathVariable Long id,
                                Authentication authentication,
                                RedirectAttributes ra) {
        try {
            Usuario u = usuarioService.alternarAtivo(id, authentication.getName());
            ra.addFlashAttribute("sucesso", "Usuário \"" + u.getUsername() + "\" "
                    + (u.getAtivo() ? "reativado." : "desativado."));
        } catch (RuntimeException ex) {
            ra.addFlashAttribute("erro", ex.getMessage());
        }
        return "redirect:/usuarios";
    }

    private void prepararForm(Model model, boolean ehNovo) {
        model.addAttribute("perfis", PerfilUsuario.values());
        model.addAttribute("ehNovo", ehNovo);
    }
}
