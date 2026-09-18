package com.fundacao.aualmoxarifado.controller.api;

import com.fundacao.aualmoxarifado.dto.SetorApiDTO;
import com.fundacao.aualmoxarifado.repository.SetorRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * Endpoint REST mínimo para o app mobile listar setores no dropdown.
 * Mantemos um shape enxuto (id + nome + codigoCentroCusto) para não vazar
 * entidades JPA; codigoCentroCusto pode ser {@code null} (contrato do app).
 */
@RestController
@RequestMapping("/api/setores")
@RequiredArgsConstructor
public class SetorApiController {

    private final SetorRepository setorRepository;

    @GetMapping
    public List<SetorApiDTO> listar() {
        return setorRepository.findAll().stream()
                .map(SetorApiDTO::from)
                .toList();
    }
}
