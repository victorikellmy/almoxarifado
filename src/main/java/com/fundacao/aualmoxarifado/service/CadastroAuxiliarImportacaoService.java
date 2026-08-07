package com.fundacao.aualmoxarifado.service;

import com.fundacao.aualmoxarifado.domain.Area;
import com.fundacao.aualmoxarifado.domain.Subcategoria;
import com.fundacao.aualmoxarifado.repository.AreaRepository;
import com.fundacao.aualmoxarifado.repository.SubcategoriaRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

/**
 * Cria Área e Subcategoria durante a importação, cada uma em uma transação
 * PRÓPRIA e já COMMITADA.
 *
 * <p><b>Por que isso é necessário?</b> O {@code SkuGeneratorService} roda em
 * {@code REQUIRES_NEW} (ele precisa de transação isolada para o lock
 * pessimista do sequencial). Uma transação nova usa outra conexão e, em
 * READ_COMMITTED, NÃO enxerga inserts ainda não commitados. Se a subcategoria
 * fosse criada na mesma transação da linha, o gerador de SKU não a encontraria
 * e a importação quebraria com "Subcategoria não encontrada".</p>
 *
 * <p>Efeito colateral aceito: se a linha falhar DEPOIS deste ponto, a área/
 * subcategoria recém-criada permanece no banco. É um cadastro vazio e
 * inofensivo, que o usuário pode remover pela tela (RN07 permite excluir
 * enquanto não houver materiais vinculados) — bem melhor do que travar a
 * importação inteira.</p>
 */
@Service
@RequiredArgsConstructor
public class CadastroAuxiliarImportacaoService {

    private final AreaRepository areaRepository;
    private final SubcategoriaRepository subcategoriaRepository;

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public Area criarArea(String nome, String sigla) {
        return areaRepository.saveAndFlush(Area.builder()
                .nome(nome)
                .sigla(sigla)
                .descricao("Criada automaticamente pela importação de planilha.")
                .build());
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public Subcategoria criarSubcategoria(String nome, String sigla, Long areaId) {
        Area area = areaRepository.findById(areaId)
                .orElseThrow(() -> new IllegalStateException(
                        "Área id=" + areaId + " desapareceu durante a importação."));

        return subcategoriaRepository.saveAndFlush(Subcategoria.builder()
                .nome(nome)
                .sigla(sigla)
                .area(area)
                .proximoSequencial(1)
                .descricao("Criada automaticamente pela importação de planilha.")
                .build());
    }
}
