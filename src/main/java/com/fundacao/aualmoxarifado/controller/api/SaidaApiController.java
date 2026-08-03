package com.fundacao.aualmoxarifado.controller.api;

import com.fundacao.aualmoxarifado.domain.Material;
import com.fundacao.aualmoxarifado.domain.Movimentacao;
import com.fundacao.aualmoxarifado.domain.Setor;
import com.fundacao.aualmoxarifado.domain.StatusMovimentacao;
import com.fundacao.aualmoxarifado.domain.TipoMovimentacao;
import com.fundacao.aualmoxarifado.dto.ItemSaidaDTO;
import com.fundacao.aualmoxarifado.dto.MovimentacaoResumoDTO;
import com.fundacao.aualmoxarifado.dto.PageResponse;
import com.fundacao.aualmoxarifado.dto.SaidaRequestDTO;
import com.fundacao.aualmoxarifado.dto.SaidaResponseDTO;
import com.fundacao.aualmoxarifado.exception.RecursoNaoEncontradoException;
import com.fundacao.aualmoxarifado.exception.SkuFormatoInvalidoException;
import com.fundacao.aualmoxarifado.repository.MaterialRepository;
import com.fundacao.aualmoxarifado.repository.SetorRepository;
import com.fundacao.aualmoxarifado.service.IdempotencyService;
import com.fundacao.aualmoxarifado.service.MovimentacaoService;
import com.fundacao.aualmoxarifado.service.MovimentacaoService.LinhaItem;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.web.PageableDefault;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Pattern;

/**
 * Endpoint REST consumido pelo app mobile (RF06 + RF18).
 *
 * <p>Fluxo: o app seleciona um Setor, bipa N produtos (SKU + quantidade) e
 * envia tudo aqui em uma única requisição. Toda requisição vira <b>uma única
 * Movimentacao multi-item</b> do tipo SAIDA reusando
 * {@link MovimentacaoService#registrarSaida} (RN03 — setor obrigatório, RN04
 * — nasce PENDENTE_APROVACAO, estoque debitado só após aprovação).</p>
 *
 * <p>Tratamento de erros: o controller <b>não captura exceções</b>. Toda
 * sinalização de falha (SKU inválido/inexistente, setor inexistente, saldo
 * insuficiente) sai como {@code ErroResponse} via
 * {@link com.fundacao.aualmoxarifado.exception.GlobalExceptionHandler}.</p>
 *
 * <p>Idempotência: aceita header {@code Idempotency-Key} (opcional, mas
 * recomendado). O app deve gerar um UUID por bipagem; reenvios com a mesma
 * chave em até 10 minutos devolvem a mesma resposta sem reprocessar — protege
 * contra leituras duplicadas do leitor de código de barras e clique duplo no
 * botão "Finalizar".</p>
 */
@RestController
@RequestMapping("/api/saidas")
@RequiredArgsConstructor
@Slf4j
public class SaidaApiController {

    /** Sanidade mínima para um código bipado: sem espaços/controle, 3–64 chars. */
    private static final Pattern SKU_VALIDO = Pattern.compile("^[\\p{Print}&&[^\\s]]{3,64}$");

    private final MovimentacaoService movimentacaoService;
    private final MaterialRepository materialRepository;
    private final SetorRepository setorRepository;
    private final IdempotencyService idempotencyService;

    /**
     * Listagem paginada de saídas — espelha o {@code POST /api/saidas}.
     * Internamente delega para a Specification de Movimentacao com o tipo
     * fixado em {@link TipoMovimentacao#SAIDA}, então os filtros aceitos são
     * os mesmos da listagem geral de movimentações, exceto {@code tipo}.
     */
    @GetMapping
    public PageResponse<MovimentacaoResumoDTO> listar(
            @RequestParam(required = false) StatusMovimentacao status,
            @RequestParam(required = false) Long setorId,
            @RequestParam(required = false) Long materialId,
            @RequestParam(required = false)
            @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) LocalDateTime inicio,
            @RequestParam(required = false)
            @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) LocalDateTime fim,
            @PageableDefault(size = 20, sort = "data", direction = Sort.Direction.DESC)
            Pageable pageable) {

        // Mesmo endpoint da listagem geral com o tipo fixado — lógica única no service.
        return PageResponse.of(movimentacaoService.listarResumo(
                TipoMovimentacao.SAIDA, status, materialId, setorId, inicio, fim, pageable));
    }

    @GetMapping("/{id}")
    public MovimentacaoResumoDTO buscar(@PathVariable Long id) {
        Movimentacao mov = movimentacaoService.buscar(id);
        if (mov.getTipo() != TipoMovimentacao.SAIDA) {
            // Mantém o /api/saidas semanticamente fechado: outras movimentações
            // são acessíveis em /api/movimentacoes/{id}.
            throw new RecursoNaoEncontradoException("Saida", id);
        }
        return MovimentacaoResumoDTO.from(mov);
    }

    @PostMapping
    public ResponseEntity<SaidaResponseDTO> registrarSaida(
            @Valid @RequestBody SaidaRequestDTO request,
            @RequestHeader(value = "Idempotency-Key", required = false) String idempotencyKey) {

        log.info("[API] Saída recebida: setorId={}, retiradoPor={}, itens={}, idempotencyKey={}",
                request.setorDestinoId(), request.retiradoPor(), request.itens().size(), idempotencyKey);

        SaidaResponseDTO response = idempotencyService.executar(
                idempotencyKey,
                () -> processar(request));

        return ResponseEntity.ok(response);
    }

    private SaidaResponseDTO processar(SaidaRequestDTO request) {
        Setor setor = setorRepository.findById(request.setorDestinoId())
                .orElseThrow(() -> new RecursoNaoEncontradoException("Setor", request.setorDestinoId()));

        // Resolve todos os SKUs bipados numa única query IN, em vez de 1 SELECT
        // por item — uma saída de 50 bipagens passa de 50 queries para 1.
        Map<String, Long> idPorSku = new HashMap<>();
        List<String> skus = request.itens().stream()
                .map(item -> sanitizarSku(item.codigoSku()))
                .distinct()
                .toList();
        for (Material material : materialRepository.findByCodigoSkuIn(skus)) {
            idPorSku.put(material.getCodigoSku(), material.getId());
        }

        List<LinhaItem> linhas = new ArrayList<>(request.itens().size());
        for (ItemSaidaDTO item : request.itens()) {
            String sku = sanitizarSku(item.codigoSku());
            Long materialId = idPorSku.get(sku);
            if (materialId == null) {
                throw new RecursoNaoEncontradoException(
                        "Material para o SKU '" + sku + "' não encontrado.");
            }
            linhas.add(new LinhaItem(materialId, item.quantidade()));
        }

        Movimentacao salva = movimentacaoService.registrarSaida(
                setor, request.retiradoPor(), null, linhas);

        log.info("[API] Saída #{} registrada com {} item(ns)", salva.getId(), linhas.size());
        return new SaidaResponseDTO(
                salva.getData(),
                setor.getId(),
                setor.getNome(),
                linhas.size(),
                List.of(salva.getId()));
    }

    /**
     * Normaliza e valida o código bipado. Bipos podem vir de leitores
     * diferentes (1D, 2D, RFID) com padding/whitespace acidental — fazemos
     * trim + uppercase e rejeitamos só o que é claramente lixo.
     */
    private String sanitizarSku(String codigo) {
        if (codigo == null) {
            throw new SkuFormatoInvalidoException("(nulo)");
        }
        String limpo = codigo.trim().toUpperCase();
        if (!SKU_VALIDO.matcher(limpo).matches()) {
            throw new SkuFormatoInvalidoException(codigo);
        }
        return limpo;
    }
}
