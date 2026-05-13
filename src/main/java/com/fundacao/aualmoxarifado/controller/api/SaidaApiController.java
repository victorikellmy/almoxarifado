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
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

/**
 * Endpoint REST consumido pelo app mobile (RF06 + RF18).
 *
 * Fluxo do app:
 *  1) seleciona Setor
 *  2) bipa N produtos (SKU + quantidade)
 *  3) envia tudo aqui como uma única requisição
 *
 * Cada item vira uma {@link Movimentacao} do tipo SAIDA, reusando
 * {@link MovimentacaoService#registrarSaida} para honrar RN03/RN04
 * (setor obrigatório, status PENDENTE_APROVACAO, validação de estoque).
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

        Setor setor = setorRepository.findById(request.setorDestinoId())
                .orElse(null);
        if (setor == null) {
            return ResponseEntity.badRequest()
                    .body("Setor não encontrado: " + request.setorDestinoId());
        }

        LocalDateTime agora = LocalDateTime.now();
        List<Long> movimentacaoIds = new ArrayList<>(request.itens().size());

        try {
            for (ItemSaidaDTO item : request.itens()) {
                Material material = materialRepository.findByCodigoSku(item.codigoSku())
                        .orElseThrow(() -> new IllegalArgumentException(
                                "Material não encontrado para o SKU: " + item.codigoSku()));

                Movimentacao mov = Movimentacao.builder()
                        .material(material)
                        .setorDestino(setor)
                        .quantidade(item.quantidade())
                        .data(agora)
                        .retiradoPor(request.retiradoPor())
                        .build();

                Movimentacao salva = movimentacaoService.registrarSaida(mov);
                movimentacaoIds.add(salva.getId());
            }
        } catch (IllegalArgumentException | IllegalStateException ex) {
            log.warn("Falha ao registrar saída via app: {}", ex.getMessage());
            return ResponseEntity.badRequest().body(ex.getMessage());
        }

        SaidaResponseDTO response = new SaidaResponseDTO(
                agora,
                setor.getId(),
                setor.getNome(),
                movimentacaoIds.size(),
                movimentacaoIds
        );
        log.info("<== [API] Saída registrada com sucesso: {} movimentações criadas", movimentacaoIds.size());
        return ResponseEntity.ok(response);
    }
}
