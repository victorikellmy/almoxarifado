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
import org.springframework.security.config.annotation.web.configurers.SessionManagementConfigurer;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.web.header.writers.ReferrerPolicyHeaderWriter;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.config.annotation.web.configuration.WebSecurityCustomizer;
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

    private final ApiAuthenticationEntryPoint apiAuthenticationEntryPoint;

    /**
     * Console H2 só existe em dev. A flag reflete {@code spring.h2.console.enabled}
     * (true apenas no perfil dev), então em produção as regras que abrem o
     * console nem entram na cadeia de filtros.
     */
    @org.springframework.beans.factory.annotation.Value("${spring.h2.console.enabled:false}")
    private boolean h2ConsoleEnabled;

    /**
     * Necessário para o {@code maximumSessions(1)} funcionar: publica os eventos
     * de criação/destruição de sessão para o SessionRegistry, senão sessões
     * encerradas nunca são liberadas do contador e o usuário fica travado.
     */
    @Bean
    public org.springframework.security.web.session.HttpSessionEventPublisher httpSessionEventPublisher() {
        return new org.springframework.security.web.session.HttpSessionEventPublisher();
    }

    @Bean
    public PasswordEncoder passwordEncoder() {
        // Strength explícito: o default pode mudar entre versões do Spring Security
        // e alterar silenciosamente a latência de autenticação.
        return new BCryptPasswordEncoder(10);
    }

    /**
     * Recursos estáticos fora da cadeia de filtros: permitAll() ainda executa
     * ~15 filtros por CSS/JS/imagem; ignoring() pula a cadeia inteira.
     */
    @Bean
    public WebSecurityCustomizer ignorarEstaticos() {
        return web -> web.ignoring()
                .requestMatchers("/css/**", "/js/**", "/images/**", "/webjars/**", "/favicon.ico");
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
                .cors(Customizer.withDefaults())
                .sessionManagement(s -> s.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
                .authorizeHttpRequests(auth -> auth
                        .requestMatchers(HttpMethod.DELETE, "/api/**").hasRole("ADMINISTRADOR")
                        .requestMatchers(HttpMethod.POST, "/api/importacao/**").hasRole("ADMINISTRADOR")
                        // Trilha de auditoria é admin-only na web; a API tem de
                        // seguir a mesma regra, senão um OPERADOR (ou a credencial
                        // do app mobile) leria o histórico inteiro de todos.
                        .requestMatchers("/api/auditoria/**").hasRole("ADMINISTRADOR")
                        .anyRequest().authenticated()
                )
                .httpBasic(basic -> basic.authenticationEntryPoint(apiAuthenticationEntryPoint))
                // Falha de autenticação → 401 JSON; falta de perfil → 403 JSON.
                // Nunca 302 para /login: o app mobile não sabe seguir redirect de formulário.
                .exceptionHandling(ex -> ex
                        .authenticationEntryPoint(apiAuthenticationEntryPoint)
                        .accessDeniedHandler(apiAuthenticationEntryPoint));
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
                .authorizeHttpRequests(auth -> {
                        // públicos (estáticos ficam fora da cadeia — ver ignorarEstaticos())
                        auth.requestMatchers("/login").permitAll();
                        // Console H2 só é liberado quando de fato habilitado (dev).
                        // Em prod a regra nem existe, então /h2-console cai em authenticated.
                        if (h2ConsoleEnabled) {
                            auth.requestMatchers("/h2-console/**").permitAll();
                        }

                        // ações administrativas
                        auth.requestMatchers("/usuarios/**").hasRole("ADMINISTRADOR")
                        .requestMatchers("/auditoria/**").hasRole("ADMINISTRADOR")
                        .requestMatchers(HttpMethod.POST, "/movimentacoes/*/status").hasRole("ADMINISTRADOR")
                        .requestMatchers(HttpMethod.POST, "/areas/*/excluir").hasRole("ADMINISTRADOR")
                        .requestMatchers(HttpMethod.POST, "/subcategorias/*/excluir").hasRole("ADMINISTRADOR")
                        .requestMatchers(HttpMethod.POST, "/materiais/*/excluir").hasRole("ADMINISTRADOR")
                        .requestMatchers(HttpMethod.POST, "/setores/*/excluir").hasRole("ADMINISTRADOR")

                        .anyRequest().authenticated();
                })
                .formLogin(form -> form
                        .loginPage("/login")
                        .defaultSuccessUrl("/", true)
                        .failureUrl("/login?erro")
                        .permitAll()
                )
                .logout(logout -> logout
                        .logoutUrl("/logout")
                        .logoutSuccessUrl("/login?desconectado")
                        .invalidateHttpSession(true)
                        .deleteCookies("JSESSIONID")
                        .permitAll()
                )
                // Sessão: fixação tratada pelo default (changeSessionId no login).
                // maximumSessions(1) impede que a mesma conta fique aberta em
                // vários lugares ao mesmo tempo — um novo login expira o anterior,
                // reduzindo a janela de uma credencial compartilhada/roubada.
                .sessionManagement(s -> s
                        .sessionFixation(SessionManagementConfigurer.SessionFixationConfigurer::changeSessionId)
                        .maximumSessions(1)
                        .maxSessionsPreventsLogin(false)
                        .expiredUrl("/login?expirado"))
                .headers(h -> {
                        // frameOptions: sameOrigin só é necessário para o console H2 (dev).
                        // Em prod, DENY — nada legítimo embute o app em iframe.
                        if (h2ConsoleEnabled) {
                            h.frameOptions(HeadersConfigurer.FrameOptionsConfig::sameOrigin);
                        } else {
                            h.frameOptions(HeadersConfigurer.FrameOptionsConfig::deny);
                        }
                        // Referrer-Policy: impede que URLs internas (com filtros/ids
                        // em query string) vazem no header Referer para sites externos.
                        h.referrerPolicy(r -> r.policy(
                                ReferrerPolicyHeaderWriter.ReferrerPolicy.SAME_ORIGIN));
                })
                // CSRF continua ativo no fluxo web; só o console H2 (dev) é isento.
                .csrf(c -> {
                        if (h2ConsoleEnabled) {
                            c.ignoringRequestMatchers("/h2-console/**");
                        }
                });
        return http.build();
    }
}
