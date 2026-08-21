package com.fundacao.aualmoxarifado.service;

import com.fundacao.aualmoxarifado.domain.*;
import com.fundacao.aualmoxarifado.dto.*;
import com.fundacao.aualmoxarifado.repository.AreaRepository;
import com.fundacao.aualmoxarifado.repository.MaterialRepository;
import com.fundacao.aualmoxarifado.repository.SubcategoriaRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.text.Normalizer;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.function.Predicate;

import static com.fundacao.aualmoxarifado.service.LeitorPlanilhaService.LinhaPlanilha;
import static com.fundacao.aualmoxarifado.service.LeitorPlanilhaService.parseDecimal;
import static com.fundacao.aualmoxarifado.service.LeitorPlanilhaService.parseInteiro;

/**
 * Processa UMA linha da planilha de materiais.
 *
 * Está em um bean separado do {@link ImportacaoMaterialService} de propósito:
 * o {@code @Transactional(REQUIRES_NEW)} abaixo só funciona quando a chamada
 * passa pelo proxy do Spring — se o laço estivesse na mesma classe, a
 * auto-invocação ignoraria a anotação e uma única linha ruim derrubaria o lote
 * inteiro. Com uma transação por linha, o erro fica contido: a linha 37 falha,
 * as outras 499 entram.
 *
 * <p><b>Simulação:</b> quando {@code contexto.isSimular()} é true, NADA é
 * gravado — nem material, nem área, nem SKU (o gerador de SKU não é chamado
 * para não consumir números da sequência à toa).</p>
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class ImportacaoMaterialLinhaService {

    private static final String SKU_SIMULADO = "(gerado ao importar)";
    private static final String ORIGEM_ENTRADA = "Importação de planilha";

    private final MaterialRepository materialRepository;
    private final AreaRepository areaRepository;
    private final SubcategoriaRepository subcategoriaRepository;
    private final MaterialService materialService;
    private final MovimentacaoService movimentacaoService;
    private final CadastroAuxiliarImportacaoService cadastroAuxiliar;

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public ResultadoLinhaImportacao processar(LinhaPlanilha linha, ContextoImportacao ctx) {
        Map<String, String> v = linha.valores();
        String nome = ColunaMaterial.NOME.valorDe(v);
        ResultadoLinhaImportacao r = new ResultadoLinhaImportacao(linha.numero(), nome);

        try {
            return processarOuFalhar(v, nome, ctx, r);
        } catch (IllegalArgumentException | IllegalStateException ex) {
            // Erro previsto e com mensagem amigável — vira uma linha vermelha
            // no relatório, sem interromper as demais.
            return r.erro(ex.getMessage());
        } catch (RuntimeException ex) {
            log.warn("Falha inesperada na linha {} da importação", linha.numero(), ex);
            return r.erro("Falha inesperada: " + ex.getClass().getSimpleName()
                    + (ex.getMessage() == null ? "" : " - " + ex.getMessage()));
        }
    }

    // =====================================================================
    // Fluxo principal
    // =====================================================================

    private ResultadoLinhaImportacao processarOuFalhar(Map<String, String> v,
                                                       String nome,
                                                       ContextoImportacao ctx,
                                                       ResultadoLinhaImportacao r) {

        // --- Conversões: falham cedo, com o nome da coluna na mensagem. ---
        Integer estoquePlanilha = parseInteiro(ColunaMaterial.ESTOQUE_ATUAL.valorDe(v), "estoque_atual");
        Integer estoqueMinimo   = parseInteiro(ColunaMaterial.ESTOQUE_MINIMO.valorDe(v), "estoque_minimo");
        BigDecimal valorUnit    = parseDecimal(ColunaMaterial.VALOR_UNITARIO.valorDe(v), "valor_unitario");
        String unidade          = ColunaMaterial.UNIDADE.valorDe(v);
        String skuInformado     = ColunaMaterial.SKU.valorDe(v);

        if (valorUnit != null && valorUnit.signum() < 0) {
            throw new IllegalArgumentException("\"valor_unitario\" não pode ser negativo.");
        }

        // --- Caminho 1: planilha traz SKU → é uma ATUALIZAÇÃO explícita. ---
        if (skuInformado != null) {
            Material existente = materialRepository.findByCodigoSku(skuInformado.trim().toUpperCase(Locale.ROOT))
                    .or(() -> materialRepository.findByCodigoSku(skuInformado.trim()))
                    .orElseThrow(() -> new IllegalArgumentException(
                            "SKU \"" + skuInformado + "\" não existe no sistema. "
                          + "Para cadastrar um material NOVO, deixe a coluna \"sku\" em branco — "
                          + "o código é gerado automaticamente."));

            avisarSeCategoriaDivergente(v, existente, r);
            if (ctx.registrarEDetectarDuplicata("sku:" + existente.getCodigoSku())) {
                r.aviso("Este SKU aparece mais de uma vez no arquivo.");
            }
            return atualizar(existente, nome, unidade, estoqueMinimo, valorUnit, estoquePlanilha, ctx, r);
        }

        // --- Caminho 2: sem SKU → resolve a hierarquia e decide criar/atualizar. ---
        if (nome == null || nome.isBlank()) {
            throw new IllegalArgumentException("A coluna \"nome\" é obrigatória.");
        }

        Area area = resolverArea(v, ctx, r);
        Subcategoria sub = resolverSubcategoria(v, area, ctx, r);

        String chave = chaveNormalizada(area.getNome()) + "|"
                     + chaveNormalizada(sub.getNome()) + "|"
                     + chaveNormalizada(nome);
        if (ctx.registrarEDetectarDuplicata(chave)) {
            r.aviso("Material repetido no arquivo — a segunda ocorrência atualizou a primeira.");
        }

        // Subcategoria transiente só acontece em simulação: nesse caso o
        // material é necessariamente novo (a categoria nem existe ainda).
        Material existente = sub.getId() == null ? null
                : materialRepository.findFirstBySubcategoriaAndNomeIgnoreCase(sub, nome).orElse(null);

        if (existente != null) {
            return atualizar(existente, nome, unidade, estoqueMinimo, valorUnit, estoquePlanilha, ctx, r);
        }
        return criar(nome, sub, unidade, estoqueMinimo, valorUnit, estoquePlanilha, ctx, r);
    }

    // =====================================================================
    // Criação
    // =====================================================================

    private ResultadoLinhaImportacao criar(String nome,
                                           Subcategoria sub,
                                           String unidade,
                                           Integer estoqueMinimo,
                                           BigDecimal valorUnit,
                                           Integer estoquePlanilha,
                                           ContextoImportacao ctx,
                                           ResultadoLinhaImportacao r) {

        // No modo SOMAR o material nasce zerado e a quantidade entra como
        // movimentação de ENTRADA (RN05) — assim a carga fica no histórico.
        // Nos modos DEFINIR/IGNORAR o saldo é gravado direto no cadastro.
        int saldoInicial = switch (ctx.getModoEstoque()) {
            case SOMAR   -> 0;
            case DEFINIR -> estoquePlanilha == null ? 0 : estoquePlanilha;
            case IGNORAR -> 0;
        };

        Material material = Material.builder()
                .nome(nome)
                .unidadeMedida(unidade)
                .estoqueAtual(saldoInicial)
                .estoqueMinimo(estoqueMinimo == null ? 0 : estoqueMinimo)
                .valorUnitario(valorUnit)
                .subcategoria(sub)
                .build();

        if (ctx.isSimular()) {
            String previa = sub.getArea().getSigla() + "-" + sub.getSigla() + "-…";
            if (ctx.getModoEstoque() == ModoEstoqueImportacao.SOMAR && estoquePlanilha != null && estoquePlanilha > 0) {
                r.aviso("Entrada de " + estoquePlanilha + " unidade(s) será registrada.");
            }
            return r.criado(SKU_SIMULADO, "Será criado em " + previa);
        }

        Material salvo = materialService.salvar(material);   // gera o SKU (RF18)

        if (ctx.getModoEstoque() == ModoEstoqueImportacao.SOMAR
                && estoquePlanilha != null && estoquePlanilha > 0) {
            registrarEntrada(salvo, estoquePlanilha);
            r.aviso("Entrada de " + estoquePlanilha + " unidade(s) registrada no histórico.");
        }

        return r.criado(salvo.getCodigoSku(), "Material cadastrado.");
    }

    // =====================================================================
    // Atualização
    // =====================================================================

    private ResultadoLinhaImportacao atualizar(Material material,
                                               String nome,
                                               String unidade,
                                               Integer estoqueMinimo,
                                               BigDecimal valorUnit,
                                               Integer estoquePlanilha,
                                               ContextoImportacao ctx,
                                               ResultadoLinhaImportacao r) {

        // Campos em branco na planilha NÃO apagam o que já está cadastrado —
        // a planilha atualiza só o que ela traz preenchido.
        if (nome != null && !nome.isBlank() && !nome.equals(material.getNome())) {
            material.setNome(nome);
        }
        if (unidade != null) {
            material.setUnidadeMedida(unidade);
        }
        if (estoqueMinimo != null) {
            material.setEstoqueMinimo(estoqueMinimo);
        }
        if (valorUnit != null) {
            material.setValorUnitario(valorUnit);
        }

        String descricaoEstoque = aplicarEstoqueNaAtualizacao(material, estoquePlanilha, ctx, r);

        if (ctx.isSimular()) {
            return r.atualizado(material.getCodigoSku(), "Será atualizado. " + descricaoEstoque);
        }

        materialRepository.save(material);
        return r.atualizado(material.getCodigoSku(), "Cadastro atualizado. " + descricaoEstoque);
    }

    /**
     * Aplica a coluna de estoque conforme o modo escolhido e devolve uma frase
     * curta para o relatório. No modo SOMAR a alteração passa pelo
     * {@code MovimentacaoService} para gerar histórico; no DEFINIR o saldo é
     * sobrescrito (é uma correção de inventário, não uma entrada).
     */
    private String aplicarEstoqueNaAtualizacao(Material material,
                                               Integer estoquePlanilha,
                                               ContextoImportacao ctx,
                                               ResultadoLinhaImportacao r) {
        if (ctx.getModoEstoque() == ModoEstoqueImportacao.IGNORAR || estoquePlanilha == null) {
            return "Estoque mantido em " + material.getEstoqueAtual() + ".";
        }

        int saldoAtual = material.getEstoqueAtual() == null ? 0 : material.getEstoqueAtual();

        if (ctx.getModoEstoque() == ModoEstoqueImportacao.SOMAR) {
            if (estoquePlanilha == 0) {
                return "Estoque mantido em " + saldoAtual + ".";
            }
            if (!ctx.isSimular()) {
                registrarEntrada(material, estoquePlanilha);   // já incrementa o saldo (RN05)
            }
            return "Entrada de " + estoquePlanilha + ": " + saldoAtual + " → " + (saldoAtual + estoquePlanilha) + ".";
        }

        // DEFINIR
        if (saldoAtual != estoquePlanilha) {
            r.aviso("Saldo sobrescrito sem movimentação (modo inventário).");
        }
        material.setEstoqueAtual(estoquePlanilha);
        return "Saldo definido: " + saldoAtual + " → " + estoquePlanilha + ".";
    }

    /**
     * Entrada de estoque com um único item — a movimentação passou a ser
     * cabeçalho + itens, então a linha da planilha vira uma lista de um
     * elemento. O service continua sendo quem incrementa o saldo (RN05).
     */
    private void registrarEntrada(Material material, int quantidade) {
        movimentacaoService.registrarEntrada(
                ORIGEM_ENTRADA, null, null,
                List.of(new MovimentacaoService.LinhaItem(material.getId(), quantidade)));
    }

    // =====================================================================
    // Resolução da hierarquia Área → Subcategoria
    // =====================================================================

    private Area resolverArea(Map<String, String> v, ContextoImportacao ctx, ResultadoLinhaImportacao r) {
        String areaTexto = ColunaMaterial.AREA.valorDe(v);
        String siglaTexto = ColunaMaterial.AREA_SIGLA.valorDe(v);

        if (areaTexto == null && siglaTexto == null) {
            throw new IllegalArgumentException(
                    "A coluna \"area\" é obrigatória para cadastrar um material novo.");
        }

        // Aceita tanto a sigla quanto o nome na mesma coluna — é o que o
        // usuário faz na prática ("ODO" numa linha, "Odontologia" na outra).
        if (siglaTexto != null) {
            var achada = areaRepository.findBySiglaIgnoreCase(siglaTexto);
            if (achada.isPresent()) {
                return achada.get();
            }
        }
        if (areaTexto != null) {
            var achada = areaRepository.findBySiglaIgnoreCase(areaTexto)
                    .or(() -> areaRepository.findByNomeIgnoreCase(areaTexto));
            if (achada.isPresent()) {
                return achada.get();
            }
        }

        String nomeArea = areaTexto != null ? areaTexto : siglaTexto;
        String chave = chaveNormalizada(nomeArea);

        // Os caches só valem na SIMULAÇÃO. Na importação real cada linha commita
        // sozinha, então o banco já é a fonte da verdade para a próxima linha —
        // e guardar entidades aqui só criaria referências obsoletas caso a
        // transação da linha tivesse feito rollback.
        if (ctx.isSimular()) {
            Area pendente = ctx.getAreasPendentes().get(chave);
            if (pendente != null) {
                return pendente;
            }
        }

        if (!ctx.isCriarAusentes()) {
            throw new IllegalArgumentException(
                    "Área \"" + nomeArea + "\" não está cadastrada. Cadastre-a antes "
                  + "ou marque a opção \"Criar áreas e subcategorias que não existirem\".");
        }

        Predicate<String> disponivel = s -> !areaRepository.existsBySiglaIgnoreCase(s)
                && !(ctx.isSimular() && ctx.getSiglasAreaReservadas().contains(s));

        String sigla = siglaTexto != null
                ? validarSigla(siglaTexto, "area_sigla")
                : derivarSigla(nomeArea, disponivel);

        if (!disponivel.test(sigla)) {
            throw new IllegalArgumentException(
                    "A sigla de área \"" + sigla + "\" já está em uso por outra área. "
                  + "Informe uma sigla diferente na coluna \"area_sigla\".");
        }

        if (ctx.isSimular()) {
            Area nova = Area.builder().nome(nomeArea).sigla(sigla).build();  // transiente
            ctx.getSiglasAreaReservadas().add(sigla);
            ctx.getAreasPendentes().put(chave, nova);
            r.aviso("Área \"" + nomeArea + "\" (" + sigla + ") será criada.");
            return nova;
        }

        Area salva = cadastroAuxiliar.criarArea(nomeArea, sigla);
        r.aviso("Área \"" + nomeArea + "\" (" + sigla + ") criada automaticamente.");
        return salva;
    }

    private Subcategoria resolverSubcategoria(Map<String, String> v,
                                              Area area,
                                              ContextoImportacao ctx,
                                              ResultadoLinhaImportacao r) {
        String subTexto = ColunaMaterial.SUBCATEGORIA.valorDe(v);
        String siglaTexto = ColunaMaterial.SUBCATEGORIA_SIGLA.valorDe(v);

        if (subTexto == null && siglaTexto == null) {
            throw new IllegalArgumentException(
                    "A coluna \"subcategoria\" é obrigatória para cadastrar um material novo.");
        }

        String nomeSub = subTexto != null ? subTexto : siglaTexto;
        String chave = chaveNormalizada(area.getNome()) + "|" + chaveNormalizada(nomeSub);

        // Área transiente (simulação) ⇒ nenhuma subcategoria dela existe no banco.
        if (area.getId() != null) {
            if (siglaTexto != null) {
                var achada = subcategoriaRepository.findByAreaIdAndSiglaIgnoreCase(area.getId(), siglaTexto);
                if (achada.isPresent()) {
                    return achada.get();
                }
            }
            if (subTexto != null) {
                var achada = subcategoriaRepository.findByAreaIdAndSiglaIgnoreCase(area.getId(), subTexto)
                        .or(() -> subcategoriaRepository.findByAreaIdAndNomeIgnoreCase(area.getId(), subTexto));
                if (achada.isPresent()) {
                    return achada.get();
                }
            }
        }

        if (ctx.isSimular()) {
            Subcategoria pendente = ctx.getSubcategoriasPendentes().get(chave);
            if (pendente != null) {
                return pendente;
            }
        }

        if (!ctx.isCriarAusentes()) {
            throw new IllegalArgumentException(
                    "Subcategoria \"" + nomeSub + "\" não existe na área \"" + area.getNome()
                  + "\". Cadastre-a antes ou marque a opção "
                  + "\"Criar áreas e subcategorias que não existirem\".");
        }

        // RN11: a sigla precisa ser única DENTRO da área.
        String prefixoReserva = chaveNormalizada(area.getNome()) + "|";
        Predicate<String> disponivel = s ->
                (area.getId() == null || !subcategoriaRepository.existsByAreaIdAndSiglaIgnoreCase(area.getId(), s))
                && !(ctx.isSimular() && ctx.getSiglasSubcategoriaReservadas().contains(prefixoReserva + s));

        String sigla = siglaTexto != null
                ? validarSigla(siglaTexto, "subcategoria_sigla")
                : derivarSigla(nomeSub, disponivel);

        if (!disponivel.test(sigla)) {
            throw new IllegalArgumentException(
                    "A sigla de subcategoria \"" + sigla + "\" já está em uso na área \""
                  + area.getNome() + "\" (RN11). Informe outra na coluna \"subcategoria_sigla\".");
        }

        if (ctx.isSimular()) {
            Subcategoria nova = Subcategoria.builder()      // transiente
                    .nome(nomeSub).sigla(sigla).area(area).proximoSequencial(1)
                    .build();
            ctx.getSiglasSubcategoriaReservadas().add(prefixoReserva + sigla);
            ctx.getSubcategoriasPendentes().put(chave, nova);
            r.aviso("Subcategoria \"" + nomeSub + "\" (" + sigla + ") será criada.");
            return nova;
        }

        Subcategoria salva = cadastroAuxiliar.criarSubcategoria(nomeSub, sigla, area.getId());
        r.aviso("Subcategoria \"" + nomeSub + "\" (" + sigla + ") criada automaticamente.");
        return salva;
    }

    /**
     * O SKU é a identidade física do produto (está impresso na etiqueta), então
     * a importação NUNCA recategoriza um material existente — só avisa que a
     * planilha diverge do cadastro, para o usuário decidir o que fazer.
     */
    private void avisarSeCategoriaDivergente(Map<String, String> v, Material material, ResultadoLinhaImportacao r) {
        String subTexto = ColunaMaterial.SUBCATEGORIA.valorDe(v);
        if (subTexto == null) {
            return;
        }
        Subcategoria atual = material.getSubcategoria();
        boolean bate = chaveNormalizada(subTexto).equals(chaveNormalizada(atual.getNome()))
                    || subTexto.equalsIgnoreCase(atual.getSigla());
        if (!bate) {
            r.aviso("A subcategoria da planilha (\"" + subTexto + "\") difere do cadastro (\""
                  + atual.getNome() + "\"). O SKU foi mantido — recategorize pela tela de edição.");
        }
    }

    // =====================================================================
    // Siglas
    // =====================================================================

    /** Valida uma sigla digitada pelo usuário contra as regras de Area/Subcategoria. */
    private String validarSigla(String bruta, String coluna) {
        String sigla = bruta.trim().toUpperCase(Locale.ROOT);
        if (!sigla.matches("^[A-Z0-9]{2,5}$")) {
            throw new IllegalArgumentException(
                    "\"" + coluna + "\" deve ter de 2 a 5 letras/dígitos sem acento (recebido: " + bruta + ").");
        }
        return sigla;
    }

    /**
     * Deriva uma sigla a partir do nome quando o usuário não informou nenhuma:
     * "Odontologia" → ODO, "Material de Limpeza" → MAT. Se já estiver em uso,
     * acrescenta um número (ODO2, ODO3…), sempre respeitando o limite de 5
     * caracteres das entidades.
     */
    private String derivarSigla(String nome, Predicate<String> disponivel) {
        String base = Normalizer.normalize(nome, Normalizer.Form.NFD)
                                .replaceAll("\\p{M}+", "")
                                .toUpperCase(Locale.ROOT)
                                .replaceAll("[^A-Z0-9]", "");
        if (base.isEmpty()) {
            base = "GER";
        }
        base = base.substring(0, Math.min(3, base.length()));
        while (base.length() < 2) {
            base = base + "X";
        }

        if (disponivel.test(base)) {
            return base;
        }
        for (int i = 2; i <= 999; i++) {
            String sufixo = String.valueOf(i);
            String prefixo = base.substring(0, Math.min(base.length(), 5 - sufixo.length()));
            String candidata = prefixo + sufixo;
            if (disponivel.test(candidata)) {
                return candidata;
            }
        }
        throw new IllegalStateException(
                "Não foi possível gerar uma sigla livre para \"" + nome
              + "\". Informe a sigla manualmente na planilha.");
    }

    /** Chave de comparação: sem acento, minúscula e sem espaços duplicados. */
    private static String chaveNormalizada(String texto) {
        if (texto == null) {
            return "";
        }
        return Normalizer.normalize(texto.trim(), Normalizer.Form.NFD)
                         .replaceAll("\\p{M}+", "")
                         .toLowerCase(Locale.ROOT)
                         .replaceAll("\\s+", " ");
    }
}
