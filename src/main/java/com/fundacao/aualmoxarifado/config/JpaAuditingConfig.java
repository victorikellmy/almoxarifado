package com.fundacao.aualmoxarifado.config;

import org.springframework.context.annotation.Configuration;
import org.springframework.data.jpa.repository.config.EnableJpaAuditing;

/**
 * Habilita a auditoria automática do Spring Data JPA
 * ({@code @CreatedDate}, {@code @LastModifiedDate},
 * {@code @CreatedBy}, {@code @LastModifiedBy}).
 *
 * <p>O bean que resolve "quem está fazendo a alteração" é o
 * {@link com.fundacao.aualmoxarifado.security.AuditorAwareImpl}
 * (lê o {@code SecurityContextHolder}).</p>
 */
@Configuration
@EnableJpaAuditing(auditorAwareRef = "auditorAwareImpl")
public class JpaAuditingConfig {
}
