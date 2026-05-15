package com.fundacao.aualmoxarifado.controller.api;

import com.fundacao.aualmoxarifado.dto.ConsumoSetorDTO;
import com.fundacao.aualmoxarifado.repository.MovimentacaoRepository;
import com.fundacao.aualmoxarifado.service.report.RelatorioService;
import com.fundacao.aualmoxarifado.service.report.RelatorioService.Arquivo;
import com.fundacao.aualmoxarifado.service.report.RelatorioService.Formato;
import lombok.RequiredArgsConstructor;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.util.List;

/**
 * Endpoints REST de relatórios — JSON (consulta) e download (CSV/XLSX/PDF).
 */
@RestController
@RequestMapping("/api/relatorios")
@RequiredArgsConstructor
public class RelatorioApiController {

    private final RelatorioService relatorioService;
    private final MovimentacaoRepository movimentacaoRepository;

    @GetMapping("/consumo-setor")
    public List<ConsumoSetorDTO> consumoSetor(
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate inicio,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate fim) {
        var range = periodo(inicio, fim);
        return movimentacaoRepository.consumoPorSetor(range[0], range[1]);
    }

    @GetMapping("/consumo-setor/export")
    public ResponseEntity<byte[]> consumoSetorExport(
            @RequestParam Formato formato,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate inicio,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate fim) {
        var range = periodo(inicio, fim);
        return respond(relatorioService.consumoPorSetor(range[0], range[1], formato));
    }

    @GetMapping("/estoque/export")
    public ResponseEntity<byte[]> estoqueExport(@RequestParam Formato formato) {
        return respond(relatorioService.estoqueAtual(formato));
    }

    @GetMapping("/alertas/export")
    public ResponseEntity<byte[]> alertasExport(@RequestParam Formato formato) {
        return respond(relatorioService.alertasEstoque(formato));
    }

    // ---------- helpers ----------
    private LocalDateTime[] periodo(LocalDate inicio, LocalDate fim) {
        LocalDate ini = inicio != null ? inicio : LocalDate.now().withDayOfMonth(1);
        LocalDate end = fim    != null ? fim    : LocalDate.now();
        return new LocalDateTime[]{ ini.atStartOfDay(), end.atTime(LocalTime.MAX) };
    }

    private ResponseEntity<byte[]> respond(Arquivo a) {
        return ResponseEntity.ok()
                .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=\"" + a.nome() + "\"")
                .contentType(MediaType.parseMediaType(a.contentType()))
                .body(a.conteudo());
    }
}
