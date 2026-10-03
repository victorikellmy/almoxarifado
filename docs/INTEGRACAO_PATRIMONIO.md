# Integração Almoxarifado → Gerenciador Patrimonial

> Guia para implementar o lado do **Patrimônio** (`C:\Users\victorikellmy\IdeaProjects\Gerenciador_Patrimonial`,
> branch `main`, pacote `com.fundacao.gerenciador_patrimonial`). O lado do Almoxarifado já está pronto
> e envia os dados conforme abaixo.

## Fluxo de negócio

1. No Almoxarifado, o setor de Compras (Delva Maria, Sarah Luz, Daisy Dias — perfil `COMPRAS`) cadastra a
   pré-compra e registra se a **Diretoria autorizou**. Só compras autorizadas podem ser recebidas.
2. No recebimento, elas anexam a **nota fiscal** e informam **quem retirou** e o **setor de destino**.
   Compras do tipo **PATRIMONIAL** (e compras DIRETAS marcadas "será patrimoniado") não mexem em estoque:
   o recebimento é enviado ao Patrimônio.
3. O Patrimônio recebe uma **pendência de patrimoniamento**, mostra uma **notificação** ("X bens a
   patrimoniar") e, ao abrir a pendência, pré-preenche o cadastro do bem (descrição, data e valor de
   aquisição, NF, observação com setor/retirado por). O usuário completa (tombo, lotação, responsável,
   categoria...) e salva; a pendência fica CONCLUÍDA.

O Almoxarifado guarda uma fila (outbox) e reenvia automaticamente se o Patrimônio estiver fora do ar.
Reenvios do mesmo recebimento devem ser **idempotentes** (ver `compraId`).

## Endpoint a criar no Patrimônio

```
POST /api/integracao/almoxarifado/recebimentos
Authorization: Basic <usuario de integração>
Content-Type: application/json
```

Fica dentro da chain `/api/**` já existente (HTTP Basic, stateless). Sugestão: criar um usuário
`integracao.almoxarifado` com perfil `ADMINISTRADOR` (ou um perfil novo `INTEGRACAO` só com este
endpoint) e configurar no Almoxarifado `PATRIMONIO_USUARIO` / `PATRIMONIO_SENHA`.

### Corpo recebido (`RecebimentoPatrimonioPayload`)

```json
{
  "origem": "ALMOXARIFADO",
  "compraId": 42,
  "numeroDocumento": "001/2026",
  "numeroSgd": "2026/09039/000362",
  "assunto": "Solicitação para pagamento",
  "solicitanteDocumento": "Emerson Rodrigues Moura - MAJ QOPM - Resp. pelo Comando do 5º BPM",
  "fornecedor": "DAVID WELLYNGTON VAZ-ME",
  "numeroNotaFiscal": "00000002",
  "dataRecebimento": "2026-10-03T14:22:10",
  "dataCompra": "2026-10-03",
  "valorTotal": 320.00,
  "retiradoPor": "Sgt. João Silva",
  "setorDestino": "5º BPM - Núcleo de Saúde",
  "setorDestinoCentroCusto": "CC-05",
  "registradoPor": "sarah.luz",
  "observacao": "Reparo impressora Samsung · Parte/Ofício 001/2026 · Solicitação para pagamento",
  "itens": [
    { "descricao": "Impressora multifuncional Samsung", "codigoSku": null, "quantidade": 1, "valorUnitario": 320.00 }
  ],
  "anexos": [
    { "tipo": "SOLICITACAO", "nomeOriginal": "Oficio 001-2026.pdf", "contentType": "application/pdf", "tamanhoBytes": 61730, "conteudoBase64": null },
    { "tipo": "NOTA_FISCAL", "nomeOriginal": "NF 2.pdf", "contentType": "application/pdf", "tamanhoBytes": 20480, "conteudoBase64": "JVBERi0xLjQK..." }
  ]
}
```

- Só o anexo `NOTA_FISCAL` vem com `conteudoBase64` (PDF, até 10 MB). O da `SOLICITACAO` é só metadado.
- `compraId` é único por recebimento: **chave de idempotência**.
- Datas ISO-8601, valores numéricos.

### Respostas esperadas pelo Almoxarifado

| Situação | Status | Corpo |
|---|---|---|
| Pendência criada | `201` | `{ "id": 17, "status": "PENDENTE" }` |
| `compraId` já recebido antes | `409` **ou** `200` com a pendência existente | `{ "id": 17, "status": "PENDENTE" }` |
| Payload inválido | `400` | `ErroResponse` |
| Sem credencial / sem perfil | `401` / `403` | — |

O Almoxarifado grava o `id` devolvido (`idExterno`) e trata `409` como sucesso.

## Modelo sugerido no Patrimônio

```sql
-- V6__pendencia_patrimoniamento.sql
CREATE TABLE pendencia_patrimoniamento (
    id                   BIGSERIAL PRIMARY KEY,
    origem               VARCHAR(30)  NOT NULL,            -- ALMOXARIFADO
    compra_id_origem     BIGINT       NOT NULL,            -- compraId do almoxarifado
    status               VARCHAR(20)  NOT NULL DEFAULT 'PENDENTE', -- PENDENTE | CONCLUIDA | DESCARTADA
    numero_documento     VARCHAR(30),
    numero_sgd           VARCHAR(40),
    assunto              VARCHAR(255),
    solicitante_documento VARCHAR(255),
    fornecedor           VARCHAR(150),
    numero_nota_fiscal   VARCHAR(60),
    data_recebimento     TIMESTAMP,
    data_compra          DATE,
    valor_total          NUMERIC(19,2),
    retirado_por         VARCHAR(120),
    setor_destino        VARCHAR(150),
    setor_destino_cc     VARCHAR(30),
    registrado_por       VARCHAR(120),
    observacao           VARCHAR(1000),
    itens_json           TEXT,                              -- lista de itens como recebida
    nf_nome_original     VARCHAR(255),
    nf_caminho           VARCHAR(500),                      -- PDF salvo pelo StorageService
    patrimonio_id        BIGINT REFERENCES patrimonio(id),  -- preenchido ao concluir
    concluida_por        VARCHAR(80),
    concluida_em         TIMESTAMP,
    criado_em            TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT uk_pendencia_origem UNIQUE (origem, compra_id_origem)
);
CREATE INDEX idx_pendencia_status ON pendencia_patrimoniamento(status);
```

### Peças de código

1. `dto/request/RecebimentoAlmoxarifadoRequest` — espelho do JSON acima (records aninhados `Item`, `Anexo`).
2. `domain/entity/PendenciaPatrimoniamento` + `repository/PendenciaPatrimoniamentoRepository`
   (`findByOrigemAndCompraIdOrigem`, `countByStatus`, `findByStatusOrderByCriadoEmAsc`).
3. `service/IntegracaoAlmoxarifadoService.receber(request)`:
   - se já existe `(origem, compraId)` → devolve a existente (idempotente);
   - decodifica o `conteudoBase64` da NF e grava via `StorageService` (pasta `pendencias/{id}`);
   - salva a pendência com `status = PENDENTE`; audita (`AuditoriaService`).
4. `controller/IntegracaoAlmoxarifadoController` — `@RestController @RequestMapping("/api/integracao/almoxarifado")`,
   `@PostMapping("/recebimentos")` → `201` com `{id, status}`.
5. **Notificação**: em `DashboardService`/`DashboardMetrics` adicionar `pendenciasPatrimoniamento`
   (`countByStatus(PENDENTE)`) e no `fragments/layout.html` um sino/badge na navbar com o número,
   apontando para `/pendencias`.
6. Tela web `/pendencias` (lista PENDENTE/CONCLUÍDA) e `/pendencias/{id}/patrimoniar`, que abre o
   formulário existente de **Novo patrimônio** já preenchido:
   `descricao` = itens[0].descricao (ou concatenação), `dataCompra`, `valorCompra` = valorTotal
   (ou valorUnitario quando houver 1 item), `notaFiscal`, `observacao` = "Origem: Almoxarifado compra #42 ·
   Retirado por ... · Setor ... · Parte/Ofício ...". Ao salvar o `Patrimonio`, copiar o PDF da NF como
   `ArquivoAnexo` tipo `NOTA_FISCAL`, gravar `patrimonio_id`, `status = CONCLUIDA`, `concluida_por/em`.
   Se houver vários itens/quantidade > 1, permitir criar um bem por unidade (botão "Patrimoniar próximo")
   até zerar a quantidade, ou concluir manualmente.
7. Segurança: em `SecurityConfig.apiFilterChain` nada muda (anyRequest().authenticated()); se criar
   o perfil `INTEGRACAO`, restringir `/api/integracao/**` a `hasAnyRole("ADMINISTRADOR","INTEGRACAO")`
   e bloquear o restante da API para esse perfil. Na chain web, `/pendencias/**` → autenticado.

## Configuração no Almoxarifado (já implementada)

| Variável | Uso |
|---|---|
| `PATRIMONIO_URL` | ex.: `https://patrimonio.fasaudefpto.com.br` (sem barra final). Sem ela a integração fica em fila. |
| `PATRIMONIO_USUARIO` / `PATRIMONIO_SENHA` | credencial Basic do usuário de integração no Patrimônio |
| `PATRIMONIO_REENVIO_MS` | intervalo do reenvio automático dos pendentes (default 600000 = 10 min) |
| `COMPRAS_SENHA_INICIAL` | senha inicial dos usuários `delva.maria`, `sarah.luz`, `daisy.dias` (perfil COMPRAS). Padrão `Famsaudepm.`, trocada por elas em "Alterar senha" |

Tela de acompanhamento: **Compras → detalhes** mostra o status do envio (Aguardando envio / Enviado /
Falha), o nº da pendência no Patrimônio e um botão **Reenviar agora**.

## Teste ponta a ponta

```bash
# 1) Patrimônio: criar o usuário de integração e subir.
# 2) Almoxarifado: exportar PATRIMONIO_URL/USUARIO/SENHA e subir.
# 3) Como delva.maria: Nova pré-compra (tipo Patrimonial, item "Impressora ..."), registrar
#    "Diretoria autorizou", Receber/baixar com NF + quem retirou + setor.
# 4) Patrimônio: badge de pendência no topo; abrir e "Patrimoniar".
# Conferência manual do endpoint:
curl -u integracao.almoxarifado:SENHA -H "Content-Type: application/json" \
     -d @exemplo-recebimento.json https://patrimonio.../api/integracao/almoxarifado/recebimentos
```
