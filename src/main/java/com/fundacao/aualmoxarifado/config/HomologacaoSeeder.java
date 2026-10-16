package com.fundacao.aualmoxarifado.config;

import com.fundacao.aualmoxarifado.domain.*;
import com.fundacao.aualmoxarifado.repository.MaterialRepository;
import com.fundacao.aualmoxarifado.repository.MovimentacaoRepository;
import com.fundacao.aualmoxarifado.repository.SetorRepository;
import com.fundacao.aualmoxarifado.repository.UsuarioRepository;
import com.fundacao.aualmoxarifado.service.CompraService;
import com.fundacao.aualmoxarifado.service.CompraService.DadosRecebimento;
import com.fundacao.aualmoxarifado.service.MovimentacaoService;
import com.fundacao.aualmoxarifado.service.MovimentacaoService.LinhaItem;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.CommandLineRunner;
import org.springframework.context.annotation.Profile;
import org.springframework.core.annotation.Order;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

/**
 * Massa de HOMOLOGAÇÃO (perfil "homolog"), complementar ao {@link DataSeeder}.
 *
 * O DataSeeder cria o catálogo (áreas, subcategorias, materiais), entradas e
 * saídas já entregues. Aqui entra o que falta para exercitar cada fluxo do
 * sistema — web e app — sempre pelos services, para respeitar as mesmas
 * regras de produção:
 *
 *  - Setores oficiais FPTO e FA-Saúde (os mesmos da migration V5).
 *  - Usuários de cada perfil: ADMIN (admin), COMPRAS (delva.maria, sarah.luz,
 *    daisy.dias — UsuariosComprasSeeder) e PADRAO (homolog.padrao).
 *  - Saídas em todos os status: pendentes para aprovar, aprovada para
 *    entregar, rejeitada com motivo e saídas de hoje (indicador do Início).
 *  - Compras em todas as etapas: aguardando Diretoria, autorizada pronta para
 *    baixa, realizadas (estoque e direta), patrimonial, não autorizada e
 *    cancelada.
 *
 * O banco do perfil homolog é H2 em memória: tudo é recriado a cada boot.
 * Roteiro de testes: docs/HOMOLOGACAO.md.
 */
@Slf4j
@Component
@Profile("homolog")
@Order(10)   // depois do DataSeeder (@Order 5)
@RequiredArgsConstructor
public class HomologacaoSeeder implements CommandLineRunner {

    static final String USUARIO_PADRAO = "homolog.padrao";

    private final SetorRepository setorRepository;
    private final MaterialRepository materialRepository;
    private final MovimentacaoRepository movimentacaoRepository;
    private final UsuarioRepository usuarioRepository;
    private final MovimentacaoService movimentacaoService;
    private final CompraService compraService;
    private final PasswordEncoder passwordEncoder;

    @Value("${homolog.senha:homolog123}")
    private String senha;

    @Override
    public void run(String... args) {
        if (usuarioRepository.existsByUsernameIgnoreCase(USUARIO_PADRAO)) {
            log.info("[Homologação] Massa já carregada - ignorado.");
            return;
        }
        log.info("[Homologação] Carregando massa de testes...");

        criarSetoresOficiais();
        criarUsuarioPadrao();
        criarSaidas();
        criarCompras();

        log.info("[Homologação] Pronto: {} setores, {} materiais ({} em alerta), {} movimentações.",
                setorRepository.count(), materialRepository.count(),
                materialRepository.countEmAlertaDeEstoque(), movimentacaoRepository.count());
    }

    // ------------------------------------------------------------------ setores

    private static final List<String> SETORES_FPTO = List.of(
            "Compras e Almoxarifado", "Financeiro", "Documentação da Fundação",
            "Transporte e Patrimônio", "Recursos Humanos", "Jurídico", "Conselho",
            "Assistência Social", "Tecnologia da Informação", "Controle Interno",
            "Contratos de Prestação", "Pecúlio e Cadastro", "Recepção");

    private static final List<String> SETORES_FA_SAUDE = List.of(
            "Administração / T.I.", "Financeiro", "Atendimento", "Pecúlio", "FAMCARD",
            "Coordenação", "Faturamento", "Cadastro", "Enfermagem");

    private void criarSetoresOficiais() {
        salvarSetores(SETORES_FPTO, Organizacao.FPTO);
        salvarSetores(SETORES_FA_SAUDE, Organizacao.FA_SAUDE);
    }

    private void salvarSetores(List<String> nomes, Organizacao org) {
        for (String nome : nomes) {
            if (!setorRepository.existsByNomeIgnoreCaseAndOrganizacao(nome, org)) {
                setorRepository.save(Setor.builder().nome(nome).organizacao(org).build());
            }
        }
    }

    private Setor setor(String nome, Organizacao org) {
        return setorRepository.findByOrganizacaoOrderByNomeAsc(org).stream()
                .filter(s -> s.getNome().equalsIgnoreCase(nome))
                .findFirst()
                .orElseThrow(() -> new IllegalStateException("Setor de homologação ausente: " + nome));
    }

    // ------------------------------------------------------------------ usuários

    private void criarUsuarioPadrao() {
        usuarioRepository.save(Usuario.builder()
                .nome("Usuário de Homologação")
                .username(USUARIO_PADRAO)
                .senhaHash(passwordEncoder.encode(senha))
                .perfil(PerfilUsuario.PADRAO)
                .ativo(true)
                .build());
    }

    // ------------------------------------------------------------------ saídas

    private Material material(String trechoNome) {
        return materialRepository.findAll().stream()
                .filter(m -> m.getNome().toLowerCase().contains(trechoNome.toLowerCase()))
                .findFirst()
                .orElseThrow(() -> new IllegalStateException("Material de homologação ausente: " + trechoNome));
    }

    private void criarSaidas() {
        Setor atendimento = setor("Atendimento", Organizacao.FA_SAUDE);
        Setor enfermagem = setor("Enfermagem", Organizacao.FA_SAUDE);
        Setor financeiroFa = setor("Financeiro", Organizacao.FA_SAUDE);
        Setor financeiroFpto = setor("Financeiro", Organizacao.FPTO);
        Setor juridico = setor("Jurídico", Organizacao.FPTO);
        Setor recepcao = setor("Recepção", Organizacao.FPTO);

        // Pendentes de aprovação (fluxo web, RN04): o ADMIN aprova/entrega/rejeita.
        saida(enfermagem, "Edna Nogueira", "Reposição semanal da enfermagem", -1, null,
                linha("Luva de Procedimento", 4), linha("Algodão Rolete", 6));
        saida(financeiroFa, "Lucilene Alves", null, -2, null,
                linha("Resma de Papel A4", 5), linha("Caneta Esferográfica", 20));
        saida(juridico, "Lucas Mendes", "Material para audiência", 0, null,
                linha("Resma de Papel A4", 2));

        // Aprovada (estoque já baixado), aguardando a entrega.
        saida(recepcao, "Maria Lourdes", null, -3, StatusMovimentacao.APROVADO,
                linha("Sabonete Líquido", 4), linha("Desinfetante", 2));

        // Rejeitada, com motivo.
        Movimentacao rejeitada = saida(financeiroFpto, "Raysa Vilarins", null, -4, null,
                linha("Toner Impressora", 2));   // o DataSeeder deixa só 2 em estoque
        movimentacaoService.alterarStatus(rejeitada.getId(), StatusMovimentacao.REJEITADO,
                "Toner em falta; aguardar a compra em andamento.");

        // Saídas de hoje pelo balcão (como o app faz: nascem entregues).
        movimentacaoService.registrarSaida(atendimento, "Luana Costa", null,
                List.of(linha("Caneta Esferográfica", 10)), true);
        movimentacaoService.registrarSaida(enfermagem, "Cecília Arruda", null,
                List.of(linha("Agulha Gengival", 2), linha("Luva de Procedimento", 1)), true);
    }

    private LinhaItem linha(String trechoNome, int quantidade) {
        return new LinhaItem(material(trechoNome).getId(), quantidade);
    }

    /** Registra saída pendente e, se pedido, avança o status; data no passado. */
    private Movimentacao saida(Setor setor, String retiradoPor, String obs, int diasAtras,
                               StatusMovimentacao statusFinal, LinhaItem... linhas) {
        Movimentacao mov = movimentacaoService.registrarSaida(setor, retiradoPor, obs, List.of(linhas));
        mov.setData(LocalDateTime.now().plusDays(diasAtras).withHour(9).withMinute(30));
        movimentacaoRepository.save(mov);
        if (statusFinal != null) {
            movimentacaoService.alterarStatus(mov.getId(), statusFinal);
        }
        return mov;
    }

    // ------------------------------------------------------------------ compras

    private void criarCompras() {
        Setor enfermagem = setor("Enfermagem", Organizacao.FA_SAUDE);
        Setor coordenacao = setor("Coordenação", Organizacao.FA_SAUDE);
        Setor ti = setor("Tecnologia da Informação", Organizacao.FPTO);
        Setor transporte = setor("Transporte e Patrimônio", Organizacao.FPTO);

        // 1) Aguardando a decisão da Diretoria (testar autorizar / não autorizar).
        precompra(TipoCompra.ESTOQUE, null, "Dental Speed", "Parte nº 041/2026",
                "Reposição de materiais odontológicos", "Delva Maria",
                item("Resina Z350", 20, "145.00"), item("Adesivo Single Bond", 10, "89.90"));

        // 2) Autorizada, pronta para a baixa (testar recebimento com NF).
        Compra pronta = precompra(TipoCompra.DIRETA, enfermagem, "Cremer", "Ofício nº 118/2026",
                "Luvas e algodão para a enfermagem", "Edna Nogueira",
                item("Luva de Procedimento", 10, "45.00"), item("Algodão Rolete", 15, "9.50"));
        autorizar(pronta);

        // 3) Realizada — compra de ESTOQUE (entrada no estoque geral).
        Compra estoque = precompra(TipoCompra.ESTOQUE, null, "Kalunga S/A", "Parte nº 033/2026",
                "Papelaria do trimestre", "Sarah Luz",
                item("Resma de Papel A4", 40, "28.90"), item("Caneta Esferográfica", 100, "2.50"));
        autorizar(estoque);
        compraService.darBaixa(estoque.getId(), "NF-55120", new BigDecimal("1406.00"), null,
                new DadosRecebimento(null, null, false, "delva.maria"));

        // 4) Realizada — compra DIRETA (repasse ao setor, entra no consumo).
        Compra direta = precompra(TipoCompra.DIRETA, ti, "InfoSupri Comércio", "Parte nº 029/2026",
                "Toners para a TI", "Samuel",
                item("Toner Impressora", 4, "289.00"));
        autorizar(direta);
        compraService.darBaixa(direta.getId(), "NF-88014", new BigDecimal("1150.00"), null,
                new DadosRecebimento("Samuel", null, false, "sarah.luz"));

        // 5) Patrimonial autorizada, aguardando baixa (itens descritos livremente).
        Compra patrimonial = precompra(TipoCompra.PATRIMONIAL, transporte, "Magazine Escritório",
                "Ofício nº 120/2026", "Mobiliário para a sala de Transporte", "Carlos Regino",
                itemLivre("Cadeira giratória com braços", "Resma", 4, "690.00"),
                itemLivre("Armário de aço 2 portas", "Resma", 1, "1250.00"));
        autorizar(patrimonial);

        // 6) Não autorizada pela Diretoria (vira cancelada, com parecer).
        Compra negada = precompra(TipoCompra.DIRETA, coordenacao, "Dabi Atlante", "Ofício nº 097/2026",
                "Caneta de alta rotação extra", "Frederico Neto",
                item("Caneta de Alta Rotação", 2, "680.00"));
        compraService.registrarAutorizacao(negada.getId(), AutorizacaoDiretoria.NAO_AUTORIZADA,
                "Sem dotação orçamentária neste exercício.", "delva.maria");

        // 7) Cancelada antes da decisão.
        Compra cancelada = precompra(TipoCompra.ESTOQUE, null, "Distribuidora ClearMix", null,
                "Produtos de limpeza (duplicada)", "Daisy Dias",
                item("Desinfetante", 10, "32.00"));
        compraService.cancelar(cancelada.getId());
    }

    private Compra precompra(TipoCompra tipo, Setor setor, String fornecedor, String documento,
                             String assunto, String solicitante, ItemCompra... itens) {
        Compra c = Compra.builder()
                .tipo(tipo)
                .setorSolicitante(setor)
                .fornecedor(fornecedor)
                .numeroDocumento(documento)
                .assunto(assunto)
                .solicitanteDocumento(solicitante)
                .dataDocumento(documento == null ? null : LocalDate.now().minusDays(7))
                .observacao("Massa de homologação")
                .build();
        return compraService.criarPreCompra(c, new ArrayList<>(List.of(itens)), null);
    }

    private void autorizar(Compra c) {
        compraService.registrarAutorizacao(c.getId(), AutorizacaoDiretoria.AUTORIZADA,
                "Autorizado em reunião da Diretoria.", "delva.maria");
    }

    private ItemCompra item(String trechoNome, int qtd, String valor) {
        return ItemCompra.builder()
                .material(material(trechoNome))
                .quantidade(qtd)
                .valorUnitario(new BigDecimal(valor))
                .build();
    }

    /** Item sem vínculo com o estoque: descrição livre + grupo/subgrupo (herdado de um material parecido). */
    private ItemCompra itemLivre(String descricao, String trechoMaterialDoGrupo, int qtd, String valor) {
        return ItemCompra.builder()
                .descricao(descricao)
                .subcategoria(material(trechoMaterialDoGrupo).getSubcategoria())
                .quantidade(qtd)
                .valorUnitario(new BigDecimal(valor))
                .build();
    }
}
