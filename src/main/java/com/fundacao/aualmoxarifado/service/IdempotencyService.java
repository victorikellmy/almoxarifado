package com.fundacao.aualmoxarifado.service;

import com.github.benmanes.caffeine.cache.Cache;
import com.github.benmanes.caffeine.cache.Caffeine;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.util.function.Supplier;

/**
 * Garante idempotência de operações sensíveis (saída de material via bipagem)
 * a partir de um header {@code Idempotency-Key} enviado pelo cliente.
 *
 * <p>Estratégia: cache em memória (Caffeine) com TTL de 10 minutos. Se a mesma
 * chave chega novamente dentro do TTL, devolvemos o resultado anteriormente
 * computado <b>sem reexecutar a operação</b> — esse é o ponto que protege
 * contra leituras duplicadas do leitor de código de barras / cliques duplos
 * no app.</p>
 *
 * <p>Quando a chave é {@code null} ou em branco a operação é executada
 * normalmente sem cache — mantém compatibilidade com clientes legados que
 * ainda não enviam o header.</p>
 *
 * <p>Detalhe de concorrência: {@link Cache#get(Object, java.util.function.Function)}
 * é atômico por chave; chamadas simultâneas com a mesma chave esperam pelo
 * primeiro executor — não geram operação duplicada.</p>
 *
 * <p>Falhas (exceptions) <b>não são cacheadas</b>: se a saída falhou (ex.
 * estoque insuficiente), o cliente pode reenviar com a mesma chave após
 * corrigir o problema.</p>
 *
 * <p>Limitação conhecida: cache local ao processo. Se o backend for escalado
 * horizontalmente, migrar para Redis/Caffeine distribuído.</p>
 */
@Service
@Slf4j
public class IdempotencyService {

    private final Cache<String, Object> cache = Caffeine.newBuilder()
            .expireAfterWrite(Duration.ofMinutes(10))
            .maximumSize(10_000)
            .recordStats()
            .build();

    @SuppressWarnings("unchecked")
    public <T> T executar(String chave, Supplier<T> operacao) {
        if (chave == null || chave.isBlank()) {
            return operacao.get();
        }

        Object existente = cache.getIfPresent(chave);
        if (existente != null) {
            log.info("Idempotency-Key replay: chave='{}' — devolvendo resposta em cache", chave);
            return (T) existente;
        }

        return (T) cache.get(chave, k -> {
            log.debug("Idempotency-Key novo: chave='{}' — executando operação", k);
            return operacao.get();
        });
    }
}
