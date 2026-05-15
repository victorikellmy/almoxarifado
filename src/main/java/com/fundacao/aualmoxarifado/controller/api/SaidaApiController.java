package com.fundacao.aualmoxarifado.controller.api;

import com.fundacao.aualmoxarifado.domain.Material;
import com.fundacao.aualmoxarifado.domain.Movimentacao;
import com.fundacao.aualmoxarifado.domain.Setor;
import com.fundacao.aualmoxarifado.dto.ItemSaidaDTO;
import com.fundacao.aualmoxarifado.dto.SaidaRequestDTO;
import com.fundacao.aualmoxarifado.dto.SaidaResponseDTO;
import com.fundacao.aualmoxarifado.repository.MaterialRepository;
import com.fundacao.aualmoxarifado.repository.SetorRepository;
import com.fundacao.aualmoxarifado.service.MovimentacaoService;
import com.fundacao.aualmoxarifado.service.MovimentacaoService.LinhaItem;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.ArrayList;
import java.util.List;

/**
 * Endpoint REST consumido pelo app mobile (RF06 + RF18).
 *
 * <p>Fluxo do app:</p>
 * <ol>
 *   <li>seleciona Setor</li>
 *   <li>bipa N produtos (SKU + quantidade)</li>
 *   <li>envia tudo aqui como uma única requisição</li>
 * </ol>
 *
 * <p>Toda a requisição vira <b>uma única Movimentação multi-item</b>
 * do tipo SAIDA, reusando {@link MovimentacaoService#registrarSaida}
 * para honrar RN03/RN04 (setor obrigatório, status PENDENTE_APROVACAO,
 * validação de estoque item a item).</p>
 */
@RestController
@RequestMapping("/api/saidas")
@RequiredArgsConstructor
@Slf4j
public class SaidaApiController {

    private final MovimentacaoService movimentacaoService;
    private final MaterialRepository materialRepository;
    private final SetorRepository setorRepository;

    @PostMapping
    public ResponseEntity<?> registrarSaida(@Valid @RequestBody SaidaRequestDTO request) {

        log.info("==> [API] Recebida saída do app: setorId={}, retiradoPor={}, totalItens={}",
                request.setorDestinoId(), request.retiradoPor(), request.itens().size());
        request.itens().forEach(i ->
                log.info("    item: sku={}, qtd={}", i.codigoSku(), i.quantidade()));

        Setor setor = setorRepository.findById(request.setorDestinoId()).orElse(null);
        if (setor == null) {
            return ResponseEntity.badRequest()
                    .body("Setor não encontrado: " + request.setorDestinoId());
        }

        // Converte SKUs em materialId + quantidade para o service.
        List<LinhaItem> linhas = new ArrayList<>(request.itens().size());
        try {
            for (ItemSaidaDTO item : request.itens()) {
                Material material = materialRepository.findByCodigoSku(item.codigoSku())
                        .orElseThrow(() -> new IllegalArgumentException(
                                "Material não encontrado para o SKU: " + item.codigoSku()));
                linhas.add(new LinhaItem(material.getId(), item.quantidade()));
            }

            Movimentacao salva = movimentacaoService.registrarSaida(
                    setor, request.retiradoPor(), null, linhas);

            SaidaResponseDTO response = new SaidaResponseDTO(
                    salva.getData(),
                    setor.getId(),
                    setor.getNome(),
                    linhas.size(),
                    List.of(salva.getId())   // contrato: lista de movimentações criadas
            );
            log.info("<== [API] Saída #{} registrada com {} item(ns)", salva.getId(), linhas.size());
            return ResponseEntity.ok(response);
        } catch (IllegalArgumentException | IllegalStateException ex) {
            log.warn("Falha ao registrar saída via app: {}", ex.getMessage());
            return ResponseEntity.badRequest().body(ex.getMessage());
        }
    }
}
