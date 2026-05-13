package com.fundacao.aualmoxarifado.controller.api;

import com.fundacao.aualmoxarifado.repository.SetorRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.Map;

/**
 * Endpoint REST mínimo para o app mobile listar setores no dropdown.
 * Mantemos um shape enxuto (id + nome) para não vazar entidades JPA.
 */
@RestController
@RequestMapping("/api/setores")
@RequiredArgsConstructor
public class SetorApiController {

    private final SetorRepository setorRepository;

    @GetMapping
    public List<Map<String, Object>> listar() {
        return setorRepository.findAll().stream()
                .map(s -> Map.<String, Object>of(
                        "id", s.getId(),
                        "nome", s.getNome(),
                        "codigoCentroCusto",
                        s.getCodigoCentroCusto() == null ? "" : s.getCodigoCentroCusto()))
                .toList();
    }
}
