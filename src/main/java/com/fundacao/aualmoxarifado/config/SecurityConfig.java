package com.fundacao.aualmoxarifado.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.web.SecurityFilterChain;

/**
 * Configuração de autenticação e autorização.
 *
 * - Login por formulário próprio em /login (tela com a identidade do sistema);
 * - Toda página exige usuário autenticado, exceto o próprio login;
 * - /usuarios/** é restrito ao perfil ADMIN;
 * - Console do H2 liberado apenas para ADMIN (ferramenta de manutenção);
 * - CSRF permanece LIGADO — os formulários Thymeleaf usam th:action, então o
 *   token é injetado automaticamente. O console H2 é a única exceção (ele não
 *   sabe mandar o token) e por isso está fora da proteção.
 *
 * O usuário inicial (admin) é criado pelo {@link UsuarioSeeder} no primeiro
 * boot com banco vazio.
 */
@Configuration
@EnableWebSecurity
public class SecurityConfig {

    @Bean
    public PasswordEncoder passwordEncoder() {
        return new BCryptPasswordEncoder();
    }

    @Bean
    public SecurityFilterChain filterChain(HttpSecurity http) throws Exception {
        http
            .authorizeHttpRequests(auth -> auth
                    // Healthcheck do Docker/proxy: sem autenticação e sem
                    // detalhes de infra (show-details=never nas properties).
                    .requestMatchers("/actuator/health").permitAll()
                    .requestMatchers("/login", "/css/**", "/js/**", "/img/**", "/favicon.ico").permitAll()
                    .requestMatchers("/usuarios/**").hasRole("ADMIN")
                    .requestMatchers("/h2-console/**").hasRole("ADMIN")
                    .anyRequest().authenticated()
            )
            .formLogin(form -> form
                    .loginPage("/login")
                    .defaultSuccessUrl("/", false)
                    .failureUrl("/login?erro")
                    .permitAll()
            )
            // Logout: o default do Spring Security já é POST /logout com CSRF.
            .logout(logout -> logout.logoutSuccessUrl("/login?saiu"))
            // O console do H2 roda em <frame> e não envia token CSRF.
            .csrf(csrf -> csrf.ignoringRequestMatchers("/h2-console/**"))
            .headers(headers -> headers
                    .frameOptions(frame -> frame.sameOrigin()));

        return http.build();
    }
}
