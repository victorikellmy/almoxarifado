package com.fundacao.aualmoxarifado.controller;

import com.fundacao.aualmoxarifado.domain.Subcategoria;
import com.fundacao.aualmoxarifado.repository.AreaRepository;
import com.fundacao.aualmoxarifado.service.SubcategoriaService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.validation.BindingResult;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

import java.util.List;
import java.util.Map;

@Controller
@RequestMapping("/subcategorias")
@RequiredArgsConstructor
public class SubcategoriaController {

    private final SubcategoriaService subcategoriaService;
    private final AreaRepository areaRepository;

    @GetMapping
    public String listar(Model model) {
        model.addAttribute("subcategorias", subcategoriaService.listar());
        model.addAttribute("areas", areaRepository.findAllByOrderByNomeAsc());
        if (!model.containsAttribute("subcategoria")) {
            model.addAttribute("subcategoria", new Subcategoria());
        }
        return "subcategorias/lista";
    }

    @PostMapping
    public String salvar(@Valid @ModelAttribute("subcategoria") Subcategoria sub,
                         BindingResult br, Model model) {
        if (br.hasErrors()) {
            model.addAttribute("subcategorias", subcategoriaService.listar());
            model.addAttribute("areas", areaRepository.findAllByOrderByNomeAsc());
            return "subcategorias/lista";
        }
        try {
            subcategoriaService.salvar(sub);
        } catch (RuntimeException ex) {
            model.addAttribute("erro", ex.getMessage());
            model.addAttribute("subcategorias", subcategoriaService.listar());
            model.addAttribute("areas", areaRepository.findAllByOrderByNomeAsc());
            return "subcategorias/lista";
        }
        return "redirect:/subcategorias";
    }

    @PostMapping("/{id}/excluir")
    public String excluir(@PathVariable Long id, RedirectAttributes ra) {
        try {
            subcategoriaService.excluir(id);
        } catch (RuntimeException ex) {
            ra.addFlashAttribute("erro", ex.getMessage());
        }
        return "redirect:/subcategorias";
    }

    /**
     * Endpoint JSON consumido pelo dropdown em cascata do form de Material.
     *
     * Ao trocar a Área no <select>, o frontend faz
     * {@code GET /subcategorias/api/por-area/{areaId}} e recebe a lista
     * de subcategorias da área. Devolvemos um JSON enxuto (apenas id + nome
     * + sigla) — não expomos a entidade inteira para evitar serialização
     * indevida do {@code proximoSequencial} e do grafo Area.
     */
    @GetMapping(value = "/api/por-area/{areaId}", produces = "application/json")
    @ResponseBody
    public List<Map<String, Object>> porArea(@PathVariable Long areaId) {
        return subcategoriaService.listarPorArea(areaId).stream()
                .map(s -> Map.<String, Object>of(
                        "id", s.getId(),
                        "nome", s.getNome(),
                        "sigla", s.getSigla()))
                .toList();
    }
}
