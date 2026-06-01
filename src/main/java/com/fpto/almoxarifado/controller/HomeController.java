package com.fpto.almoxarifado.controller;

import com.fpto.almoxarifado.domain.StatusCompra;
import com.fpto.almoxarifado.domain.StatusMovimentacao;
import com.fpto.almoxarifado.repository.*;
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
     * exibidos em {@code index.html}. As consultas são todas O(1) (count + filter
     * por status) e cabem perfeitamente em uma página de visão geral.
     */
    @GetMapping("/")
    public String home(Model model) {
        long totalMateriais = materialRepository.count();
        long materiaisEmAlerta = materialRepository.findEmAlertaDeEstoque().size(); // RN06
        long totalAreas = areaRepository.count();
        long totalSubcategorias = subcategoriaRepository.count();
        long totalSetores = setorRepository.count();

        long comprasAguardando = compraRepository
                .findByStatusOrderByDataSolicitacaoAsc(StatusCompra.AGUARDANDO_COMPRA).size();
        long totalCompras = compraRepository.count();

        long totalMovimentacoes = movimentacaoRepository.count();
        long pendentesAprovacao = movimentacaoRepository.findAll().stream()
                .filter(m -> m.getStatus() == StatusMovimentacao.PENDENTE_APROVACAO)
                .count();

        model.addAttribute("totalMateriais", totalMateriais);
        model.addAttribute("materiaisEmAlerta", materiaisEmAlerta);
        model.addAttribute("totalAreas", totalAreas);
        model.addAttribute("totalSubcategorias", totalSubcategorias);
        model.addAttribute("totalSetores", totalSetores);
        model.addAttribute("comprasAguardando", comprasAguardando);
        model.addAttribute("totalCompras", totalCompras);
        model.addAttribute("totalMovimentacoes", totalMovimentacoes);
        model.addAttribute("pendentesAprovacao", pendentesAprovacao);

        // Bloco de últimos cadastros / alertas para a UI mostrar contexto.
        model.addAttribute("alertas", materialRepository.findEmAlertaDeEstoque());

        return "index";
    }
}
