package com.fundacao.aualmoxarifado.config;

import com.github.benmanes.caffeine.cache.Caffeine;
import org.springframework.cache.CacheManager;
import org.springframework.cache.annotation.EnableCaching;
import org.springframework.cache.caffeine.CaffeineCacheManager;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.time.Duration;

/**
 * Cache de aplicação (Caffeine) para as agregações caras dos relatórios.
 *
 * <p>Um fluxo típico de usuário (abrir a página + exportar CSV/XLSX/PDF do
 * mesmo período) reagregava as mesmas 3 queries de GROUP BY 4 vezes. Com o
 * cache, só a primeira paga o custo.</p>
 *
 * <p>Correção garantida por dois mecanismos: TTL curto e evicção explícita em
 * toda escrita de movimentação ({@code @CacheEvict} no MovimentacaoService).</p>
 */
@Configuration
@EnableCaching
public class CacheConfig {

    /** Caches das agregações de relatório — nomes usados nos @Cacheable. */
    public static final String CACHE_REL_MENSAL     = "relatorioMensal";
    public static final String CACHE_REL_TRIMESTRAL = "relatorioTrimestral";
    public static final String CACHE_REL_ANUAL      = "relatorioAnual";

    @Bean
    public CacheManager cacheManager() {
        CaffeineCacheManager manager = new CaffeineCacheManager(
                CACHE_REL_MENSAL, CACHE_REL_TRIMESTRAL, CACHE_REL_ANUAL);
        manager.setCaffeine(Caffeine.newBuilder()
                .expireAfterWrite(Duration.ofMinutes(10))
                .maximumSize(200));
        return manager;
    }
}
