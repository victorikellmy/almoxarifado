package com.fundacao.aualmoxarifado.service;

import com.fundacao.aualmoxarifado.domain.Area;
import com.fundacao.aualmoxarifado.domain.Subcategoria;
import com.fundacao.aualmoxarifado.repository.AreaRepository;
import com.fundacao.aualmoxarifado.repository.MaterialRepository;
import com.fundacao.aualmoxarifado.repository.SubcategoriaRepository;
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

    public Subcategoria buscar(Long id) {
        return subcategoriaRepository.findById(id)
                .orElseThrow(() -> new IllegalArgumentException("Subcategoria não encontrada."));
    }

    /**
     * Edita uma subcategoria já existente.
     *
     * <p><b>Apenas {@code nome} e {@code descricao} podem ser alterados.</b>
     * Sigla e Área são imutáveis após o cadastro porque o SKU dos materiais
     * (RF18) é montado com elas — alterá-las desalinharia o histórico.</p>
     */
    @Transactional
    public Subcategoria editar(Long id, String nome, String descricao) {
        Subcategoria sub = buscar(id);
        if (nome == null || nome.isBlank()) {
            throw new IllegalArgumentException("Nome da subcategoria é obrigatório.");
        }
        sub.setNome(nome.trim());
        sub.setDescricao(descricao);
        return subcategoriaRepository.save(sub);
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
