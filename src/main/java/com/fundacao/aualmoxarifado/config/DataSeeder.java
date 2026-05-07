package com.fundacao.aualmoxarifado.config;

import com.fundacao.aualmoxarifado.domain.*;
import com.fundacao.aualmoxarifado.repository.*;
import com.fundacao.aualmoxarifado.service.MovimentacaoService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.CommandLineRunner;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Popula o banco com dados realistas para a apresentação do sistema.
 *
 * ====== COMO DESATIVAR ESTE SEEDER ======
 * Este componente está marcado com @Profile("dev"), portanto SÓ executa quando a
 * aplicação roda com o perfil "dev" ativo (-Dspring.profiles.active=dev ou
 * SPRING_PROFILES_ACTIVE=dev). Em produção (sem perfil ou com perfil "prod"),
 * o Spring nem sequer instancia esta classe.
 *
 * Outras formas de desativar antes do merge para master:
 *   1. Comentar a anotação @Component (mais drástico).
 *   2. Trocar @Profile("dev") por @Profile("never") para garantir que nunca rode.
 *   3. Mover esta classe para src/test (fora do classpath de produção).
 *   4. Adicionar a propriedade app.seeder.enabled=false e usar
 *      @ConditionalOnProperty(name = "app.seeder.enabled", havingValue = "true").
 */
@Slf4j
@Component
@Profile("dev")
@RequiredArgsConstructor
public class DataSeeder implements CommandLineRunner {

    private final CategoriaRepository categoriaRepository;
    private final MaterialRepository materialRepository;
    private final SetorRepository setorRepository;
    private final MovimentacaoRepository movimentacaoRepository;
    private final MovimentacaoService movimentacaoService;

    @Override
    public void run(String... args) {
        // Idempotência: se já houver categorias, assume que o seed já foi rodado.
        if (categoriaRepository.count() > 0) {
            log.info("[DataSeeder] Banco já populado - seed ignorado.");
            return;
        }

        log.info("[DataSeeder] Iniciando carga de dados de apresentação...");

        Map<String, Categoria> categorias = criarCategorias();
        Map<String, Setor> setores = criarSetores();
        Map<String, Material> materiais = criarMateriais(categorias);

        criarEntradas(materiais);
        criarSaidasAprovadas(materiais, setores);

        log.info("[DataSeeder] Carga concluída: {} categorias, {} setores, {} materiais, {} movimentações.",
                categoriaRepository.count(),
                setorRepository.count(),
                materialRepository.count(),
                movimentacaoRepository.count());
    }

    // ========================================================
    // CATEGORIAS (RF11)
    // ========================================================
    private Map<String, Categoria> criarCategorias() {
        Map<String, Categoria> map = new HashMap<>();
        map.put("PAPELARIA", categoriaRepository.save(Categoria.builder()
                .nome("Papelaria/Escritório")
                .descricao("Materiais de uso administrativo e expediente")
                .build()));
        map.put("LIMPEZA", categoriaRepository.save(Categoria.builder()
                .nome("Limpeza")
                .descricao("Produtos de higienização e conservação")
                .build()));
        map.put("EPI", categoriaRepository.save(Categoria.builder()
                .nome("Equipamento de Proteção Individual (EPI)")
                .descricao("Itens de segurança do trabalho")
                .build()));
        map.put("INFORMATICA", categoriaRepository.save(Categoria.builder()
                .nome("Informática")
                .descricao("Suprimentos e periféricos de TI")
                .build()));
        return map;
    }

    // ========================================================
    // SETORES (RF04)
    // ========================================================
    private Map<String, Setor> criarSetores() {
        Map<String, Setor> map = new HashMap<>();
        map.put("DIR", setorRepository.save(Setor.builder()
                .nome("Diretoria")
                .responsavel("Marcos Antunes")
                .codigoCentroCusto("CC-001")
                .build()));
        map.put("RH", setorRepository.save(Setor.builder()
                .nome("Recursos Humanos")
                .responsavel("Patrícia Lemos")
                .codigoCentroCusto("CC-010")
                .build()));
        map.put("TI", setorRepository.save(Setor.builder()
                .nome("Tecnologia da Informação")
                .responsavel("Rafael Souza")
                .codigoCentroCusto("CC-020")
                .build()));
        map.put("MANUT", setorRepository.save(Setor.builder()
                .nome("Operacional/Manutenção")
                .responsavel("Carlos Pereira")
                .codigoCentroCusto("CC-030")
                .build()));
        map.put("FIN", setorRepository.save(Setor.builder()
                .nome("Financeiro")
                .responsavel("Juliana Martins")
                .codigoCentroCusto("CC-040")
                .build()));
        return map;
    }

    // ========================================================
    // MATERIAIS (RF12) - estoque inicia em 0; subirá com as ENTRADAS
    // ========================================================
    private Map<String, Material> criarMateriais(Map<String, Categoria> cat) {
        Map<String, Material> map = new HashMap<>();

        map.put("PAPEL_A4", materialRepository.save(Material.builder()
                .nome("Resma de Papel A4 75g")
                .codigoSku("PAP-A4-001")
                .unidadeMedida("Pacote")
                .estoqueAtual(0)
                .estoqueMinimo(15)
                .valorUnitario(new BigDecimal("28.90"))
                .categoria(cat.get("PAPELARIA"))
                .build()));

        map.put("CANETA", materialRepository.save(Material.builder()
                .nome("Caneta Esferográfica Azul")
                .codigoSku("PAP-CAN-002")
                .unidadeMedida("Unidade")
                .estoqueAtual(0)
                .estoqueMinimo(40)
                .valorUnitario(new BigDecimal("2.50"))
                .categoria(cat.get("PAPELARIA"))
                .build()));

        map.put("DESINFETANTE", materialRepository.save(Material.builder()
                .nome("Desinfetante Lavanda 5L")
                .codigoSku("LIM-DES-001")
                .unidadeMedida("Galão")
                .estoqueAtual(0)
                .estoqueMinimo(10)
                .valorUnitario(new BigDecimal("32.00"))
                .categoria(cat.get("LIMPEZA"))
                .build()));

        map.put("SABONETE", materialRepository.save(Material.builder()
                .nome("Sabonete Líquido 1L")
                .codigoSku("LIM-SAB-002")
                .unidadeMedida("Frasco")
                .estoqueAtual(0)
                .estoqueMinimo(20)
                .valorUnitario(new BigDecimal("12.40"))
                .categoria(cat.get("LIMPEZA"))
                .build()));

        map.put("LUVA", materialRepository.save(Material.builder()
                .nome("Luva de Procedimento (cx 100un)")
                .codigoSku("EPI-LUV-001")
                .unidadeMedida("Caixa")
                .estoqueAtual(0)
                .estoqueMinimo(8)
                .valorUnitario(new BigDecimal("45.00"))
                .categoria(cat.get("EPI"))
                .build()));

        map.put("CAPACETE", materialRepository.save(Material.builder()
                .nome("Capacete de Segurança Branco")
                .codigoSku("EPI-CAP-002")
                .unidadeMedida("Unidade")
                .estoqueAtual(0)
                .estoqueMinimo(5)
                .valorUnitario(new BigDecimal("38.90"))
                .categoria(cat.get("EPI"))
                .build()));

        map.put("TONER", materialRepository.save(Material.builder()
                .nome("Toner Impressora HP CF283A")
                .codigoSku("INF-TON-001")
                .unidadeMedida("Unidade")
                .estoqueAtual(0)
                .estoqueMinimo(3)
                .valorUnitario(new BigDecimal("289.00"))
                .categoria(cat.get("INFORMATICA"))
                .build()));

        return map;
    }

    // ========================================================
    // ENTRADAS (RF13 + RN05) - alimentam o estoque inicial
    // ========================================================
    private void criarEntradas(Map<String, Material> m) {
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
    // SAÍDAS (RF06) - todas APROVADAS para alimentar o relatório RF10
    // Algumas saídas levam o estoque ABAIXO do mínimo (RN06)
    // ========================================================
    private void criarSaidasAprovadas(Map<String, Material> m, Map<String, Setor> s) {

        // RH consome MUITA papelaria
        gerarSaida(m.get("PAPEL_A4"), 25, s.get("RH"),    "Patrícia Lemos",   -20);
        gerarSaida(m.get("PAPEL_A4"), 30, s.get("RH"),    "Ana Beatriz",      -10);
        gerarSaida(m.get("CANETA"),   60, s.get("RH"),    "Patrícia Lemos",   -18);

        // Diretoria - papelaria moderada e toner
        gerarSaida(m.get("PAPEL_A4"),  8, s.get("DIR"),   "Marcos Antunes",   -15);
        gerarSaida(m.get("TONER"),     2, s.get("DIR"),   "Marcos Antunes",    -8);

        // TI - toner pesado (deixa abaixo do mínimo!)
        gerarSaida(m.get("TONER"),     4, s.get("TI"),    "Rafael Souza",     -12);
        gerarSaida(m.get("CANETA"),   30, s.get("TI"),    "Rafael Souza",      -7);

        // Manutenção - consome MUITOS EPIs (deixa LUVA abaixo do mínimo!)
        gerarSaida(m.get("LUVA"),     18, s.get("MANUT"), "Carlos Pereira",   -25);
        gerarSaida(m.get("CAPACETE"),  4, s.get("MANUT"), "Carlos Pereira",   -20);
        gerarSaida(m.get("CAPACETE"),  3, s.get("MANUT"), "José Antônio",      -5);

        // Financeiro - papelaria leve
        gerarSaida(m.get("PAPEL_A4"),  5, s.get("FIN"),   "Juliana Martins",   -3);
        gerarSaida(m.get("CANETA"),   15, s.get("FIN"),   "Juliana Martins",   -3);

        // Limpeza distribuída para todos os setores
        gerarSaida(m.get("DESINFETANTE"),  6, s.get("MANUT"), "Carlos Pereira", -14);
        gerarSaida(m.get("DESINFETANTE"),  4, s.get("RH"),    "Equipe Limpeza", -10);
        gerarSaida(m.get("SABONETE"),     12, s.get("MANUT"), "Carlos Pereira",  -9);
        gerarSaida(m.get("SABONETE"),      8, s.get("DIR"),   "Equipe Limpeza",  -4);
    }

    /**
     * Cria a saída e já transita para ENTREGUE para que:
     *   - O estoque do material seja decrementado (RN04);
     *   - A movimentação apareça no Relatório de Consumo por Setor (RF10).
     */
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
