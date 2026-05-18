package com.fundacao.aualmoxarifado.security;

import lombok.RequiredArgsConstructor;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.annotation.Order;
import org.springframework.http.HttpMethod;
import org.springframework.security.config.Customizer;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.config.annotation.web.configurers.HeadersConfigurer;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.web.SecurityFilterChain;

/**
 * Configuração central do Spring Security.
 *
 * <ul>
 *   <li>Interface web → sessão + form login + CSRF habilitado</li>
 *   <li>Endpoints /api → HTTP Basic + stateless (CSRF desabilitado apenas para /api)</li>
 *   <li>Recursos estáticos (/css, /js) e H2-console (dev) → públicos</li>
 * </ul>
 */
@Configuration
@EnableMethodSecurity
@RequiredArgsConstructor
public class SecurityConfig {

    @Bean
    public PasswordEncoder passwordEncoder() {
        return new BCryptPasswordEncoder();
    }

    // =========================================================================
    // Filter chain #1 — API REST (/api/**) — Basic Auth, stateless
    // =========================================================================
    @Bean
    @Order(1)
    public SecurityFilterChain apiFilterChain(HttpSecurity http) throws Exception {
        http
                .securityMatcher("/api/**")
                .csrf(AbstractHttpConfigurer::disable)
                .sessionManagement(s -> s.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
                .authorizeHttpRequests(auth -> auth
                        .requestMatchers(HttpMethod.DELETE, "/api/**").hasRole("ADMINISTRADOR")
                        .requestMatchers(HttpMethod.POST, "/api/importacao/**").hasRole("ADMINISTRADOR")
                        .anyRequest().authenticated()
                )
                .httpBasic(Customizer.withDefaults());
        return http.build();
    }

    // =========================================================================
    // Filter chain #2 — Interface web (Thymeleaf)
    //
    // Estratégia inicial: tudo autenticado, exceto login/h2-console/assets.
    // As regras finas por perfil (ADMINISTRADOR vs OPERADOR) podem ser
    // adicionadas via @PreAuthorize nos métodos dos services/controllers.
    // =========================================================================
    @Bean
    @Order(2)
    public SecurityFilterChain webFilterChain(HttpSecurity http) throws Exception {
        http
                .authorizeHttpRequests(auth -> auth
                        // recursos estáticos e públicos
                        .requestMatchers(
                                "/login", "/css/**", "/js/**", "/images/**",
                                "/webjars/**", "/favicon.ico"
                        ).permitAll()
                        .requestMatchers("/h2-console/**").permitAll()

                        // ações administrativas
                        .requestMatchers("/usuarios/**").hasRole("ADMINISTRADOR")
                        .requestMatchers("/auditoria/**").hasRole("ADMINISTRADOR")
                        .requestMatchers(HttpMethod.POST, "/movimentacoes/*/status").hasRole("ADMINISTRADOR")
                        .requestMatchers(HttpMethod.POST, "/areas/*/excluir").hasRole("ADMINISTRADOR")
                        .requestMatchers(HttpMethod.POST, "/subcategorias/*/excluir").hasRole("ADMINISTRADOR")
                        .requestMatchers(HttpMethod.POST, "/materiais/*/excluir").hasRole("ADMINISTRADOR")
                        .requestMatchers(HttpMethod.POST, "/setores/*/excluir").hasRole("ADMINISTRADOR")

                        .anyRequest().authenticated()
                )
                .formLogin(form -> form
                        .loginPage("/login")
                        .defaultSuccessUrl("/", true)
                        .failureUrl("/login?erro")
                        .permitAll()
                )
                .logout(logout -> logout
                        .logoutUrl("/logout")
                        .logoutSuccessUrl("/login?desconectado")
                        .deleteCookies("JSESSIONID")
                        .permitAll()
                )
                .headers(h -> h.frameOptions(HeadersConfigurer.FrameOptionsConfig::sameOrigin))
                .csrf(c -> c.ignoringRequestMatchers("/h2-console/**"));
        return http.build();
    }
}
