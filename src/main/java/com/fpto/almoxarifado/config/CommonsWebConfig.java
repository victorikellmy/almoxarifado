package com.fpto.almoxarifado.config;

import com.fpto.commons.web.CorrelationIdFilter;
import org.springframework.boot.web.servlet.FilterRegistrationBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.Ordered;

/**
 * Integração com a biblioteca compartilhada {@code fpto-commons}.
 *
 * <p>Registra o {@link CorrelationIdFilter} com a maior precedência possível,
 * garantindo um {@code X-Correlation-Id} por requisição (propagado no MDC e no
 * header de resposta) para rastreio distribuído entre os serviços do ecossistema.
 */
@Configuration
public class CommonsWebConfig {

    @Bean
    public FilterRegistrationBean<CorrelationIdFilter> correlationIdFilter() {
        FilterRegistrationBean<CorrelationIdFilter> registration =
                new FilterRegistrationBean<>(new CorrelationIdFilter());
        registration.setOrder(Ordered.HIGHEST_PRECEDENCE);
        registration.addUrlPatterns("/*");
        return registration;
    }
}
