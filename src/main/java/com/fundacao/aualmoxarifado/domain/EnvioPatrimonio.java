package com.fundacao.aualmoxarifado.domain;

import jakarta.persistence.*;
import lombok.*;

import java.time.LocalDateTime;

/**
 * Outbox da integração Almoxarifado → Patrimônio.
 *
 * Quando uma compra é recebida com "será patrimoniado", este registro é criado na
 * mesma transação da baixa. O envio HTTP acontece depois (imediatamente e, em caso
 * de falha ou sistema de Patrimônio fora do ar, pelo reenvio agendado). Assim a
 * baixa nunca falha por causa da integração e nenhum recebimento se perde.
 */
@Entity
@Table(name = "envio_patrimonio", indexes = {
        @Index(name = "idx_envio_patrimonio_status", columnList = "status"),
        @Index(name = "idx_envio_patrimonio_compra", columnList = "compra_id")
})
@Getter @Setter @NoArgsConstructor @AllArgsConstructor @Builder
public class EnvioPatrimonio {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @OneToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "compra_id", nullable = false, unique = true)
    private Compra compra;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 15)
    @Builder.Default
    private StatusEnvioPatrimonio status = StatusEnvioPatrimonio.PENDENTE;

    @Column(nullable = false)
    @Builder.Default
    private Integer tentativas = 0;

    @Column(name = "ultimo_erro", length = 1000)
    private String ultimoErro;

    /** Identificador da pendência criada no Patrimônio (devolvido pela API dele). */
    @Column(name = "id_externo", length = 60)
    private String idExterno;

    @Column(name = "criado_em", nullable = false)
    @Builder.Default
    private LocalDateTime criadoEm = LocalDateTime.now();

    @Column(name = "ultima_tentativa_em")
    private LocalDateTime ultimaTentativaEm;

    @Column(name = "enviado_em")
    private LocalDateTime enviadoEm;
}
