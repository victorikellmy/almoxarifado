package com.fundacao.aualmoxarifado.service;

import com.fundacao.aualmoxarifado.domain.Material;
import com.fundacao.aualmoxarifado.repository.MaterialRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
@RequiredArgsConstructor
public class MaterialService {

    private final MaterialRepository materialRepository;

    public List<Material> listar() {
        return materialRepository.findAll();
    }

    public Material buscar(Long id) {
        return materialRepository.findById(id)
                .orElseThrow(() -> new IllegalArgumentException("Material não encontrado."));
    }

    @Transactional
    public Material salvar(Material material) {
        // Materiais novos sempre nascem com estoque 0 (RF12).
        if (material.getId() == null && material.getEstoqueAtual() == null) {
            material.setEstoqueAtual(0);
        }
        return materialRepository.save(material);
    }

    /** RN06 - Lista materiais com estoque abaixo (ou igual) do mínimo, para alertas no frontend. */
    public List<Material> alertasDeEstoque() {
        return materialRepository.findEmAlertaDeEstoque();
    }
}
