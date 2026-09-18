package com.fundacao.aualmoxarifado.domain;

import jakarta.persistence.*;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.*;

import java.time.LocalDateTime;

/**
 * RF16 - Metadados de um arquivo (PDF) anexado a uma {@link Compra}.
 *
 * NÃO armazenamos o conteúdo binário no banco (mantemos a tabela leve);
 * o arquivo físico é gravado em disco no diretório configurado por
 * {@code app.uploads.dir} e o caminho relativo é mantido em
 * {@link #caminhoArmazenado}. Para servir o download usamos
 * {@code GET /compras/{id}/anexos/{anexoId}/download}.
 */
@Entity
@Table(name = "anexo_compra", indexes = {
        @Index(name = "idx_anexo_compra_compra", columnList = "compra_id")
})
@Getter @Setter @NoArgsConstructor @AllArgsConstructor @Builder
public class AnexoCompra {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @NotNull
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "compra_id", nullable = false)
    private Compra compra;

    @NotNull
    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private TipoAnexoCompra tipo;

    /** Nome original do arquivo enviado pelo usuário. */
    @NotBlank
    @Column(nullable = false, length = 255)
    private String nomeOriginal;

    /** Caminho/identificador único do arquivo no storage local. */
    @NotBlank
    @Column(nullable = false, length = 500)
    private String caminhoArmazenado;

    /** Content-type detectado no upload (geralmente application/pdf). */
    @Column(length = 100)
    private String contentType;

    /** Tamanho em bytes — útil para auditoria e listagens. */
    @Column
    private Long tamanhoBytes;

    @NotNull
    @Column(nullable = false)
    private LocalDateTime dataUpload;
}
