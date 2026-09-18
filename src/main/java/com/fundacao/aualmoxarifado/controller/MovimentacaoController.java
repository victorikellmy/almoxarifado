package com.fundacao.aualmoxarifado.controller;

import com.fundacao.aualmoxarifado.domain.Setor;
import com.fundacao.aualmoxarifado.domain.StatusMovimentacao;
import com.fundacao.aualmoxarifado.domain.TipoMovimentacao;
import com.fundacao.aualmoxarifado.repository.SetorRepository;
import com.fundacao.aualmoxarifado.service.MovimentacaoService;
import com.fundacao.aualmoxarifado.service.MovimentacaoService.LinhaItem;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.web.PageableDefault;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.util.ArrayList;
import java.util.List;

@Controller
@RequestMapping("/movimentacoes")
@RequiredArgsConstructor
public class MovimentacaoController {

    private final MovimentacaoService movimentacaoService;
    private final SetorRepository setorRepository;

    // ---------- Listagem paginada + filtros ----------
    @GetMapping
    public String listar(
            @RequestParam(required = false) TipoMovimentacao tipo,
            @RequestParam(required = false) StatusMovimentacao status,
            @RequestParam(required = false) Long setorId,
            @RequestParam(required = false)
            @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate inicio,
            @RequestParam(required = false)
            @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate fim,
            @PageableDefault(size = 20, sort = "data", direction = Sort.Direction.DESC)
            Pageable pageable,
            Model model) {

        LocalDateTime inicioDt = inicio != null ? inicio.atStartOfDay() : null;
        LocalDateTime fimDt    = fim    != null ? fim.atTime(LocalTime.MAX) : null;

        var page = movimentacaoService.listar(tipo, status, null, setorId, inicioDt, fimDt, pageable);

        model.addAttribute("page", page);
        model.addAttribute("setores", setorRepository.findAll());
        // Devolve filtros selecionados para a UI manter o estado.
        model.addAttribute("filtroTipo", tipo);
        model.addAttribute("filtroStatus", status);
        model.addAttribute("filtroSetorId", setorId);
        model.addAttribute("filtroInicio", inicio);
        model.addAttribute("filtroFim", fim);
        return "movimentacoes/lista";
    }

    /** Detalhe da movimentação — mostra a lista de itens enviados. */
    @GetMapping("/{id}")
    public String detalhar(@PathVariable Long id, Model model) {
        model.addAttribute("movimentacao", movimentacaoService.buscar(id));
        return "movimentacoes/detalhes";
    }

    // ---------- Saída multi-item (RF06) ----------
    @GetMapping("/saida/nova")
    public String novaSaida(Model model) {
        model.addAttribute("setores", setorRepository.findAll());
        return "movimentacoes/saida-form";
    }

    /**
     * Persiste a saída.
     *
     * <p>Recebe arrays paralelos {@code materialIds[]} e {@code quantidades[]}
     * vindos do form dinâmico — cada índice é uma linha de item.</p>
     */
    @PostMapping("/saida")
    public String salvarSaida(@RequestParam Long setorId,
                              @RequestParam(required = false) String retiradoPor,
                              @RequestParam(required = false) String observacao,
                              @RequestParam(name = "materialIds", required = false) List<Long> materialIds,
                              @RequestParam(name = "quantidades", required = false) List<Integer> quantidades,
                              RedirectAttributes ra,
                              Model model) {
        try {
            Setor setor = setorRepository.findById(setorId)
                    .orElseThrow(() -> new IllegalArgumentException("Setor não encontrado."));
            List<LinhaItem> linhas = construirLinhas(materialIds, quantidades);
            var mov = movimentacaoService.registrarSaida(setor, retiradoPor, observacao, linhas);
            ra.addFlashAttribute("sucesso",
                    "Saída registrada com " + linhas.size() + " item(ns) — aguardando aprovação.");
            return "redirect:/movimentacoes/" + mov.getId();
        } catch (RuntimeException ex) {
            model.addAttribute("erro", ex.getMessage());
            model.addAttribute("setores", setorRepository.findAll());
            // devolve os valores digitados para o usuário não perder tudo
            model.addAttribute("setorIdSelecionado", setorId);
            model.addAttribute("retiradoPorInformado", retiradoPor);
            model.addAttribute("observacaoInformada", observacao);
            model.addAttribute("materialIdsInformados", materialIds);
            model.addAttribute("quantidadesInformadas", quantidades);
            return "movimentacoes/saida-form";
        }
    }

    // RF13 - Lançamento manual de Entrada removido (vide CompraService).

    /** RN04 - Aprovação/entrega por gestor (debita estoque de todos os itens). */
    @PostMapping("/{id}/status")
    public String alterarStatus(@PathVariable Long id,
                                @RequestParam StatusMovimentacao status,
                                RedirectAttributes ra) {
        movimentacaoService.alterarStatus(id, status);
        ra.addFlashAttribute("sucesso", "Status atualizado para " + status + ".");
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

    // ---------- helpers ----------
    private static List<LinhaItem> construirLinhas(List<Long> materialIds, List<Integer> quantidades) {
        if (materialIds == null || materialIds.isEmpty()) {
            throw new IllegalArgumentException("Adicione ao menos um item à saída.");
        }
        if (quantidades == null || quantidades.size() != materialIds.size()) {
            throw new IllegalArgumentException("Cada item precisa de uma quantidade.");
        }
        List<LinhaItem> linhas = new ArrayList<>(materialIds.size());
        for (int i = 0; i < materialIds.size(); i++) {
            Long matId = materialIds.get(i);
            Integer qty = quantidades.get(i);
            if (matId == null || qty == null) continue;     // linha em branco — ignora
            linhas.add(new LinhaItem(matId, qty));
        }
        if (linhas.isEmpty()) {
            throw new IllegalArgumentException("Nenhum item válido informado.");
        }
        return linhas;
    }
}
