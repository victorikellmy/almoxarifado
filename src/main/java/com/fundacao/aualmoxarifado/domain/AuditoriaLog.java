package com.fundacao.aualmoxarifado.domain;

import jakarta.persistence.*;
import lombok.*;
import org.springframework.data.annotation.CreatedBy;
import org.springframework.data.annotation.CreatedDate;
import org.springframework.data.jpa.domain.support.AuditingEntityListener;

import java.time.LocalDateTime;

/**
 * Trilha de auditoria de operações sensíveis (saídas, aprovações, entradas).
 *
 * <p>Cada operação relevante grava UMA linha aqui dentro da mesma transação
 * da operação — se a operação dá rollback, o log também some. Isso garante
 * que não exista log de saída que "não aconteceu".</p>
 *
 * <p>Para auditar tentativas FALHAS (ex.: usuário tentou aprovar sem saldo),
 * usa-se {@code @Transactional(REQUIRES_NEW)} no service correspondente —
 * fora do escopo desta primeira iteração.</p>
 *
 * <p>Campo {@code detalhes} guarda um resumo legível dos itens (SKU × qtd) —
 * suficiente para reconstrução manual. Se no futuro precisar de query
 * estruturada por item, criar tabela {@code auditoria_log_item} 1:N.</p>
 */
@Entity
@Table(name = "auditoria_log",
       indexes = {
               @Index(name = "idx_audit_timestamp", columnList = "timestamp"),
               @Index(name = "idx_audit_acao",      columnList = "acao"),
               @Index(name = "idx_audit_usuario",   columnList = "usuario"),
               @Index(name = "idx_audit_entidade",  columnList = "entidade,entidade_id")
       })
@EntityListeners(AuditingEntityListener.class)
@Getter @Setter @NoArgsConstructor @AllArgsConstructor @Builder
public class AuditoriaLog {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @CreatedDate
    @Column(nullable = false, updatable = false)
    private LocalDateTime timestamp;

    /** Login do usuário autenticado (ou "SYSTEM" para operações automáticas). */
    @CreatedBy
    @Column(nullable = false, updatable = false, length = 120)
    private String usuario;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 40)
    private AcaoAuditoria acao;

    /** Tipo de entidade afetada — "Movimentacao", "Material", etc. */
    @Column(nullable = false, length = 60)
    private String entidade;

    /** ID da entidade afetada (FK lógica, sem constraint para não acoplar). */
    @Column(name = "entidade_id")
    private Long entidadeId;

    /** Setor de destino, quando aplicável (saída/compra direta). */
    @Column(length = 120)
    private String setor;

    /** Resumo textual dos itens — "10× ODO-CON-00001, 5× FAR-MED-00042 (+1 item)". */
    @Column(length = 2000)
    private String detalhes;

    /** Origem da chamada (IP do cliente HTTP). Vazio para operações non-web. */
    @Column(name = "ip_origem", length = 45)
    private String ipOrigem;

    /** Header {@code Idempotency-Key} da requisição que originou (quando houver). */
    @Column(name = "idempotency_key", length = 64)
    private String idempotencyKey;
}
