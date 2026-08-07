package com.fundacao.aualmoxarifado.controller;

import com.fundacao.aualmoxarifado.dto.ModoEstoqueImportacao;
import com.fundacao.aualmoxarifado.dto.ResultadoImportacao;
import com.fundacao.aualmoxarifado.service.ImportacaoMaterialService;
import com.fundacao.aualmoxarifado.service.LeitorPlanilhaService;
import com.fundacao.aualmoxarifado.service.ModeloPlanilhaMaterialService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MaxUploadSizeExceededException;
import org.springframework.web.multipart.MultipartFile;

import java.io.UncheckedIOException;

/**
 * RF12 - Importação em massa de materiais por planilha (.xlsx, .xls, .csv).
 *
 * Fluxo pensado para o usuário do almoxarifado:
 *   1. baixa o modelo (ou usa a planilha que já tem);
 *   2. SIMULA e confere o relatório linha a linha;
 *   3. importa de verdade.
 */
@Slf4j
@Controller
@RequestMapping("/materiais/importar")
@RequiredArgsConstructor
public class ImportacaoMaterialController {

    private static final String VIEW = "materiais/importar";

    private final ImportacaoMaterialService importacaoService;
    private final ModeloPlanilhaMaterialService modeloService;

    @GetMapping
    public String tela(Model model) {
        prepararTela(model);
        return VIEW;
    }

    @PostMapping
    public String importar(@RequestParam("arquivo") MultipartFile arquivo,
                           @RequestParam(defaultValue = "SOMAR") ModoEstoqueImportacao modoEstoque,
                           @RequestParam(defaultValue = "false") boolean criarAusentes,
                           @RequestParam(defaultValue = "false") boolean simular,
                           Model model) {
        prepararTela(model);
        model.addAttribute("modoEstoqueSelecionado", modoEstoque);
        model.addAttribute("criarAusentesSelecionado", criarAusentes);

        try {
            ResultadoImportacao resultado =
                    importacaoService.importar(arquivo, modoEstoque, criarAusentes, simular);
            model.addAttribute("resultado", resultado);
        } catch (IllegalArgumentException | IllegalStateException | UncheckedIOException ex) {
            model.addAttribute("erro", ex.getMessage());
        }
        return VIEW;
    }

    /** Download da planilha-modelo já com cabeçalho, exemplo e instruções. */
    @GetMapping("/modelo")
    public ResponseEntity<byte[]> baixarModelo() {
        byte[] planilha = modeloService.gerar();
        return ResponseEntity.ok()
                .header(HttpHeaders.CONTENT_DISPOSITION,
                        "attachment; filename=\"modelo-importacao-materiais.xlsx\"")
                .contentType(MediaType.parseMediaType(
                        "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet"))
                .body(planilha);
    }

    /**
     * O limite de upload vem do {@code application.properties}
     * ({@code spring.servlet.multipart.max-file-size}). Sem este handler o
     * usuário receberia uma página de erro 500 crua.
     */
    @ExceptionHandler(MaxUploadSizeExceededException.class)
    public String arquivoGrandeDemais(Model model) {
        prepararTela(model);
        model.addAttribute("erro",
                "O arquivo excede o tamanho máximo permitido para upload. "
              + "Divida a planilha em partes menores.");
        return VIEW;
    }

    private void prepararTela(Model model) {
        model.addAttribute("colunas", ModeloPlanilhaMaterialService.COLUNAS);
        model.addAttribute("exemplos", ModeloPlanilhaMaterialService.LINHAS_EXEMPLO);
        model.addAttribute("modos", ModoEstoqueImportacao.values());
        model.addAttribute("maxLinhas", LeitorPlanilhaService.MAX_LINHAS);
    }
}
