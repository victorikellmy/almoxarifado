package com.fundacao.aualmoxarifado.security;

import org.springframework.data.domain.AuditorAware;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;

import java.util.Optional;

/**
 * Fonte do "quem fez isso" para as colunas {@code @CreatedBy} e
 * {@code @LastModifiedBy} do Spring Data.
 *
 * <p>Em operações de sistema (bootstrap, importação) retorna "SYSTEM".</p>
 */
@Component
public class AuditorAwareImpl implements AuditorAware<String> {

    private static final String SYSTEM = "SYSTEM";

    @Override
    public Optional<String> getCurrentAuditor() {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth == null || !auth.isAuthenticated() || "anonymousUser".equals(auth.getPrincipal())) {
            return Optional.of(SYSTEM);
        }
        return Optional.of(auth.getName());
    }
}
