package com.fundacao.aualmoxarifado.config;

import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.EnableScheduling;

/** Habilita tarefas agendadas (reenvio da fila de integração com o Patrimônio). */
@Configuration
@EnableScheduling
public class AgendamentoConfig {
}
