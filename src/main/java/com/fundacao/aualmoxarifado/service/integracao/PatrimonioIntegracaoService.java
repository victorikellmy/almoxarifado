package com.fundacao.aualmoxarifado.service.integracao;

import com.fundacao.aualmoxarifado.domain.*;
import com.fundacao.aualmoxarifado.dto.integracao.RecebimentoPatrimonioPayload;
import com.fundacao.aualmoxarifado.repository.CompraRepository;
import com.fundacao.aualmoxarifado.repository.EnvioPatrimonioRepository;
import com.fundacao.aualmoxarifado.service.AnexoStorageService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.core.io.Resource;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Base64;
import java.util.EnumSet;
import java.util.List;
import java.util.Optional;

/**
 * Integração Almoxarifado → Patrimônio.
 *
 * <ol>
 *   <li>{@link #registrarEnvio} roda na transação da baixa: grava o outbox
 *       ({@link EnvioPatrimonio} PENDENTE). Nada de rede aqui.</li>
 *   <li>{@link #enviar} (chamado logo após a baixa e pelo {@link #reenviarPendentes}
 *       agendado) monta o payload com itens, destino e a NF em base64 e faz o POST.
 *       Sucesso → ENVIADO; falha → ERRO com o motivo, para nova tentativa.</li>
 * </ol>
 */
@Service
@Slf4j
@RequiredArgsConstructor
public class PatrimonioIntegracaoService {

    private static final int MAX_TENTATIVAS = 200;

    private final EnvioPatrimonioRepository envioRepository;
    private final CompraRepository compraRepository;
    private final AnexoStorageService anexoStorageService;
    private final PatrimonioClient client;

    public boolean isConfigurado() {
        return client.isConfigurado();
    }

    /** Cria (ou reaproveita) o registro de envio da compra. Transação do chamador. */
    @Transactional
    public EnvioPatrimonio registrarEnvio(Compra compra) {
        return envioRepository.findByCompraId(compra.getId()).orElseGet(() ->
                envioRepository.save(EnvioPatrimonio.builder().compra(compra).build()));
    }

    @Transactional(readOnly = true)
    public Optional<EnvioPatrimonio> envioDa(Long compraId) {
        return envioRepository.findByCompraId(compraId);
    }

    @Transactional(readOnly = true)
    public long pendentes() {
        return envioRepository.countByStatusIn(EnumSet.of(StatusEnvioPatrimonio.PENDENTE, StatusEnvioPatrimonio.ERRO));
    }

    /**
     * Tenta enviar agora o recebimento da compra. Nunca lança: o resultado fica no
     * registro de envio (ENVIADO ou ERRO + motivo). Transação própria para que uma
     * falha de rede não interfira na transação de quem chamou.
     */
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public EnvioPatrimonio enviar(Long compraId) {
        EnvioPatrimonio envio = envioRepository.findByCompraId(compraId)
                .orElseThrow(() -> new IllegalArgumentException("Compra " + compraId + " não está na fila do Patrimônio."));
        if (envio.getStatus() == StatusEnvioPatrimonio.ENVIADO) {
            return envio;
        }
        envio.setTentativas(envio.getTentativas() + 1);
        envio.setUltimaTentativaEm(LocalDateTime.now());

        if (!client.isConfigurado()) {
            envio.setStatus(StatusEnvioPatrimonio.PENDENTE);
            envio.setUltimoErro("Integração não configurada (defina PATRIMONIO_URL). O envio será feito automaticamente depois.");
            return envioRepository.save(envio);
        }

        try {
            PatrimonioClient.Resultado r = client.enviarRecebimento(montarPayloadDaCompra(compraId));
            envio.setStatus(StatusEnvioPatrimonio.ENVIADO);
            envio.setEnviadoEm(LocalDateTime.now());
            envio.setIdExterno(r.idExterno());
            envio.setUltimoErro(null);
            log.info("[Patrimônio] Compra #{} enviada ao Patrimônio (idExterno={}).", compraId, r.idExterno());
        } catch (Exception e) {
            envio.setStatus(StatusEnvioPatrimonio.ERRO);
            envio.setUltimoErro(truncar(e.getMessage() == null ? e.toString() : e.getMessage(), 1000));
            log.warn("[Patrimônio] Falha ao enviar compra #{} (tentativa {}): {}", compraId, envio.getTentativas(), envio.getUltimoErro());
        }
        return envioRepository.save(envio);
    }

    /** Reenvio automático dos registros PENDENTE/ERRO. */
    @Scheduled(fixedDelayString = "${patrimonio.reenvio-intervalo-ms:600000}", initialDelayString = "60000")
    public void reenviarPendentes() {
        if (!client.isConfigurado()) return;
        List<EnvioPatrimonio> fila = envioRepository.findByStatusInAndTentativasLessThanOrderByCriadoEmAsc(
                EnumSet.of(StatusEnvioPatrimonio.PENDENTE, StatusEnvioPatrimonio.ERRO), MAX_TENTATIVAS);
        if (fila.isEmpty()) return;
        log.info("[Patrimônio] Reenviando {} recebimento(s) pendente(s).", fila.size());
        for (EnvioPatrimonio e : fila) {
            enviar(e.getCompra().getId());
        }
    }

    // ------------------------------------------------------------------ payload

    /**
     * Carrega a compra com itens, materiais, setores e anexos (mesma sessão) e monta o
     * contrato enviado ao Patrimônio. Público para inspeção/testes.
     */
    @Transactional(readOnly = true)
    public RecebimentoPatrimonioPayload montarPayloadDaCompra(Long compraId) {
        Compra compra = compraRepository.findByIdComItens(compraId)
                .orElseThrow(() -> new IllegalStateException("Compra " + compraId + " não encontrada."));
        // segunda query na mesma sessão hidrata compra.anexos na mesma instância
        compraRepository.findByIdComAnexos(compraId);
        return montarPayload(compra);
    }

    RecebimentoPatrimonioPayload montarPayload(Compra c) {
        Setor destino = c.getSetorEntrega() != null ? c.getSetorEntrega() : c.getSetorSolicitante();

        List<RecebimentoPatrimonioPayload.Item> itens = c.getItens().stream()
                .map(i -> new RecebimentoPatrimonioPayload.Item(
                        i.getNomeItem(),
                        i.getMaterial() != null ? i.getMaterial().getCodigoSku() : null,
                        i.getQuantidade(),
                        i.getValorUnitario()))
                .toList();

        List<RecebimentoPatrimonioPayload.Anexo> anexos = new ArrayList<>();
        for (AnexoCompra a : c.getAnexos()) {
            String base64 = null;
            if (a.getTipo() == TipoAnexoCompra.NOTA_FISCAL) {
                base64 = lerBase64(a);
            }
            anexos.add(new RecebimentoPatrimonioPayload.Anexo(
                    a.getTipo().name(), a.getNomeOriginal(), a.getContentType(), a.getTamanhoBytes(), base64));
        }

        String observacao = juntar(
                c.getObservacao(),
                c.getNumeroDocumento() != null ? "Parte/Ofício " + c.getNumeroDocumento() : null,
                c.getAssunto());

        return new RecebimentoPatrimonioPayload(
                "ALMOXARIFADO",
                c.getId(),
                c.getNumeroDocumento(),
                c.getNumeroSgd(),
                c.getAssunto(),
                c.getSolicitanteDocumento(),
                c.getFornecedor(),
                c.getNumeroNotaFiscal(),
                c.getDataRecebimento(),
                c.getDataRecebimento() != null ? c.getDataRecebimento().toLocalDate() : null,
                c.getValorRealFinal() != null ? c.getValorRealFinal() : c.getValorEstimado(),
                c.getRetiradoPor(),
                destino != null ? destino.getNome() : null,
                destino != null ? destino.getCodigoCentroCusto() : null,
                c.getRecebidoPor(),
                observacao,
                itens,
                anexos);
    }

    private String lerBase64(AnexoCompra a) {
        try {
            Resource r = anexoStorageService.carregar(a.getCaminhoArmazenado());
            try (var in = r.getInputStream()) {
                return Base64.getEncoder().encodeToString(in.readAllBytes());
            }
        } catch (Exception e) {
            log.warn("[Patrimônio] Não foi possível ler o anexo {} da compra: {}", a.getNomeOriginal(), e.toString());
            return null;
        }
    }

    private static String juntar(String... partes) {
        List<String> l = new ArrayList<>();
        for (String p : partes) if (p != null && !p.isBlank()) l.add(p.trim());
        return l.isEmpty() ? null : String.join(" · ", l);
    }

    private static String truncar(String s, int max) {
        return s.length() <= max ? s : s.substring(0, max - 3) + "...";
    }
}
