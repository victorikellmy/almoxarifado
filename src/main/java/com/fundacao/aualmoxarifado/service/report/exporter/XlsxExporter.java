package com.fundacao.aualmoxarifado.service.report.exporter;

import org.apache.poi.ss.usermodel.Cell;
import org.apache.poi.ss.usermodel.CellStyle;
import org.apache.poi.ss.usermodel.Font;
import org.apache.poi.ss.usermodel.HorizontalAlignment;
import org.apache.poi.ss.usermodel.Row;
import org.apache.poi.ss.usermodel.Sheet;
import org.apache.poi.xssf.streaming.SXSSFWorkbook;
import org.springframework.stereotype.Component;

import java.io.OutputStream;
import java.util.List;

/**
 * Exporta para XLSX usando Apache POI em modo <b>streaming</b> (SXSSF):
 * apenas uma janela de linhas fica no heap; o restante vai para arquivo
 * temporário. Memória O(1) no número de linhas, contra O(n) do XSSF.
 */
@Component
public class XlsxExporter implements Exporter {

    /** Linhas mantidas em memória antes de irem para o buffer em disco. */
    private static final int JANELA_LINHAS = 100;
    /** Largura máxima de coluna, em caracteres (evita colunas quilométricas). */
    private static final int LARGURA_MAX = 60;

    @Override public String contentType()    { return "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet"; }
    @Override public String fileExtension()  { return "xlsx"; }

    @Override
    public void exportar(String titulo, List<String> cabecalhos, List<List<String>> linhas, OutputStream out)
            throws Exception {
        SXSSFWorkbook wb = new SXSSFWorkbook(JANELA_LINHAS);
        try {
            Sheet sheet = wb.createSheet(abreviar(titulo));

            // Estilo do cabeçalho
            CellStyle headerStyle = wb.createCellStyle();
            Font headerFont = wb.createFont();
            headerFont.setBold(true);
            headerStyle.setFont(headerFont);
            headerStyle.setAlignment(HorizontalAlignment.CENTER);

            // Larguras calculadas durante a escrita (autoSizeColumn exigiria a
            // planilha inteira em memória e é O(linhas × colunas) com métricas
            // de fonte — o método mais caro do POI).
            int[] larguras = new int[cabecalhos.size()];

            Row header = sheet.createRow(0);
            for (int c = 0; c < cabecalhos.size(); c++) {
                Cell cell = header.createCell(c);
                cell.setCellValue(cabecalhos.get(c));
                cell.setCellStyle(headerStyle);
                larguras[c] = cabecalhos.get(c).length();
            }

            int r = 1;
            for (List<String> linha : linhas) {
                Row row = sheet.createRow(r++);
                for (int c = 0; c < linha.size(); c++) {
                    String v = linha.get(c) != null ? linha.get(c) : "";
                    row.createCell(c).setCellValue(v);
                    if (c < larguras.length && v.length() > larguras[c]) {
                        larguras[c] = v.length();
                    }
                }
            }

            for (int c = 0; c < larguras.length; c++) {
                sheet.setColumnWidth(c, Math.min(larguras[c] + 2, LARGURA_MAX) * 256);
            }

            wb.write(out);
        } finally {
            // close() também apaga os arquivos temporários do streaming (POI 5+).
            wb.close();
        }
    }

    /** Nomes de aba no Excel não podem exceder 31 caracteres. */
    private static String abreviar(String s) {
        if (s == null) return "Relatorio";
        return s.length() <= 31 ? s : s.substring(0, 31);
    }
}
