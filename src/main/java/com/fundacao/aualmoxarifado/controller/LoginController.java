package com.fundacao.aualmoxarifado.controller;

import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.GetMapping;

/**
 * Tela de login. O POST de autenticação é processado pelo próprio Spring
 * Security (formLogin) — este controller só serve a página.
 */
@Controller
public class LoginController {

    @GetMapping("/login")
    public String login() {
        return "login";
    }
}
