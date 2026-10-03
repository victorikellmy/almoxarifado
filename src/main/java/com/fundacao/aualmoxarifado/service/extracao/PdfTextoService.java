package com.fundacao.aualmoxarifado.service.extracao;

import org.apache.pdfbox.Loader;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.text.PDFTextStripper;
import org.springframework.stereotype.Service;

import java.io.IOException;

/** Extrai a camada de texto de um PDF usando Apache PDFBox. */
@Service
public class PdfTextoService {

    public record TextoPdf(String texto, int paginas) {

        /** Heurística: menos de ~80 caracteres úteis por página indica PDF escaneado (imagem). */
        public boolean provavelmenteEscaneado() {
            if (paginas <= 0) return true;
            long uteis = texto == null ? 0 : texto.chars().filter(Character::isLetterOrDigit).count();
            return uteis / paginas < 80;
        }
    }

    public TextoPdf extrair(byte[] pdf) {
        try (PDDocument doc = Loader.loadPDF(pdf)) {
            PDFTextStripper stripper = new PDFTextStripper();
            stripper.setSortByPosition(true);
            stripper.setLineSeparator("\n");
            String texto = stripper.getText(doc);
            return new TextoPdf(texto, doc.getNumberOfPages());
        } catch (IOException e) {
            throw new IllegalArgumentException("Não foi possível ler o PDF: " + e.getMessage(), e);
        }
    }
}
