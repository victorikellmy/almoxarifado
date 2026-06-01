package com.fpto.almoxarifado.service;

import com.fpto.almoxarifado.domain.Subcategoria;
import com.fpto.almoxarifado.repository.SubcategoriaRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

/**
 * RF18 - Gerador de SKU para "bipagem" (leitura por código de barras).
 *
 * Formato: {@code [AREA_SIGLA]-[SUBCAT_SIGLA]-[NNNNN]}
 * Exemplo: {@code ODO-CON-00001}
 *
 * COMO FUNCIONA E POR QUE NÃO GERA DUPLICATAS:
 *
 *  1. Quando um Material é cadastrado, o {@link MaterialService} chama
 *     {@link #gerarParaSubcategoria(Long)} dentro da transação de criação.
 *
 *  2. Este método abre uma transação própria ({@code REQUIRES_NEW}) e dispara
 *     {@code SubcategoriaRepository.findByIdComLock} — uma query
 *     {@code SELECT ... FOR UPDATE} que ADQUIRE LOCK PESSIMISTA na linha da
 *     Subcategoria. Qualquer outra thread que execute a mesma operação
 *     simultaneamente FICARÁ ESPERANDO até a primeira terminar.
 *
 *  3. Lemos o {@code proximoSequencial} (ex.: 7), formatamos com {@code %05d}
 *     para obter "00007", concatenamos as siglas e devolvemos o SKU pronto.
 *
 *  4. Incrementamos o contador (proximoSequencial++) e fazemos o save —
 *     ainda dentro da mesma transação lockada.
 *
 *  5. Ao retornar, a transação faz commit, libera o lock, e a próxima thread
 *     prossegue lendo já o NOVO valor (8 → "00008"). Nunca há colisão.
 *
 *  6. Defesa adicional: a coluna {@code material.codigo_sku} tem UNIQUE
 *     constraint no banco. Se algo passar pelo lock (ex. SKU manual editado
 *     por outra rota), a INSERT falha com {@code DataIntegrityViolation} e
 *     a transação do MaterialService faz rollback.
 *
 *  7. Idempotência em re-tentativas: se o save falhar por outro motivo após
 *     o gerador, o número incrementado fica perdido (gap na sequência), mas
 *     NUNCA reaproveitado — preferimos um buraco no histórico a um SKU duplicado.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class SkuGeneratorService {

    /** Largura do bloco numérico do SKU (NNNNN = 5 dígitos com zero à esquerda). */
    private static final int LARGURA_SEQUENCIAL = 5;

    private final SubcategoriaRepository subcategoriaRepository;

    /**
     * Gera um SKU novo para a subcategoria informada e já incrementa o
     * contador interno em uma transação isolada com lock pessimista.
     *
     * <p>É chamado ANTES de persistir o Material — o SKU vai como string pronta
     * para o {@code Material.codigoSku}.</p>
     *
     * @param subcategoriaId id da Subcategoria escolhida pelo usuário
     * @return SKU no formato {@code AAA-SSS-NNNNN}
     * @throws IllegalArgumentException se a subcategoria não existir
     */
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public String gerarParaSubcategoria(Long subcategoriaId) {
        // SELECT ... FOR UPDATE — bloqueia a linha até o commit/rollback.
        Subcategoria sub = subcategoriaRepository.findByIdComLock(subcategoriaId)
                .orElseThrow(() -> new IllegalArgumentException(
                        "Subcategoria id=" + subcategoriaId + " não encontrada para gerar SKU."));

        int sequencialAtual = sub.getProximoSequencial();
        String sku = montarSku(sub.getArea().getSigla(), sub.getSigla(), sequencialAtual);

        // Avança o contador. O save acontece DENTRO do lock, então a próxima
        // thread só verá o valor incrementado depois do nosso commit.
        sub.setProximoSequencial(sequencialAtual + 1);
        try {
            subcategoriaRepository.saveAndFlush(sub);
        } catch (DataIntegrityViolationException ex) {
            // Cenário muito raro (ex: alguém apagou a sub no meio do fluxo).
            // Propagamos para que a transação do MaterialService aborte.
            log.warn("[SKU] Falha ao incrementar sequencial da subcategoria {}: {}",
                    subcategoriaId, ex.getMessage());
            throw ex;
        }

        log.debug("[SKU] Gerado {} para subcategoria id={} (próx={})",
                sku, subcategoriaId, sub.getProximoSequencial());
        return sku;
    }

    /** Monta a string final, isolado para facilitar testes. */
    private static String montarSku(String siglaArea, String siglaSub, int sequencial) {
        // %05d → preenche com zeros à esquerda até 5 dígitos.
        // Se passar de 99999 (improvável em almoxarifado), o número
        // simplesmente cresce e a string fica mais longa — sem corrupção.
        return String.format("%s-%s-%0" + LARGURA_SEQUENCIAL + "d",
                             siglaArea, siglaSub, sequencial);
    }
}
