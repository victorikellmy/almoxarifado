package com.fpto.almoxarifado.service;

import com.fpto.almoxarifado.domain.Area;
import com.fpto.almoxarifado.domain.Subcategoria;
import com.fpto.almoxarifado.repository.AreaRepository;
import com.fpto.almoxarifado.repository.MaterialRepository;
import com.fpto.almoxarifado.repository.SubcategoriaRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
@RequiredArgsConstructor
public class SubcategoriaService {

    private final SubcategoriaRepository subcategoriaRepository;
    private final AreaRepository areaRepository;
    private final MaterialRepository materialRepository;

    public List<Subcategoria> listar() {
        return subcategoriaRepository.findAll();
    }

    /** Endpoint usado pelo dropdown em cascata do form de Material. */
    public List<Subcategoria> listarPorArea(Long areaId) {
        return subcategoriaRepository.findByAreaIdOrderByNomeAsc(areaId);
    }

    /**
     * Salva a subcategoria, garantindo que a Área é válida e que a sigla é
     * única DENTRO da área (RN11). A sigla é normalizada para maiúsculas.
     */
    @Transactional
    public Subcategoria salvar(Subcategoria sub) {
        if (sub.getArea() == null || sub.getArea().getId() == null) {
            throw new IllegalArgumentException("Selecione a área da subcategoria.");
        }
        Area area = areaRepository.findById(sub.getArea().getId())
                .orElseThrow(() -> new IllegalArgumentException("Área não encontrada."));
        sub.setArea(area);

        if (sub.getSigla() != null) {
            sub.setSigla(sub.getSigla().trim().toUpperCase());
        }

        // RN11 - valida unicidade composta (area_id + sigla).
        subcategoriaRepository.findByAreaAndSigla(area, sub.getSigla()).ifPresent(existente -> {
            if (!existente.getId().equals(sub.getId())) {
                throw new IllegalStateException(
                        "Já existe uma subcategoria com a sigla \"" + sub.getSigla()
                                + "\" na área \"" + area.getNome() + "\".");
            }
        });

        return subcategoriaRepository.save(sub);
    }

    /**
     * RN07 - protege exclusão: uma subcategoria com materiais cadastrados não
     * pode ser removida (caso contrário, perderíamos a referência do SKU).
     */
    @Transactional
    public void excluir(Long id) {
        Subcategoria sub = subcategoriaRepository.findById(id)
                .orElseThrow(() -> new IllegalArgumentException("Subcategoria não encontrada."));

        if (materialRepository.existsBySubcategoria(sub)) {
            throw new IllegalStateException(
                    "RN07: não é possível excluir a subcategoria \"" + sub.getNome()
                            + "\" pois há materiais vinculados.");
        }
        subcategoriaRepository.delete(sub);
    }
}
