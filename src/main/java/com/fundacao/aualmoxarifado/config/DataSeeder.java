package com.fundacao.aualmoxarifado.config;

import com.fundacao.aualmoxarifado.domain.*;
import com.fundacao.aualmoxarifado.repository.*;
import com.fundacao.aualmoxarifado.service.MaterialService;
import com.fundacao.aualmoxarifado.service.MovimentacaoService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.CommandLineRunner;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.Map;

/**
 * Popula o banco com dados realistas para a apresentação do sistema.
 *
 * ESTRUTURA HIERÁRQUICA (RF17):
 *   Áreas:
 *     - ODONTOLOGIA (ODO)  → vinda da planilha "ESTOQUE FUNDAÇÃO 2025"
 *     - ESCRITÓRIO  (ESC)
 *     - LIMPEZA     (LIM)
 *     - EPI         (EPI)
 *     - INFORMÁTICA (INF)
 *
 *   Subcategorias da Odontologia (vindas das abas da planilha):
 *     CONSUMO, ENDO, DESCARTÁVEIS, BROCAS, DIVERSOS, ORTODONTIA,
 *     INSTRUMENTAIS, EQUIPAMENTOS.
 *
 * SKU (RF18) é gerado automaticamente pelo {@code MaterialService.salvar} —
 * nunca passamos o código manualmente aqui.
 *
 * Como desativar: este componente está marcado com {@code @Profile("dev")}.
 * Para desligar, troque o perfil ativo em application.properties para algo
 * diferente de "dev" ou comente {@code @Component}.
 */
@Slf4j
@Component
@Profile("dev")
@RequiredArgsConstructor
public class DataSeeder implements CommandLineRunner {

    private final AreaRepository areaRepository;
    private final SubcategoriaRepository subcategoriaRepository;
    private final MaterialRepository materialRepository;
    private final SetorRepository setorRepository;
    private final MovimentacaoRepository movimentacaoRepository;
    private final MaterialService materialService;
    private final MovimentacaoService movimentacaoService;

    @Override
    public void run(String... args) {
        // Idempotência: se já houver áreas, assume que o seed rodou.
        if (areaRepository.count() > 0) {
            log.info("[DataSeeder] Banco já populado - seed ignorado.");
            return;
        }

        log.info("[DataSeeder] Iniciando carga de dados de apresentação...");

        Map<String, Area> areas = criarAreas();
        Map<String, Subcategoria> subs = criarSubcategorias(areas);
        Map<String, Setor> setores = criarSetores();
        Map<String, Material> materiais = criarMateriais(subs);

        criarEntradas(materiais);
        criarSaidasAprovadas(materiais, setores);

        log.info("[DataSeeder] Carga concluída: {} áreas, {} subcategorias, "
                       + "{} setores, {} materiais, {} movimentações.",
                areaRepository.count(),
                subcategoriaRepository.count(),
                setorRepository.count(),
                materialRepository.count(),
                movimentacaoRepository.count());
    }

    // ========================================================
    // ÁREAS (RF17 - nível 1)
    // ========================================================
    private Map<String, Area> criarAreas() {
        Map<String, Area> map = new HashMap<>();
        map.put("ODO", areaRepository.save(Area.builder()
                .nome("Odontologia").sigla("ODO")
                .descricao("Materiais clínicos e instrumentais odontológicos").build()));
        map.put("ESC", areaRepository.save(Area.builder()
                .nome("Escritório").sigla("ESC")
                .descricao("Papelaria e materiais administrativos").build()));
        map.put("LIM", areaRepository.save(Area.builder()
                .nome("Limpeza").sigla("LIM")
                .descricao("Produtos de higiene e conservação").build()));
        map.put("EPI", areaRepository.save(Area.builder()
                .nome("EPI").sigla("EPI")
                .descricao("Equipamentos de Proteção Individual").build()));
        map.put("INF", areaRepository.save(Area.builder()
                .nome("Informática").sigla("INF")
                .descricao("Suprimentos e periféricos de TI").build()));
        return map;
    }

    // ========================================================
    // SUBCATEGORIAS (RF17 - nível 2)
    // Para Odontologia, espelham as abas da planilha enviada pelo cliente.
    // ========================================================
    private Map<String, Subcategoria> criarSubcategorias(Map<String, Area> a) {
        Map<String, Subcategoria> map = new HashMap<>();

        // --- Odontologia ---
        map.put("ODO_CON", saveSub(a.get("ODO"), "Consumo", "CON"));
        map.put("ODO_END", saveSub(a.get("ODO"), "Endodontia", "END"));
        map.put("ODO_DES", saveSub(a.get("ODO"), "Descartáveis", "DES"));
        map.put("ODO_BRO", saveSub(a.get("ODO"), "Brocas", "BRO"));
        map.put("ODO_DIV", saveSub(a.get("ODO"), "Diversos", "DIV"));
        map.put("ODO_ORT", saveSub(a.get("ODO"), "Ortodontia", "ORT"));
        map.put("ODO_INS", saveSub(a.get("ODO"), "Instrumentais", "INS"));
        map.put("ODO_EQU", saveSub(a.get("ODO"), "Equipamentos", "EQU"));

        // --- Demais áreas (subcategorias mínimas para o seed) ---
        map.put("ESC_PAP", saveSub(a.get("ESC"), "Papelaria", "PAP"));
        map.put("LIM_GER", saveSub(a.get("LIM"), "Geral", "GER"));
        map.put("EPI_PRO", saveSub(a.get("EPI"), "Proteção", "PRO"));
        map.put("INF_SUP", saveSub(a.get("INF"), "Suprimentos", "SUP"));

        return map;
    }

    private Subcategoria saveSub(Area area, String nome, String sigla) {
        return subcategoriaRepository.save(Subcategoria.builder()
                .area(area).nome(nome).sigla(sigla)
                .proximoSequencial(1).build());
    }

    // ========================================================
    // SETORES (RF04)
    // ========================================================
    private Map<String, Setor> criarSetores() {
        Map<String, Setor> map = new HashMap<>();
        map.put("DIR", setorRepository.save(Setor.builder()
                .nome("Diretoria").responsavel("Marcos Antunes")
                .codigoCentroCusto("CC-001").build()));
        map.put("RH", setorRepository.save(Setor.builder()
                .nome("Recursos Humanos").responsavel("Patrícia Lemos")
                .codigoCentroCusto("CC-010").build()));
        map.put("TI", setorRepository.save(Setor.builder()
                .nome("Tecnologia da Informação").responsavel("Rafael Souza")
                .codigoCentroCusto("CC-020").build()));
        map.put("MANUT", setorRepository.save(Setor.builder()
                .nome("Operacional/Manutenção").responsavel("Carlos Pereira")
                .codigoCentroCusto("CC-030").build()));
        map.put("FIN", setorRepository.save(Setor.builder()
                .nome("Financeiro").responsavel("Juliana Martins")
                .codigoCentroCusto("CC-040").build()));
        map.put("CLIN", setorRepository.save(Setor.builder()
                .nome("Clínica Odontológica").responsavel("Dra. Ana Beatriz")
                .codigoCentroCusto("CC-100").build()));
        return map;
    }

    // ========================================================
    // MATERIAIS (RF12 + RF18)
    // O SKU NÃO é passado no builder — quem gera é o MaterialService.
    // ========================================================
    private Map<String, Material> criarMateriais(Map<String, Subcategoria> s) {
        Map<String, Material> map = new HashMap<>();

        // --- Odontologia / Consumo (vindos da planilha) ---
        map.put("ACIDO_FOSF", criarMaterial(s.get("ODO_CON"),
                "Ácido Fosfórico 37% Gel (kit c/ 3 seringas)", "Kit",
                15, new BigDecimal("18.50")));
        map.put("ADESIVO_BOND", criarMaterial(s.get("ODO_CON"),
                "Adesivo Single Bond Universal 5ml", "Frasco",
                10, new BigDecimal("89.90")));
        map.put("RESINA_Z350", criarMaterial(s.get("ODO_CON"),
                "Resina Z350 XT A2", "Unidade",
                20, new BigDecimal("145.00")));

        // --- Odontologia / Endodontia ---
        map.put("LIMA_K", criarMaterial(s.get("ODO_END"),
                "Lima K Flexofile #25 (cx 6un)", "Caixa",
                8, new BigDecimal("78.00")));
        map.put("AGULHA_ENDO", criarMaterial(s.get("ODO_END"),
                "Agulha p/ Irrigação Endo Eze (c/5)", "Pacote",
                15, new BigDecimal("32.00")));

        // --- Odontologia / Descartáveis ---
        map.put("ROLETE_ALG", criarMaterial(s.get("ODO_DES"),
                "Algodão Rolete (pct c/ 100un)", "Pacote",
                30, new BigDecimal("9.50")));
        map.put("AGULHA_GENG", criarMaterial(s.get("ODO_DES"),
                "Agulha Gengival Curta (cx c/ 100)", "Caixa",
                12, new BigDecimal("38.00")));

        // --- Odontologia / Brocas ---
        map.put("BROCA_1011", criarMaterial(s.get("ODO_BRO"),
                "Broca Diamantada Esférica 1011", "Unidade",
                25, new BigDecimal("12.50")));
        map.put("BROCA_1012", criarMaterial(s.get("ODO_BRO"),
                "Broca Diamantada Esférica 1012", "Unidade",
                25, new BigDecimal("12.50")));

        // --- Odontologia / Equipamentos ---
        map.put("CANETA_ALTA", criarMaterial(s.get("ODO_EQU"),
                "Caneta de Alta Rotação Saca-Broca", "Unidade",
                3, new BigDecimal("680.00")));

        // --- Escritório ---
        map.put("PAPEL_A4", criarMaterial(s.get("ESC_PAP"),
                "Resma de Papel A4 75g", "Pacote",
                15, new BigDecimal("28.90")));
        map.put("CANETA", criarMaterial(s.get("ESC_PAP"),
                "Caneta Esferográfica Azul", "Unidade",
                40, new BigDecimal("2.50")));

        // --- Limpeza ---
        map.put("DESINFETANTE", criarMaterial(s.get("LIM_GER"),
                "Desinfetante Lavanda 5L", "Galão",
                10, new BigDecimal("32.00")));
        map.put("SABONETE", criarMaterial(s.get("LIM_GER"),
                "Sabonete Líquido 1L", "Frasco",
                20, new BigDecimal("12.40")));

        // --- EPI ---
        map.put("LUVA", criarMaterial(s.get("EPI_PRO"),
                "Luva de Procedimento (cx 100un)", "Caixa",
                8, new BigDecimal("45.00")));
        map.put("CAPACETE", criarMaterial(s.get("EPI_PRO"),
                "Capacete de Segurança Branco", "Unidade",
                5, new BigDecimal("38.90")));

        // --- Informática ---
        map.put("TONER", criarMaterial(s.get("INF_SUP"),
                "Toner Impressora HP CF283A", "Unidade",
                3, new BigDecimal("289.00")));

        return map;
    }

    /** Helper que delega ao service para que o SKU seja gerado pelo gerador oficial. */
    private Material criarMaterial(Subcategoria sub, String nome, String unidade,
                                   int estoqueMinimo, BigDecimal valor) {
        Material m = Material.builder()
                .nome(nome)
                .unidadeMedida(unidade)
                .estoqueMinimo(estoqueMinimo)
                .estoqueAtual(0)
                .valorUnitario(valor)
                .subcategoria(sub)
                .build();
        return materialService.salvar(m);
    }

    // ========================================================
    // ENTRADAS (RF13 + RN05) - alimentam o estoque inicial.
    // ========================================================
    private void criarEntradas(Map<String, Material> m) {
        registrarEntrada(m.get("ACIDO_FOSF"),  40, "Dental Speed",        "NF-22001", -45);
        registrarEntrada(m.get("ADESIVO_BOND"),25, "Dental Speed",        "NF-22001", -45);
        registrarEntrada(m.get("RESINA_Z350"), 60, "Dental Speed",        "NF-22001", -45);
        registrarEntrada(m.get("LIMA_K"),      20, "EndoBras",            "NF-3322",  -40);
        registrarEntrada(m.get("AGULHA_ENDO"), 30, "EndoBras",            "NF-3322",  -40);
        registrarEntrada(m.get("ROLETE_ALG"),  80, "Cremer",              "NF-9911",  -38);
        registrarEntrada(m.get("AGULHA_GENG"), 25, "Cremer",              "NF-9911",  -38);
        registrarEntrada(m.get("BROCA_1011"),  60, "KG Sorensen",         "NF-7755",  -35);
        registrarEntrada(m.get("BROCA_1012"),  50, "KG Sorensen",         "NF-7755",  -35);
        registrarEntrada(m.get("CANETA_ALTA"),  6, "Dabi Atlante",        "NF-1100",  -50);

        registrarEntrada(m.get("PAPEL_A4"),    100, "Kalunga S/A",          "NF-12001", -45);
        registrarEntrada(m.get("CANETA"),      200, "Kalunga S/A",          "NF-12001", -45);
        registrarEntrada(m.get("DESINFETANTE"), 30, "Distribuidora ClearMix","NF-7720", -40);
        registrarEntrada(m.get("SABONETE"),     50, "Distribuidora ClearMix","NF-7720", -40);
        registrarEntrada(m.get("LUVA"),         25, "Protege EPI Ltda",     "NF-3344", -35);
        registrarEntrada(m.get("CAPACETE"),     12, "Protege EPI Ltda",     "NF-3344", -35);
        registrarEntrada(m.get("TONER"),         8, "InfoSupri Comércio",   "NF-9981", -30);
    }

    private void registrarEntrada(Material mat, int qtd, String fornecedor, String nf, int diasAtras) {
        Movimentacao mov = Movimentacao.builder()
                .material(mat)
                .quantidade(qtd)
                .fornecedor(fornecedor)
                .notaFiscal(nf)
                .data(LocalDateTime.now().plusDays(diasAtras))
                .build();
        movimentacaoService.registrarEntrada(mov);
    }

    // ========================================================
    // SAÍDAS APROVADAS (RF06) - alimentam o relatório RF10.
    // ========================================================
    private void criarSaidasAprovadas(Map<String, Material> m, Map<String, Setor> s) {

        // Clínica Odontológica - principal consumidora dos itens odonto
        gerarSaida(m.get("ACIDO_FOSF"),  15, s.get("CLIN"), "Dra. Ana Beatriz",  -20);
        gerarSaida(m.get("ADESIVO_BOND"), 8, s.get("CLIN"), "Dra. Ana Beatriz",  -18);
        gerarSaida(m.get("RESINA_Z350"), 25, s.get("CLIN"), "Dra. Ana Beatriz",  -15);
        gerarSaida(m.get("LIMA_K"),      10, s.get("CLIN"), "Dra. Ana Beatriz",  -14);
        gerarSaida(m.get("ROLETE_ALG"),  35, s.get("CLIN"), "Equipe Clínica",    -10);
        gerarSaida(m.get("AGULHA_GENG"), 14, s.get("CLIN"), "Dra. Ana Beatriz",   -8);
        gerarSaida(m.get("BROCA_1011"),  20, s.get("CLIN"), "Dra. Ana Beatriz",   -7);

        // RH consome papelaria
        gerarSaida(m.get("PAPEL_A4"), 25, s.get("RH"),    "Patrícia Lemos",   -20);
        gerarSaida(m.get("PAPEL_A4"), 30, s.get("RH"),    "Ana Beatriz",      -10);
        gerarSaida(m.get("CANETA"),   60, s.get("RH"),    "Patrícia Lemos",   -18);

        // Diretoria - papelaria moderada e toner
        gerarSaida(m.get("PAPEL_A4"),  8, s.get("DIR"),   "Marcos Antunes",   -15);
        gerarSaida(m.get("TONER"),     2, s.get("DIR"),   "Marcos Antunes",    -8);

        // TI - toner pesado (deixa abaixo do mínimo)
        gerarSaida(m.get("TONER"),     4, s.get("TI"),    "Rafael Souza",     -12);
        gerarSaida(m.get("CANETA"),   30, s.get("TI"),    "Rafael Souza",      -7);

        // Manutenção - EPIs (deixa LUVA abaixo do mínimo)
        gerarSaida(m.get("LUVA"),     18, s.get("MANUT"), "Carlos Pereira",   -25);
        gerarSaida(m.get("CAPACETE"),  4, s.get("MANUT"), "Carlos Pereira",   -20);
        gerarSaida(m.get("CAPACETE"),  3, s.get("MANUT"), "José Antônio",      -5);

        // Financeiro - papelaria leve
        gerarSaida(m.get("PAPEL_A4"),  5, s.get("FIN"),   "Juliana Martins",   -3);
        gerarSaida(m.get("CANETA"),   15, s.get("FIN"),   "Juliana Martins",   -3);

        // Limpeza distribuída
        gerarSaida(m.get("DESINFETANTE"),  6, s.get("MANUT"), "Carlos Pereira", -14);
        gerarSaida(m.get("DESINFETANTE"),  4, s.get("RH"),    "Equipe Limpeza", -10);
        gerarSaida(m.get("SABONETE"),     12, s.get("MANUT"), "Carlos Pereira",  -9);
        gerarSaida(m.get("SABONETE"),      8, s.get("DIR"),   "Equipe Limpeza",  -4);
    }

    private void gerarSaida(Material mat, int qtd, Setor setor, String quemRetirou, int diasAtras) {
        Movimentacao mov = Movimentacao.builder()
                .material(mat)
                .quantidade(qtd)
                .setorDestino(setor)
                .retiradoPor(quemRetirou)
                .data(LocalDateTime.now().plusDays(diasAtras))
                .build();
        Movimentacao salva = movimentacaoService.registrarSaida(mov);
        movimentacaoService.alterarStatus(salva.getId(), StatusMovimentacao.ENTREGUE);
    }
}
