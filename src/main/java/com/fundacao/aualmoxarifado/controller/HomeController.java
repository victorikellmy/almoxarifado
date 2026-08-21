package com.fundacao.aualmoxarifado.controller;

import com.fundacao.aualmoxarifado.domain.StatusCompra;
import com.fundacao.aualmoxarifado.domain.StatusMovimentacao;
import com.fundacao.aualmoxarifado.repository.*;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;

@Controller
@RequiredArgsConstructor
public class HomeController {

    private final MaterialRepository materialRepository;
    private final AreaRepository areaRepository;
    private final SubcategoriaRepository subcategoriaRepository;
    private final SetorRepository setorRepository;
    private final CompraRepository compraRepository;
    private final MovimentacaoRepository movimentacaoRepository;

    /**
     * Dashboard inicial — agrega contagens leves para os cartões de estatísticas
     * exibidos em {@code index.html}. Todas as contagens descem como COUNT(*)
     * para o banco; a única lista materializada é a de alertas (RN06), que é
     * exibida na íntegra e já vem com subcategoria/área carregadas.
     */
    @GetMapping("/")
    public String home(Model model) {
        // Lista usada duas vezes (contador + tabela de contexto) — busca única.
        var alertas = materialRepository.findEmAlertaDeEstoque(); // RN06

        model.addAttribute("totalMateriais", materialRepository.count());
        model.addAttribute("materiaisEmAlerta", (long) alertas.size());
        model.addAttribute("totalAreas", areaRepository.count());
        model.addAttribute("totalSubcategorias", subcategoriaRepository.count());
        model.addAttribute("totalSetores", setorRepository.count());
        model.addAttribute("comprasAguardando",
                compraRepository.countByStatus(StatusCompra.AGUARDANDO_COMPRA));
        model.addAttribute("totalCompras", compraRepository.count());
        model.addAttribute("totalMovimentacoes", movimentacaoRepository.count());
        model.addAttribute("pendentesAprovacao",
                movimentacaoRepository.countByStatus(StatusMovimentacao.PENDENTE_APROVACAO));

        // Bloco de últimos cadastros / alertas para a UI mostrar contexto.
        model.addAttribute("alertas", alertas);

        return "index";
    }
}
