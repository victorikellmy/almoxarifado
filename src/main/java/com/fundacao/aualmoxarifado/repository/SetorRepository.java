package com.fundacao.aualmoxarifado.repository;

import com.fundacao.aualmoxarifado.domain.Organizacao;
import com.fundacao.aualmoxarifado.domain.Setor;
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Comparator;
import java.util.List;

public interface SetorRepository extends JpaRepository<Setor, Long> {

    /** Setores homônimos só são permitidos em organizações diferentes. */
    boolean existsByNomeIgnoreCaseAndOrganizacao(String nome, Organizacao organizacao);

    List<Setor> findByOrganizacaoOrderByNomeAsc(Organizacao organizacao);

    /**
     * Setores agrupados por organização (ordem do enum: FPTO, depois FA-Saúde)
     * e por nome — ordem usada em todos os selects e listagens. A ordenação da
     * organização é feita em memória porque a coluna é texto ("FA_SAUDE" viria
     * antes de "FPTO" no ORDER BY); a tabela é pequena.
     */
    default List<Setor> listarOrdenados() {
        return findAll(Sort.by("nome")).stream()
                .sorted(Comparator.comparing(Setor::getOrganizacao,
                        Comparator.nullsLast(Comparator.naturalOrder())))
                .toList();
    }
}
