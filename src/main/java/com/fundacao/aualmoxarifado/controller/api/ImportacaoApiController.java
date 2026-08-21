package com.fundacao.aualmoxarifado.controller.api;

import com.fundacao.aualmoxarifado.exception.RegraDeNegocioException;
import com.fundacao.aualmoxarifado.service.importer.ExcelImportService;
import com.fundacao.aualmoxarifado.service.importer.ImportResult;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;

/**
 * Importação em lote — recebe planilha XLSX e devolve um {@link ImportResult}.
 */
@RestController
@RequestMapping("/api/importacao")
@RequiredArgsConstructor
public class ImportacaoApiController {

    private final ExcelImportService importService;

    @PostMapping(value = "/materiais", consumes = "multipart/form-data")
    public ImportResult importarMateriais(@RequestParam("arquivo") MultipartFile arquivo) {
        if (arquivo == null || arquivo.isEmpty()) {
            throw new RegraDeNegocioException("Arquivo não enviado.");
        }
        try {
            return importService.importarMateriais(arquivo.getInputStream());
        } catch (IOException e) {
            throw new RegraDeNegocioException("Falha ao ler arquivo: " + e.getMessage());
        }
    }
}
