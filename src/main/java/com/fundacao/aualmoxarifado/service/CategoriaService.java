package com.fundacao.aualmoxarifado.service;

import com.fundacao.aualmoxarifado.domain.Categoria;
import com.fundacao.aualmoxarifado.repository.CategoriaRepository;
import com.fundacao.aualmoxarifado.repository.MaterialRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
@RequiredArgsConstructor
public class CategoriaService {

    private final CategoriaRepository categoriaRepository;
    private final MaterialRepository materialRepository;

    public List<Categoria> listar() {
        return categoriaRepository.findAll();
    }

    @Transactional
    public Categoria salvar(Categoria categoria) {
        return categoriaRepository.save(categoria);
    }

    /**
     * RN07 - Proteção de integridade.
     * Bloqueia a exclusão de uma categoria que possui Materiais vinculados.
     */
    @Transactional
    public void excluir(Long id) {
        Categoria categoria = categoriaRepository.findById(id)
                .orElseThrow(() -> new IllegalArgumentException("Categoria não encontrada."));

        if (materialRepository.existsByCategoria(categoria)) {
            throw new IllegalStateException(
                    "RN07: não é possível excluir a categoria \"" + categoria.getNome()
                            + "\" pois existem materiais vinculados a ela.");
        }
        categoriaRepository.delete(categoria);
    }
}
