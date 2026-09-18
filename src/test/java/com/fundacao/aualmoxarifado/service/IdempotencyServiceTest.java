package com.fundacao.aualmoxarifado.service;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.UUID;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class IdempotencyServiceTest {

    private IdempotencyService service;

    @BeforeEach
    void setUp() {
        service = new IdempotencyService();
    }

    @Test
    void executar_semChave_invocaOperacaoNormalmente() {
        AtomicInteger contador = new AtomicInteger();

        String r1 = service.executar(null, () -> "v" + contador.incrementAndGet());
        String r2 = service.executar(" ", () -> "v" + contador.incrementAndGet());
        String r3 = service.executar("", () -> "v" + contador.incrementAndGet());

        assertThat(r1).isEqualTo("v1");
        assertThat(r2).isEqualTo("v2");
        assertThat(r3).isEqualTo("v3");
        assertThat(contador.get()).isEqualTo(3);
    }

    @Test
    void executar_mesmaChave_devolveResultadoCacheadoSemReexecutar() {
        AtomicInteger contador = new AtomicInteger();
        String chave = UUID.randomUUID().toString();

        String primeira = service.executar(chave, () -> "v" + contador.incrementAndGet());
        String segunda  = service.executar(chave, () -> "v" + contador.incrementAndGet());
        String terceira = service.executar(chave, () -> "v" + contador.incrementAndGet());

        assertThat(primeira).isEqualTo("v1");
        assertThat(segunda).isEqualTo("v1");
        assertThat(terceira).isEqualTo("v1");
        assertThat(contador.get())
                .as("operação deve rodar apenas uma vez quando a chave é a mesma")
                .isEqualTo(1);
    }

    @Test
    void executar_chavesDistintas_invocamOperacaoSeparadamente() {
        AtomicInteger contador = new AtomicInteger();

        String r1 = service.executar("k1", () -> "v" + contador.incrementAndGet());
        String r2 = service.executar("k2", () -> "v" + contador.incrementAndGet());

        assertThat(r1).isEqualTo("v1");
        assertThat(r2).isEqualTo("v2");
    }

    @Test
    void executar_quandoOperacaoFalha_naoCacheiaEPermiteRetry() {
        AtomicInteger contador = new AtomicInteger();
        String chave = UUID.randomUUID().toString();

        // Primeira chamada: falha.
        assertThatThrownBy(() -> service.executar(chave, () -> {
            contador.incrementAndGet();
            throw new IllegalStateException("estoque insuficiente");
        })).isInstanceOf(IllegalStateException.class);

        // Segunda chamada com a mesma chave: deve reexecutar (falha não foi cacheada).
        String resultado = service.executar(chave, () -> {
            contador.incrementAndGet();
            return "sucesso";
        });

        assertThat(resultado).isEqualTo("sucesso");
        assertThat(contador.get())
                .as("erro não deve ser cacheado — retry com mesma chave deve reexecutar")
                .isEqualTo(2);
    }
}
