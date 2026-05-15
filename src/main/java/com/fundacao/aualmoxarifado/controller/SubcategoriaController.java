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

    /**
     * Lista todas as subcategorias. Aceita filtro opcional por área
     * ({@code ?areaId=}) que, quando informado, restringe a listagem.
     */
    @GetMapping
    public String listar(@RequestParam(required = false) Long areaId, Model model) {
        var subs = (areaId != null)
                ? subcategoriaService.listarPorArea(areaId)
                : subcategoriaService.listar();
        model.addAttribute("subcategorias", subs);
        model.addAttribute("areas", areaRepository.findAllByOrderByNomeAsc());
        model.addAttribute("areaSelecionada", areaId);
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

    /** Tela de edição (apenas nome + descrição — sigla/área imutáveis). */
    @GetMapping("/{id}/editar")
    public String editar(@PathVariable Long id, Model model) {
        Subcategoria sub = subcategoriaService.buscar(id);
        model.addAttribute("subcategoria", sub);
        return "subcategorias/form";
    }

    @PostMapping("/{id}")
    public String atualizar(@PathVariable Long id,
                            @RequestParam String nome,
                            @RequestParam(required = false) String descricao,
                            RedirectAttributes ra) {
        try {
            subcategoriaService.editar(id, nome, descricao);
            ra.addFlashAttribute("sucesso", "Subcategoria atualizada.");
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
