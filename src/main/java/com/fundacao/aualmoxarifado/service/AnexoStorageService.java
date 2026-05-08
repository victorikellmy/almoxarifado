package com.fundacao.aualmoxarifado.service;

import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.io.Resource;
import org.springframework.core.io.UrlResource;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import jakarta.annotation.PostConstruct;
import java.io.IOException;
import java.net.MalformedURLException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardCopyOption;
import java.util.UUID;

/**
 * Serviço de armazenamento físico de anexos (PDFs) usados pelo Módulo de Compras.
 *
 * Estratégia: o conteúdo binário NUNCA fica no banco. Os arquivos são salvos
 * no diretório local configurado em {@code app.uploads.dir} (padrão {@code ./uploads}).
 * O nome real do arquivo em disco é prefixado por um UUID, garantindo unicidade
 * mesmo quando dois usuários enviam arquivos com o mesmo nome.
 *
 * Separação de responsabilidades:
 *   - Este service NÃO conhece a entidade {@code AnexoCompra}; apenas grava bytes
 *     e devolve metadados. Quem amarra os metadados ao registro JPA é o
 *     {@link CompraService}.
 */
@Slf4j
@Service
public class AnexoStorageService {

    private final Path raizUploads;

    public AnexoStorageService(@Value("${app.uploads.dir:./uploads}") String diretorio) {
        this.raizUploads = Paths.get(diretorio).toAbsolutePath().normalize();
    }

    /**
     * Garante que o diretório raiz de uploads existe quando o bean sobe.
     * Falhar aqui é preferível a falhar no primeiro upload em produção.
     */
    @PostConstruct
    void inicializarDiretorio() {
        try {
            Files.createDirectories(raizUploads);
            log.info("[AnexoStorage] Diretório de uploads pronto em {}", raizUploads);
        } catch (IOException ex) {
            throw new IllegalStateException(
                    "Não foi possível criar o diretório de uploads: " + raizUploads, ex);
        }
    }

    /**
     * Persiste o arquivo recebido em disco e devolve um {@link ArquivoArmazenado}
     * com o caminho relativo (gravado em {@code AnexoCompra.caminhoArmazenado})
     * e os metadados básicos (nome original, content-type, tamanho).
     *
     * @param arquivo MultipartFile recebido pelo controller
     * @param subpasta nome da subpasta — recomenda-se {@code "compras/{id}"} para
     *                 organizar os arquivos por registro
     */
    public ArquivoArmazenado salvar(MultipartFile arquivo, String subpasta) {
        if (arquivo == null || arquivo.isEmpty()) {
            throw new IllegalArgumentException("Arquivo vazio ou ausente.");
        }

        String nomeOriginal = arquivo.getOriginalFilename() != null
                ? Paths.get(arquivo.getOriginalFilename()).getFileName().toString()
                : "anexo.pdf";

        // Aceitamos apenas PDF para este módulo (regra de UI também é validada no <input accept>).
        String contentType = arquivo.getContentType();
        if (contentType != null
                && !contentType.equalsIgnoreCase("application/pdf")
                && !nomeOriginal.toLowerCase().endsWith(".pdf")) {
            throw new IllegalArgumentException("Apenas arquivos PDF são aceitos. Recebido: " + contentType);
        }

        try {
            Path destinoPasta = raizUploads.resolve(subpasta).normalize();
            // Defesa contra path traversal: o destino deve permanecer abaixo da raiz.
            if (!destinoPasta.startsWith(raizUploads)) {
                throw new IllegalArgumentException("Subpasta inválida: " + subpasta);
            }
            Files.createDirectories(destinoPasta);

            String nomeUnico = UUID.randomUUID() + "_" + nomeOriginal;
            Path destinoArquivo = destinoPasta.resolve(nomeUnico);

            try (var in = arquivo.getInputStream()) {
                Files.copy(in, destinoArquivo, StandardCopyOption.REPLACE_EXISTING);
            }

            // Guardamos sempre o caminho RELATIVO à raiz para portabilidade do banco.
            String relativo = raizUploads.relativize(destinoArquivo).toString().replace('\\', '/');

            return new ArquivoArmazenado(
                    nomeOriginal,
                    relativo,
                    contentType,
                    arquivo.getSize());
        } catch (IOException ex) {
            throw new IllegalStateException("Falha ao gravar o anexo em disco.", ex);
        }
    }

    /**
     * Devolve um {@link Resource} pronto para ser servido pelo controller no
     * endpoint de download. Trata path traversal verificando que o arquivo
     * resolvido permanece abaixo da raiz de uploads.
     */
    public Resource carregar(String caminhoRelativo) {
        try {
            Path arquivo = raizUploads.resolve(caminhoRelativo).normalize();
            if (!arquivo.startsWith(raizUploads)) {
                throw new IllegalArgumentException("Caminho inválido: " + caminhoRelativo);
            }
            Resource resource = new UrlResource(arquivo.toUri());
            if (!resource.exists() || !resource.isReadable()) {
                throw new IllegalStateException("Arquivo não encontrado: " + caminhoRelativo);
            }
            return resource;
        } catch (MalformedURLException ex) {
            throw new IllegalStateException("URL malformada para o anexo: " + caminhoRelativo, ex);
        }
    }

    /** DTO interno com os metadados que precisam virar colunas em AnexoCompra. */
    public record ArquivoArmazenado(
            String nomeOriginal,
            String caminhoRelativo,
            String contentType,
            Long tamanhoBytes
    ) {}
}
