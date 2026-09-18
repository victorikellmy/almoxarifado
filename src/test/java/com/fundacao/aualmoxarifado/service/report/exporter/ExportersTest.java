package com.fundacao.aualmoxarifado.service.report.exporter;

import org.apache.poi.ss.usermodel.Sheet;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Testes de contrato e compatibilidade dos exporters: o arquivo gerado precisa
 * ser legível pelo formato alvo (POI relê o XLSX, o PDF tem header válido) e o
 * contrato do {@link Exporter} — não fechar o stream do chamador — precisa
 * valer para permitir escrita direta na resposta HTTP.
 */
class ExportersTest {

    private static final List<String> CABECALHOS = List.of("Col A", "Col B", "Col C");

    private static List<List<String>> linhas(int n) {
        List<List<String>> out = new ArrayList<>(n);
        for (int i = 0; i < n; i++) {
            out.add(List.of("valor " + i, String.valueOf(i), "R$ " + i + ",00"));
        }
        return out;
    }

    /** OutputStream que acusa se algum exporter fechar o stream do chamador. */
    private static final class StreamVigiado extends ByteArrayOutputStream {
        boolean fechado = false;
        @Override public void close() { fechado = true; }
    }

    @Nested
    class Csv {

        private final CsvExporter exporter = new CsvExporter();

        @Test
        void geraCsvComBom_separadorPontoEVirgula_eEscapes() throws Exception {
            ByteArrayOutputStream bos = new ByteArrayOutputStream();
            exporter.exportar("t", List.of("a", "b"),
                    List.of(
                            List.of("com;separador", "normal"),
                            List.of("com \"aspas\"", "multi\nlinha"),
                            java.util.Arrays.asList(null, "")
                    ), bos);

            String csv = bos.toString(StandardCharsets.UTF_8);
            assertThat(csv).startsWith("﻿");
            assertThat(csv).contains("a;b");
            // célula com separador vira quoted
            assertThat(csv).contains("\"com;separador\";normal");
            // aspas internas duplicadas
            assertThat(csv).contains("\"com \"\"aspas\"\"\"");
            // null e vazio viram células vazias sem NPE
            assertThat(csv).contains("\n;");
        }

        @Test
        void naoFechaOStreamDoChamador() throws Exception {
            StreamVigiado out = new StreamVigiado();
            exporter.exportar("t", CABECALHOS, linhas(3), out);
            assertThat(out.fechado)
                    .as("contrato do Exporter: o stream é fechado pelo CHAMADOR")
                    .isFalse();
            assertThat(out.size()).isPositive();
        }
    }

    @Nested
    class Xlsx {

        private final XlsxExporter exporter = new XlsxExporter();

        @Test
        void arquivoGeradoEmStreaming_eLegivelPeloProprioPoi() throws Exception {
            ByteArrayOutputStream bos = new ByteArrayOutputStream();
            exporter.exportar("Relatorio de teste", CABECALHOS, linhas(250), bos);

            try (XSSFWorkbook lido = new XSSFWorkbook(new ByteArrayInputStream(bos.toByteArray()))) {
                Sheet sheet = lido.getSheetAt(0);
                assertThat(sheet.getSheetName()).isEqualTo("Relatorio de teste");
                assertThat(sheet.getRow(0).getCell(0).getStringCellValue()).isEqualTo("Col A");
                // header + 250 linhas de dados
                assertThat(sheet.getLastRowNum()).isEqualTo(250);
                assertThat(sheet.getRow(250).getCell(0).getStringCellValue()).isEqualTo("valor 249");
                // largura calculada durante a escrita (sem autoSizeColumn)
                assertThat(sheet.getColumnWidth(0)).isGreaterThan(0);
            }
        }

        @Test
        void nomeDeAbaComMaisDe31Chars_eTruncado() throws Exception {
            ByteArrayOutputStream bos = new ByteArrayOutputStream();
            String titulo = "Um titulo exageradamente longo para aba de excel";
            exporter.exportar(titulo, CABECALHOS, linhas(1), bos);

            try (XSSFWorkbook lido = new XSSFWorkbook(new ByteArrayInputStream(bos.toByteArray()))) {
                assertThat(lido.getSheetAt(0).getSheetName()).hasSize(31);
            }
        }
    }

    @Nested
    class Pdf {

        private final PdfExporter exporter = new PdfExporter();

        @Test
        void geraPdfValido() throws Exception {
            ByteArrayOutputStream bos = new ByteArrayOutputStream();
            exporter.exportar("Relatório PDF", CABECALHOS, linhas(20), bos);

            byte[] pdf = bos.toByteArray();
            assertThat(new String(pdf, 0, 5, StandardCharsets.US_ASCII)).isEqualTo("%PDF-");
            assertThat(pdf.length).isGreaterThan(500);
        }

        @Test
        void volumeAcimaDoLoteDeFlush_naoQuebraOFlushIncremental() throws Exception {
            ByteArrayOutputStream bos = new ByteArrayOutputStream();
            // 2.500 linhas cruzam 2 flushes incrementais (lote = 1.000)
            exporter.exportar("Relatório grande", CABECALHOS, linhas(2_500), bos);

            byte[] pdf = bos.toByteArray();
            assertThat(new String(pdf, 0, 5, StandardCharsets.US_ASCII)).isEqualTo("%PDF-");
            assertThat(pdf.length).isGreaterThan(10_000);
        }
    }
}
