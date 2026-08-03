package com.fundacao.aualmoxarifado.service;

import com.fundacao.aualmoxarifado.domain.Area;
import com.fundacao.aualmoxarifado.repository.AreaRepository;
import com.fundacao.aualmoxarifado.repository.SubcategoriaRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class AreaService {

    private final AreaRepository areaRepository;
    private final SubcategoriaRepository subcategoriaRepository;

    public List<Area> listar() {
        return areaRepository.findAllByOrderByNomeAsc();
    }

    @Transactional
    public Area salvar(Area area) {
        // Padroniza a sigla em maiúsculas — facilita matching e mantém o
        // SKU sempre uniforme (ODO em vez de odo/Odo).
        if (area.getSigla() != null) {
            area.setSigla(area.getSigla().trim().toUpperCase());
        }
        return areaRepository.save(area);
    }

    /**
     * RN07 - protege exclusão: uma Área que tem subcategorias filhas
     * (e portanto, indiretamente, materiais com SKUs gerados) não pode ser
     * removida sem antes limpar a hierarquia.
     */
    @Transactional
    public void excluir(Long id) {
        Area area = areaRepository.findById(id)
                .orElseThrow(() -> new IllegalArgumentException("Área não encontrada."));

        if (subcategoriaRepository.existsByArea(area)) {
            throw new IllegalStateException(
                    "RN07: não é possível excluir a área \"" + area.getNome()
                            + "\" pois existem subcategorias vinculadas. Remova as subcategorias antes.");
        }
        areaRepository.delete(area);
    }
}
