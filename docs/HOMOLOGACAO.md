# Homologação — Almoxarifado (web + app)

Ambiente de testes que roda **no seu PC**, com banco próprio em memória já
populado com dados que passam por todos os fluxos do sistema. Não toca na
produção.

> O banco **zera a cada reinício** e volta populado do zero. Pode testar à
> vontade: para recomeçar, é só parar e subir de novo.

---

## 1. Subir o servidor

Na pasta `almoxarifado`, branch `homologacao`:

```bash
git switch homologacao
./gradlew bootRun --args="--spring.profiles.active=homolog"
```

Pronto quando aparecer no log:

```
[Homologação] Pronto: 25 setores, 17 materiais (4 em alerta), 49 movimentações.
```

- **Web:** http://localhost:8081 (no próprio PC) ou `http://IP-DO-PC:8081` (em outra máquina da rede).
- Porta **8081** de propósito: o dev/IntelliJ costuma ocupar a 8080.
- Console do banco (só ADMIN): http://localhost:8081/h2-console — JDBC URL `jdbc:h2:mem:homolog`, usuário `sa`, senha vazia.

> **Firewall do Windows:** na primeira vez, o Windows pode perguntar se libera o
> Java na rede. Libere em **rede privada**, senão o celular não alcança o PC.

## 2. App de homologação no celular

Na pasta `app_almoxarifado`, branch `homologacao`, o app tem a versão **homolog**:

- Nome no celular: **Almox HML** — instala **ao lado** do app normal (não o substitui).
- Aponta para `http://192.168.1.134:8081/` (IP do PC no Wi-Fi em 15/10/2026).

Gerar e instalar:

```bash
./gradlew installHomolog
```

ou no Android Studio: *Build Variants* → `homolog` → Run. O APK fica em
`app/build/outputs/apk/homolog/app-homolog.apk`.

**O IP do PC mudou?** (o roteador pode trocar) Veja com `ipconfig` e gere de novo:

```bash
./gradlew installHomolog -PhomologUrl=http://NOVO-IP:8081/
```

O celular precisa estar **no mesmo Wi-Fi** que o PC.

## 3. Usuários de teste

Todos com a senha **`homolog123`** (só deste ambiente):

| Login | Perfil | Para testar |
|---|---|---|
| `admin` | Administrador | Tudo, inclusive aprovar/entregar/rejeitar saídas e cadastro de usuários |
| `delva.maria` | Compras (chefe) | Pré-compras, decisão da Diretoria, baixa de compras |
| `sarah.luz` / `daisy.dias` | Compras | Mesmo fluxo de Compras |
| `homolog.padrao` | Padrão | Uso operacional — **não** pode aprovar saídas (deve dar "sem permissão") |

## 4. O que já vem cadastrado

**Setores (25):** os 13 da FPTO e os 9 da FA-Saúde (iguais aos de produção) +
3 de demonstração (Diretoria, Operacional/Manutenção, Clínica Odontológica). Há "Financeiro" nas duas organizações — devem aparecer como
"Financeiro — FPTO" e "Financeiro — FA-Saúde".

**Materiais (17)** em Odontologia, Escritório, Limpeza, EPI e Informática —
**4 em alerta** de estoque mínimo (ex.: Toner, Luva de Procedimento).

**Saídas em todos os status:**

| Situação | Setor | Para testar |
|---|---|---|
| 3 × Pendente de aprovação | Enfermagem (FA), Financeiro (FA), Jurídico (FPTO) | Aprovar, Marcar entregue, Rejeitar |
| Aprovada | Recepção (FPTO) | Marcar entregue / Rejeitar (devolve estoque) |
| Rejeitada (com motivo) | Financeiro (FPTO) | Ver o motivo no detalhe |
| 2 × Entregues hoje (app) | Atendimento, Enfermagem (FA) | Indicador "Saídas hoje" |
| ~20 × Entregues antigas | Vários | Histórico e relatório de consumo |

**Compras em todas as etapas:**

| # | Tipo | Situação | Para testar |
|---|---|---|---|
| 1 | Estoque | Aguardando Diretoria | Registrar autorização / não autorização |
| 2 | Direta (Enfermagem) | Autorizada, aguardando baixa | Dar baixa com NF |
| 3 | Estoque | Realizada | Entrada no estoque (papel e canetas) |
| 4 | Direta (TI) | Realizada | Repasse ao setor, consumo |
| 5 | Patrimonial (Transporte) | Autorizada, aguardando baixa | Baixa → fila de envio ao Patrimônio |
| 6 | Direta (Coordenação) | Não autorizada → cancelada | Ver parecer da Diretoria |
| 7 | Estoque | Cancelada | Compra cancelada antes da decisão |

## 5. Roteiro de testes

### Web (http://localhost:8081)
- [ ] Login com cada usuário; `homolog.padrao` não vê Usuários/Auditoria.
- [ ] Dashboard: cartões e alertas de estoque mínimo.
- [ ] Setores: lista separada FPTO × FA-Saúde; cadastrar um sem responsável.
- [ ] Movimentações: aprovar a saída do Jurídico → estoque baixa; tentar voltar status de uma entregue → bloqueado.
- [ ] Nova saída pela web → nasce **pendente** (não baixa estoque).
- [ ] Compras: autorizar a #1; dar baixa na #2 com NF; tentar dar baixa na #1 antes de autorizar → bloqueado.
- [ ] Compra patrimonial #5: dar baixa (informar quem retirou) → aparece na fila do Patrimônio (integração desligada aqui).
- [ ] Relatórios: consumo por setor, mensal/anual, exportar Excel/PDF.

### App (Almox HML)
- [ ] Login com `homolog.padrao`: Início mostra nome e perfil **Padrão**.
- [ ] Sair do app e voltar: aparece o **bloqueio** (face/digital/PIN ou senha).
- [ ] Início: indicadores (3 pendentes, 4 em alerta, saídas de hoje, 17 materiais).
- [ ] Estoque: buscar "papel" e "ESC-"; filtro "Em alerta"; tocar num material.
- [ ] Nova saída: bipar/digitar `ESC-PAP-00001`, setor "Cadastro — FA-Saúde", finalizar → estoque baixa na hora.
- [ ] Tentar sair com quantidade maior que o estoque → bloqueado com mensagem.
- [ ] Movimentações → Pendentes: com `homolog.padrao` não há botões de aprovação; com `admin`, aprovar/rejeitar (rejeitar pede motivo).
- [ ] Compras: abrir a #5 (itens patrimoniais) e a #6 (parecer de não autorização em vermelho).
- [ ] Consumo por setor: trocar o período.
- [ ] Errar a senha do bloqueio 5 vezes → volta para o login.

## 6. Observações

- Integração com o Patrimônio **desligada** aqui (`patrimonio.url` vazio): os envios ficam na fila, como em produção quando o Patrimônio está fora.
- Log de erro de auditoria `No thread-bound request found` durante a carga é esperado: a carga roda fora de uma requisição web; a auditoria registra o aviso e segue.
- Perfil configurado em `src/main/resources/application-homolog.properties`; massa em `config/DataSeeder.java` + `config/HomologacaoSeeder.java`.
