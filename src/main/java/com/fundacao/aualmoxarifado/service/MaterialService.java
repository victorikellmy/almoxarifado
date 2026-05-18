package com.fundacao.aualmoxarifado.service;

import com.fundacao.aualmoxarifado.domain.Material;
import com.fundacao.aualmoxarifado.domain.Subcategoria;
import com.fundacao.aualmoxarifado.exception.RecursoNaoEncontradoException;
import com.fundacao.aualmoxarifado.exception.RegraDeNegocioException;
import com.fundacao.aualmoxarifado.repository.MaterialRepository;
import com.fundacao.aualmoxarifado.repository.SubcategoriaRepository;
import com.fundacao.aualmoxarifado.repository.spec.MaterialSpecifications;
import lombok.RequiredArgsConstructor;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
@RequiredArgsConstructor
public class MaterialService {

    private final MaterialRepository materialRepository;
    private final SubcategoriaRepository subcategoriaRepository;
    private final SkuGeneratorService skuGeneratorService;

    public List<Material> listar() {
        return materialRepository.findAll();
    }

    /**
     * Listagem paginada e filtrada — usada pela tela web e pela REST API.
     * Filtros nulos/vazios são ignorados.
     */
    public Page<Material> listar(String nome,
                                 String sku,
                                 Long subcategoriaId,
                                 Long areaId,
                                 Boolean emAlerta,
                                 Pageable pageable) {
        return materialRepository.findAll(
                MaterialSpecifications.filtrar(nome, sku, subcategoriaId, areaId, emAlerta),
                pageable);
    }

    public Material buscar(Long id) {
        return materialRepository.findById(id)
                .orElseThrow(() -> new RecursoNaoEncontradoException("Material", id));
    }

    /**
     * RF12 + RF18 - Persiste o material.
     *
     *  - Se for NOVO (id == null): consulta a Subcategoria escolhida, gera o
     *    SKU via {@link SkuGeneratorService} e amarra no campo {@code codigoSku}.
     *  - Se for EDIÇÃO: o SKU NÃO muda (a coluna é {@code updatable = false} —
     *    o JPA ignora qualquer alteração no campo).
     *
     * Em caso de violação de unicidade (cenário extremo, ex: SKU duplicado por
     * race condition), a exceção é propagada para o controller, que mostrará a
     * mensagem ao usuário. A transação inteira é desfeita.
     */
    @Transactional
    public Material salvar(Material material) {
        if (material.getSubcategoria() == null || material.getSubcategoria().getId() == null) {
            throw new IllegalArgumentException("Selecione uma subcategoria para o material.");
        }

        // Resolve a subcategoria gerenciada (precisamos da entidade carregada
        // para o gerador de SKU e para evitar TransientObjectException no save).
        Subcategoria sub = subcategoriaRepository.findById(material.getSubcategoria().getId())
                .orElseThrow(() -> new RecursoNaoEncontradoException("Subcategoria",
                        material.getSubcategoria().getId()));
        material.setSubcategoria(sub);

        boolean ehNovo = material.getId() == null;

        if (ehNovo) {
            // Materiais novos sempre nascem com estoque 0 (RF12).
            if (material.getEstoqueAtual() == null) {
                material.setEstoqueAtual(0);
            }
            // RF18 - geração do SKU sob lock pessimista. O método roda em
            // transação própria (REQUIRES_NEW) para que o incremento do
            // sequencial seja COMMITADO mesmo que o save deste material falhe
            // depois — assim NUNCA reaproveitamos um número já "consumido".
            String sku = skuGeneratorService.gerarParaSubcategoria(sub.getId());
            material.setCodigoSku(sku);
        }

        try {
            return materialRepository.save(material);
        } catch (DataIntegrityViolationException ex) {
            // Defesa final contra duplicidade — o UNIQUE da coluna codigo_sku
            // disparou. Devolvemos uma mensagem clara em vez de stacktrace bruto.
            throw new RegraDeNegocioException(
                    "Falha ao gravar o material: SKU duplicado ou violação de integridade. "
                  + "Tente novamente.");
        }
    }

    /** RN06 - Lista materiais com estoque abaixo (ou igual) do mínimo, para alertas no frontend. */
    public List<Material> alertasDeEstoque() {
        return materialRepository.findEmAlertaDeEstoque();
    }
}
