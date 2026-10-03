# Almoxarifado FPTO — backend

Sistema de almoxarifado da Fundação Pró-Tocantins (Spring Boot 4, Thymeleaf, PostgreSQL/Flyway).
A interface web fica em `/` e a API REST consumida pelo app Android em `/api/**`.

## Rodando localmente

```bash
.\gradlew.bat bootRun      # perfil dev: H2 em memória + dados de demonstração
.\gradlew.bat test
```

Produção roda em container (`Dockerfile`), com PostgreSQL via variáveis de ambiente
(`DATABASE_URL` ou `DB_HOST`/`DB_NAME`/`DB_USER`/`DB_PASSWORD`) e migrations Flyway em
`src/main/resources/db/migration`.

## API REST (`/api/**`)

- Autenticação **HTTP Basic** com o mesmo usuário/senha da web; sessão stateless.
- Listagens paginadas devolvem `PageResponse` (`content`, `page`, `size`, `totalElements`,
  `totalPages`, `first`, `last`); parâmetros `page` (0-based), `size`, `sort=campo,direcao`.
- Datas `LocalDateTime` em ISO (`2026-10-03T10:15:30`), valores como número, enums pelo nome.
- Erros em `ErroResponse` (`timestamp`, `status`, `erro`, `mensagem`, `path`, `detalhes`):
  400 validação, 401 sem credencial, 403 sem perfil, 404 não encontrado,
  422 regra de negócio, 409 conflito de dados.

| Método | Rota | Perfil | Descrição |
|---|---|---|---|
| GET | `/api/me` | qualquer | Perfil do usuário autenticado: `{ login, nome, perfil }` |
| GET | `/api/setores` | qualquer | Setores para o dropdown |
| GET | `/api/materiais`, `/api/materiais/{id}`, `/api/materiais/sku/{sku}` | qualquer | Catálogo |
| GET | `/api/saidas`, `/api/saidas/{id}` | qualquer | Saídas (listagem/resumo) |
| POST | `/api/saidas` | qualquer | Saída por bipagem — nasce `ENTREGUE` com baixa imediata (header `Idempotency-Key` opcional) |
| GET | `/api/movimentacoes` | qualquer | Listagem paginada com filtros `tipo`, `status`, `materialId`, `setorId`, `inicio`, `fim` |
| GET | `/api/movimentacoes/{id}` | qualquer | Detalhe com `observacao`, auditoria (`criadoPor/Em`, `atualizadoPor/Em`) e `itens[]` |
| POST | `/api/movimentacoes/{id}/status` | **ADMIN** | Aprovar / entregar / rejeitar uma saída (ver regras abaixo) |
| GET | `/api/compras` | qualquer | Compras paginadas, filtro opcional `status` (`AGUARDANDO_COMPRA`, `COMPRA_REALIZADA`, `CANCELADA`) |
| GET | `/api/compras/{id}` | qualquer | Detalhe com `itens[]`, `anexos[]` (metadados) e dados do documento de origem |
| GET | `/api/relatorios/*` | qualquer | Relatórios JSON e exportações CSV/XLSX/PDF |
| GET | `/api/auditoria/**` | **ADMIN** | Trilha de auditoria |
| POST | `/api/importacao/materiais` | **ADMIN** | Importação de planilha |

### `POST /api/movimentacoes/{id}/status`

Corpo: `{ "status": "APROVADO" | "ENTREGUE" | "REJEITADO", "motivo": "texto (obrigatório ao rejeitar, 3–255)" }`.
Resposta 200: o detalhe atualizado (mesmo shape de `GET /api/movimentacoes/{id}`).

| De | Para permitido |
|---|---|
| `PENDENTE_APROVACAO` | `APROVADO`, `ENTREGUE`, `REJEITADO` |
| `APROVADO` | `ENTREGUE`, `REJEITADO` (devolve as quantidades ao estoque) |
| `ENTREGUE`, `REJEITADO` | nenhuma (422 com mensagem clara) |

Só movimentações do tipo `SAIDA` têm status alterável. O estoque é debitado na primeira passagem
`PENDENTE_APROVACAO → APROVADO/ENTREGUE`. A rejeição grava `observacao = "Rejeitada: <motivo>"`
preservando o texto anterior. As mesmas regras valem para a tela web.

### Exemplos

```bash
curl -u USUARIO:SENHA https://almoxarifado.fasaudefpto.com.br/api/me
curl -u USUARIO:SENHA "https://almoxarifado.fasaudefpto.com.br/api/compras?status=AGUARDANDO_COMPRA&size=5"
curl -u USUARIO:SENHA -X POST -H "Content-Type: application/json" \
     -d '{"status":"REJEITADO","motivo":"material vencido"}' \
     https://almoxarifado.fasaudefpto.com.br/api/movimentacoes/12/status
```

## Compras: perfis, decisão da Diretoria e Patrimônio

- **Perfil `COMPRAS`** (Compras/Almoxarifado): `delva.maria` (chefe), `sarah.luz` e `daisy.dias` (auxiliares),
  criados no primeiro boot com a senha de `COMPRAS_SENHA_INICIAL`. Só este perfil (e ADMIN) cadastra
  pré-compras, registra a decisão da Diretoria, recebe/baixa e envia bens ao Patrimônio; `PADRAO` consulta.
- **Decisão da Diretoria**: toda pré-compra nasce "Aguardando diretoria". Em *Compras → detalhes* o setor
  registra **Diretoria autorizou** ou **Não autorizou** (com parecer; cancela a pré-compra). A baixa só é
  liberada quando autorizada. Também pela API: `POST /api/compras/{id}/autorizacao`.
- **Compra PATRIMONIAL**: bens permanentes, itens descritos livremente, sem movimentar estoque. No
  recebimento informa-se NF, **quem retirou** e **setor de destino**; o recebimento vai para a fila de
  envio ao **Gerenciador Patrimonial** (também disponível para compras DIRETAS via "será patrimoniado").
  Envio automático com reenvio periódico; status e botão "Reenviar agora" nos detalhes da compra.
  Contrato e o que implementar no Patrimônio: [docs/INTEGRACAO_PATRIMONIO.md](docs/INTEGRACAO_PATRIMONIO.md).
- Variáveis: `PATRIMONIO_URL`, `PATRIMONIO_USUARIO`, `PATRIMONIO_SENHA`, `PATRIMONIO_REENVIO_MS`, `COMPRAS_SENHA_INICIAL`.

## Compras: leitura automática da Parte/Ofício

Em **Nova pré-compra** o colaborador pode anexar o PDF da Parte do setor ou do Ofício da unidade.
O sistema lê o texto (PDFBox), preenche o formulário (tipo, setor, fornecedor, valor, nº da Parte,
SGD, data, assunto, solicitante e itens casados com o catálogo) e o PDF vira o anexo da compra.
Para PDFs escaneados existe leitura assistida por IA, opcional: `COMPRAS_IA_HABILITADA=true` e
`ANTHROPIC_API_KEY` no ambiente.
