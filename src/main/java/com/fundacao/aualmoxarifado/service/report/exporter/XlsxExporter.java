package com.fundacao.aualmoxarifado.service.report.exporter;

import org.apache.poi.ss.usermodel.*;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.springframework.stereotype.Component;

import java.io.OutputStream;
import java.util.List;

/**
 * Exporta para XLSX usando Apache POI.
 */
@Component
public class XlsxExporter implements Exporter {

    @Override public String contentType()    { return "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet"; }
    @Override public String fileExtension()  { return "xlsx"; }

    @Override
    public void exportar(String titulo, List<String> cabecalhos, List<List<String>> linhas, OutputStream out)
            throws Exception {
        try (Workbook wb = new XSSFWorkbook()) {
            Sheet sheet = wb.createSheet(abreviar(titulo));

            // Estilo do cabeçalho
            CellStyle headerStyle = wb.createCellStyle();
            Font headerFont = wb.createFont();
            headerFont.setBold(true);
            headerStyle.setFont(headerFont);
            headerStyle.setAlignment(HorizontalAlignment.CENTER);

            Row header = sheet.createRow(0);
            for (int c = 0; c < cabecalhos.size(); c++) {
                Cell cell = header.createCell(c);
                cell.setCellValue(cabecalhos.get(c));
                cell.setCellStyle(headerStyle);
            }

            int r = 1;
            for (List<String> linha : linhas) {
                Row row = sheet.createRow(r++);
                for (int c = 0; c < linha.size(); c++) {
                    row.createCell(c).setCellValue(linha.get(c) != null ? linha.get(c) : "");
                }
            }

            for (int c = 0; c < cabecalhos.size(); c++) {
                sheet.autoSizeColumn(c);
            }

            wb.write(out);
        }
    }

    /** Nomes de aba no Excel não podem exceder 31 caracteres. */
    private static String abreviar(String s) {
        if (s == null) return "Relatorio";
        return s.length() <= 31 ? s : s.substring(0, 31);
    }
}
