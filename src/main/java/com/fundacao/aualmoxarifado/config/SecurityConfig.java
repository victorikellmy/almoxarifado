package com.fundacao.aualmoxarifado.config;

import com.fundacao.aualmoxarifado.security.ApiAuthenticationEntryPoint;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.annotation.Order;
import org.springframework.http.HttpMethod;
import org.springframework.security.config.Customizer;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.annotation.web.configuration.WebSecurityCustomizer;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.config.annotation.web.configurers.HeadersConfigurer;
import org.springframework.security.config.annotation.web.configurers.SessionManagementConfigurer;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.header.writers.ReferrerPolicyHeaderWriter;
import org.springframework.security.web.session.HttpSessionEventPublisher;

/**
 * Configuração de autenticação e autorização.
 *
 * Duas cadeias de filtros, nesta ordem:
 *
 *  1. /api/** — HTTP Basic + stateless + CORS, para o app mobile. Falha de
 *     credencial devolve 401/403 em JSON ({@link ApiAuthenticationEntryPoint})
 *     e nunca 302 para a tela de login — redirect de formulário quebraria o app.
 *  2. Interface web (Thymeleaf) — login por formulário próprio em /login:
 *     - Toda página exige usuário autenticado, exceto o próprio login;
 *     - /usuarios/** e /auditoria/** são restritos ao perfil ADMIN;
 *     - Console do H2 liberado apenas para ADMIN (ferramenta de manutenção);
 *     - CSRF permanece LIGADO — os formulários Thymeleaf usam th:action, então
 *       o token é injetado automaticamente. O console H2 é a única exceção (ele
 *       não sabe mandar o token) e por isso está fora da proteção.
 *
 * O usuário inicial (admin) é criado pelo {@link UsuarioSeeder} no primeiro
 * boot com banco vazio.
 */
@Configuration
@EnableWebSecurity
@EnableMethodSecurity
@RequiredArgsConstructor
public class SecurityConfig {

    private final ApiAuthenticationEntryPoint apiAuthenticationEntryPoint;

    /**
     * Console H2 só existe em dev. A flag reflete {@code spring.h2.console.enabled}
     * (true apenas nos perfis dev/test), então em produção as regras que abrem o
     * console nem entram na cadeia de filtros.
     */
    @Value("${spring.h2.console.enabled:false}")
    private boolean h2ConsoleEnabled;

    @Bean
    public PasswordEncoder passwordEncoder() {
        // Strength explícito: o default pode mudar entre versões do Spring
        // Security e alterar silenciosamente a latência de autenticação.
        return new BCryptPasswordEncoder(10);
    }

    /**
     * Necessário para o {@code maximumSessions(1)} funcionar: publica os eventos
     * de criação/destruição de sessão para o SessionRegistry, senão sessões
     * encerradas nunca são liberadas do contador e o usuário fica travado.
     */
    @Bean
    public HttpSessionEventPublisher httpSessionEventPublisher() {
        return new HttpSessionEventPublisher();
    }

    /**
     * Recursos estáticos fora da cadeia de filtros: permitAll() ainda executa
     * ~15 filtros por CSS/JS/imagem; ignoring() pula a cadeia inteira.
     */
    @Bean
    public WebSecurityCustomizer ignorarEstaticos() {
        return web -> web.ignoring()
                .requestMatchers("/css/**", "/js/**", "/images/**", "/img/**",
                                 "/webjars/**", "/favicon.ico");
    }

    // =========================================================================
    // Cadeia #1 — API REST (/api/**): Basic Auth, stateless, respostas JSON
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
                    .requestMatchers(HttpMethod.DELETE, "/api/**").hasRole("ADMIN")
                    .requestMatchers(HttpMethod.POST, "/api/importacao/**").hasRole("ADMIN")
                    // Trilha de auditoria é admin-only na web; a API tem de seguir
                    // a mesma regra, senão um usuário PADRAO (ou a credencial do
                    // app mobile) leria o histórico inteiro de todos.
                    .requestMatchers("/api/auditoria/**").hasRole("ADMIN")
                    .anyRequest().authenticated()
            )
            .httpBasic(basic -> basic.authenticationEntryPoint(apiAuthenticationEntryPoint))
            .exceptionHandling(ex -> ex
                    .authenticationEntryPoint(apiAuthenticationEntryPoint)
                    .accessDeniedHandler(apiAuthenticationEntryPoint));
        return http.build();
    }

    // =========================================================================
    // Cadeia #2 — Interface web (Thymeleaf)
    // =========================================================================
    @Bean
    @Order(2)
    public SecurityFilterChain filterChain(HttpSecurity http) throws Exception {
        http
            .authorizeHttpRequests(auth -> auth
                    // Healthcheck do Docker/proxy: sem autenticação e sem
                    // detalhes de infra (show-details=never nas properties).
                    .requestMatchers("/actuator/health").permitAll()
                    // Estáticos ficam fora da cadeia — ver ignorarEstaticos().
                    .requestMatchers("/login").permitAll()
                    .requestMatchers("/usuarios/**").hasRole("ADMIN")
                    .requestMatchers("/auditoria/**").hasRole("ADMIN")
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
            // Sessão: fixação tratada pelo default (changeSessionId no login).
            // maximumSessions(1) impede que a mesma conta fique aberta em vários
            // lugares ao mesmo tempo — um novo login expira o anterior, reduzindo
            // a janela de uma credencial compartilhada/roubada.
            .sessionManagement(s -> s
                    .sessionFixation(SessionManagementConfigurer.SessionFixationConfigurer::changeSessionId)
                    .maximumSessions(1)
                    .maxSessionsPreventsLogin(false)
                    .expiredUrl("/login?expirado"))
            // O console do H2 roda em <frame> e não envia token CSRF.
            .csrf(csrf -> {
                    if (h2ConsoleEnabled) {
                        csrf.ignoringRequestMatchers("/h2-console/**");
                    }
            })
            .headers(headers -> {
                    // sameOrigin só é necessário para o console H2 (dev).
                    // Em produção, DENY — nada legítimo embute o app em iframe.
                    if (h2ConsoleEnabled) {
                        headers.frameOptions(HeadersConfigurer.FrameOptionsConfig::sameOrigin);
                    } else {
                        headers.frameOptions(HeadersConfigurer.FrameOptionsConfig::deny);
                    }
                    // Referrer-Policy: impede que URLs internas (com filtros/ids
                    // em query string) vazem no header Referer para sites externos.
                    headers.referrerPolicy(r -> r.policy(
                            ReferrerPolicyHeaderWriter.ReferrerPolicy.SAME_ORIGIN));
            });

        return http.build();
    }
}
