package com.fundacao.aualmoxarifado.service.report.exporter;

import java.io.OutputStream;
import java.util.List;

/**
 * Contrato comum dos exporters de relatório.
 *
 * <p>Cada implementação ({@code CsvExporter}, {@code PdfExporter},
 * {@code XlsxExporter}) sabe como serializar uma tabela genérica
 * (cabeçalhos + linhas) num formato específico.</p>
 */
public interface Exporter {

    /** Tipo MIME do conteúdo gerado (usado no header HTTP). */
    String contentType();

    /** Sufixo do arquivo (sem ponto). */
    String fileExtension();

    /**
     * Escreve a tabela no {@code OutputStream}.
     *
     * @param titulo     título do relatório (cabeçalho do arquivo, quando aplicável)
     * @param cabecalhos nomes das colunas
     * @param linhas     dados — cada linha é uma lista de células serializadas como String
     * @param out        stream de saída (será fechado pelo chamador)
     */
    void exportar(String titulo, List<String> cabecalhos, List<List<String>> linhas, OutputStream out)
            throws Exception;
}
