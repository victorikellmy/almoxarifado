package com.fundacao.aualmoxarifado.config;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.cors.CorsConfiguration;
import org.springframework.web.cors.CorsConfigurationSource;
import org.springframework.web.cors.UrlBasedCorsConfigurationSource;

import java.util.List;

/**
 * CORS aberto apenas para os endpoints REST consumidos pelo app mobile.
 * As telas Thymeleaf não são afetadas.
 *
 * <p>Exposto como {@link CorsConfigurationSource} (e não via WebMvcConfigurer)
 * para que o Spring Security aplique o CORS na cadeia de filtros: o preflight
 * {@code OPTIONS} chega sem Authorization e, sem essa integração, receberia
 * 401 antes de chegar ao MVC.</p>
 */
@Configuration
public class CorsConfig {

    /**
     * Origens permitidas para chamadas cross-origin de navegador à API.
     * Default {@code *} porque o consumidor principal é o app Android nativo
     * (OkHttp, não sujeito a CORS). Em produção, defina
     * {@code app.cors.allowed-origins} com a allowlist do(s) front(s) web —
     * ex.: {@code https://almoxarifado.fasaudefpto.com.br}.
     */
    @Value("${app.cors.allowed-origins:*}")
    private List<String> allowedOrigins;

    @Bean
    public CorsConfigurationSource corsConfigurationSource() {
        CorsConfiguration config = new CorsConfiguration();
        config.setAllowedOriginPatterns(allowedOrigins);
        config.setAllowedMethods(List.of("GET", "POST", "PUT", "DELETE", "OPTIONS"));
        config.setAllowedHeaders(List.of("*"));
        config.setAllowCredentials(false);
        config.setMaxAge(3600L);

        UrlBasedCorsConfigurationSource source = new UrlBasedCorsConfigurationSource();
        source.registerCorsConfiguration("/api/**", config);
        return source;
    }
}
