package com.fundacao.aualmoxarifado.service.report.exporter;

import com.lowagie.text.*;
import com.lowagie.text.pdf.PdfPCell;
import com.lowagie.text.pdf.PdfPTable;
import com.lowagie.text.pdf.PdfWriter;
import org.springframework.stereotype.Component;

import java.awt.Color;
import java.io.OutputStream;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.List;

/**
 * Exporta para PDF usando OpenPDF.
 */
@Component
public class PdfExporter implements Exporter {

    private static final DateTimeFormatter FMT_DATA = DateTimeFormatter.ofPattern("dd/MM/yyyy HH:mm");

    /** Linhas acumuladas antes de cada flush incremental da tabela. */
    private static final int LOTE_FLUSH = 1000;

    @Override public String contentType()    { return "application/pdf"; }
    @Override public String fileExtension()  { return "pdf"; }

    @Override
    public void exportar(String titulo, List<String> cabecalhos, List<List<String>> linhas, OutputStream out)
            throws Exception {
        Document doc = new Document(PageSize.A4.rotate(), 30, 30, 30, 30);
        try {
            PdfWriter.getInstance(doc, out);
            doc.open();

            Font tituloFont = new Font(Font.HELVETICA, 14, Font.BOLD);
            Paragraph p = new Paragraph(titulo, tituloFont);
            p.setAlignment(Element.ALIGN_CENTER);
            doc.add(p);

            Font subtFont = new Font(Font.HELVETICA, 9, Font.ITALIC, Color.DARK_GRAY);
            Paragraph sub = new Paragraph(
                    "Gerado em " + LocalDateTime.now().format(FMT_DATA), subtFont);
            sub.setAlignment(Element.ALIGN_CENTER);
            sub.setSpacingAfter(12);
            doc.add(sub);

            PdfPTable table = new PdfPTable(cabecalhos.size());
            table.setWidthPercentage(100);
            // Header repetido a cada página e necessário para o flush incremental.
            table.setHeaderRows(1);

            Font headerFont = new Font(Font.HELVETICA, 10, Font.BOLD, Color.WHITE);
            for (String h : cabecalhos) {
                PdfPCell cell = new PdfPCell(new Phrase(h, headerFont));
                cell.setBackgroundColor(new Color(23, 90, 138));
                cell.setHorizontalAlignment(Element.ALIGN_CENTER);
                cell.setPadding(6f);
                table.addCell(cell);
            }

            Font cellFont = new Font(Font.HELVETICA, 9, Font.NORMAL);
            boolean alt = false;
            int pendentes = 0;
            for (List<String> linha : linhas) {
                for (String v : linha) {
                    PdfPCell cell = new PdfPCell(new Phrase(v != null ? v : "", cellFont));
                    cell.setPadding(4f);
                    if (alt) cell.setBackgroundColor(new Color(245, 247, 250));
                    table.addCell(cell);
                }
                alt = !alt;
                // Flush incremental: serializa e descarta as linhas já escritas
                // a cada lote, em vez de montar a tabela inteira no heap.
                if (++pendentes == LOTE_FLUSH) {
                    doc.add(table);
                    table.deleteBodyRows();
                    table.setSkipFirstHeader(true);
                    pendentes = 0;
                }
            }
            doc.add(table);
        } finally {
            doc.close();
        }
    }
}
