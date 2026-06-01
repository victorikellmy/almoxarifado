package com.fpto.almoxarifado.controller;

import com.fpto.almoxarifado.domain.Movimentacao;
import com.fpto.almoxarifado.domain.StatusMovimentacao;
import com.fpto.almoxarifado.repository.MaterialRepository;
import com.fpto.almoxarifado.repository.SetorRepository;
import com.fpto.almoxarifado.service.MovimentacaoService;
import lombok.RequiredArgsConstructor;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;

@Controller
@RequestMapping("/movimentacoes")
@RequiredArgsConstructor
public class MovimentacaoController {

    private final MovimentacaoService movimentacaoService;
    private final MaterialRepository materialRepository;
    private final SetorRepository setorRepository;

    @GetMapping
    public String listar(Model model) {
        model.addAttribute("movimentacoes", movimentacaoService.listarTodas());
        return "movimentacoes/lista";
    }

    /** Tela do formulário de Nova Saída (RF06). */
    @GetMapping("/saida/nova")
    public String novaSaida(Model model) {
        model.addAttribute("movimentacao", new Movimentacao());
        model.addAttribute("materiais", materialRepository.findAll());
        model.addAttribute("setores", setorRepository.findAll());
        return "movimentacoes/saida-form";
    }

    /**
     * Persiste a saída.
     * RN03 é validada no Service e devolve mensagem de erro caso o setor seja nulo.
     */
    @PostMapping("/saida")
    public String salvarSaida(@ModelAttribute Movimentacao movimentacao,
                              Model model, @RequestParam(required = false) Long setorId,
                              @RequestParam(required = false) Long materialId) {
        try {
            // Wiring de IDs vindos do form
            if (setorId != null) {
                movimentacao.setSetorDestino(setorRepository.findById(setorId).orElse(null));
            }
            if (materialId != null) {
                movimentacao.setMaterial(materialRepository.findById(materialId).orElse(null));
            }
            movimentacaoService.registrarSaida(movimentacao);
            return "redirect:/movimentacoes";
        } catch (RuntimeException ex) {
            model.addAttribute("erro", ex.getMessage());
            model.addAttribute("materiais", materialRepository.findAll());
            model.addAttribute("setores", setorRepository.findAll());
            model.addAttribute("movimentacao", movimentacao);
            return "movimentacoes/saida-form";
        }
    }

    // RF13 - O lançamento manual de Entrada foi removido.
    // Toda entrada de material no estoque agora ocorre exclusivamente através
    // do Módulo de Compras (RF14): a baixa de uma Compra do tipo ESTOQUE chama
    // MovimentacaoService.registrarEntrada para creditar o saldo (RN05/RN09).
    // Veja: CompraService.baixarComoEntradaDeEstoque.

    /** RN04 - Aprovação/entrega por gestor (debita estoque). */
    @PostMapping("/{id}/status")
    public String alterarStatus(@PathVariable Long id, @RequestParam StatusMovimentacao status) {
        movimentacaoService.alterarStatus(id, status);
        return "redirect:/movimentacoes";
    }

    /** RF10 - Relatório de consumo por setor. */
    @GetMapping("/relatorio/consumo-setor")
    public String relatorioConsumo(
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate inicio,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate fim,
            Model model) {

        LocalDate ini = inicio != null ? inicio : LocalDate.now().withDayOfMonth(1);
        LocalDate fimEfetivo = fim != null ? fim : LocalDate.now();

        var resultado = movimentacaoService.relatorioConsumoPorSetor(
                ini.atStartOfDay(),
                fimEfetivo.atTime(LocalTime.MAX));

        model.addAttribute("inicio", ini);
        model.addAttribute("fim", fimEfetivo);
        model.addAttribute("consumos", resultado);
        return "relatorios/consumo-setor";
    }
}
