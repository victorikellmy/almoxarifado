package com.fundacao.aualmoxarifado.controller;

import com.fundacao.aualmoxarifado.service.report.RelatorioService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;

import java.time.LocalDate;

/**
 * Views dos relatórios gerenciais/contábeis de movimentações:
 *   /relatorios/mensal     · /relatorios/trimestral · /relatorios/anual
 *
 * <p>Os defaults sempre apontam para o período <b>anterior já fechado</b>
 * (mês passado, trimestre passado, ano passado) — é o que normalmente se
 * apresenta para a contabilidade.</p>
 */
@Controller
@RequestMapping("/relatorios")
@RequiredArgsConstructor
public class RelatoriosController {

    private final RelatorioService relatorioService;

    @GetMapping("/mensal")
    public String mensal(@RequestParam(required = false) Integer ano,
                         @RequestParam(required = false) Integer mes,
                         Model model) {
        LocalDate mesPassado = LocalDate.now().minusMonths(1);
        int a = ano != null ? ano : mesPassado.getYear();
        int m = mes != null ? mes : mesPassado.getMonthValue();

        model.addAttribute("dados", relatorioService.dadosRelatorioMensal(a, m));
        model.addAttribute("ano", a);
        model.addAttribute("mes", m);
        return "relatorios/mensal";
    }

    @GetMapping("/trimestral")
    public String trimestral(@RequestParam(required = false) Integer ano,
                             @RequestParam(required = false) Integer trimestre,
                             Model model) {
        LocalDate hoje = LocalDate.now();
        int trimAtual = ((hoje.getMonthValue() - 1) / 3) + 1;
        int trimAnterior = trimAtual == 1 ? 4 : trimAtual - 1;
        int anoAnterior = trimAtual == 1 ? hoje.getYear() - 1 : hoje.getYear();

        int a = ano != null ? ano : anoAnterior;
        int t = trimestre != null ? trimestre : trimAnterior;

        model.addAttribute("dados", relatorioService.dadosRelatorioTrimestral(a, t));
        model.addAttribute("ano", a);
        model.addAttribute("trimestre", t);
        return "relatorios/trimestral";
    }

    @GetMapping("/anual")
    public String anual(@RequestParam(required = false) Integer ano, Model model) {
        int a = ano != null ? ano : LocalDate.now().getYear() - 1;
        model.addAttribute("dados", relatorioService.dadosRelatorioAnual(a));
        model.addAttribute("ano", a);
        return "relatorios/anual";
    }
}
