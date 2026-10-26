# CHECKPOINT 258 — Referência de estoque e pedido por seleção na Conferência

**Data:** 26/10/2026
**Estado:** IMPLEMENTADO EM BRANCH / PR DRAFT / CI WEB APROVADA / ANDROID CI FINAL A VERIFICAR / TESTE EM APARELHO PENDENTE / NÃO PUBLICADO.

## Pedido e ordem de trabalho

Solicitação #91: durante Conferência física, mostrar referência discreta do estoque do sistema ao lado da unidade; marcar produtos de diferentes categorias e, ao terminar, abrir uma lista onde o operador informa quantidades de compra, copia e compartilha. Ordem exigida: **Android primeiro, Web depois**.

## Fontes e base segura

- Fonte mestre operacional: Contexto Mestre CP257 de 19/10/2026 (Google Drive).
- Repositório: `BabyCatBE/neves-estoque`.
- Branch de trabalho: `feat/conference-stock-purchase-selection-20261026`, originada de `develop@05a6983b61a0189c2e94ec0ceefb6212f94831f6` (documentação CP257).
- **HEAD funcional de trabalho verificado antes deste checkpoint:** `083bce7ece49710d8274fab5d1cae5ec62994a72`.
- PR draft: #92, https://github.com/BabyCatBE/neves-estoque/pull/92.
- `develop` não integrado por este bloco; `main` e site/Release oficiais não publicados.

## Android implementado primeiro

- `ConferenceModuleRepository`: referência lida de `stock_current.current_quantity`, com cache offline. Referência ausente = “sem referência”, nunca zero fictício; falha na leitura suplementar não bloqueia a contagem.
- `CategoryConferenceFormScreens`: `Unidade · Estoque` discreto e Checkbox “Pedir” em cada produto.
- `ConferencePurchaseSelectionStore`: armazenamento JSON local privado por usuário, com escrita atômica, fora de cache reconstruível e das pendências de Conferência, sem RPC ou banco.
- `ConferenceOverviewScreens`: botão **Fazer pedido dos selecionados (N)** ao fim das categorias.
- `ConferencePurchaseOrderRoute`: lista dos marcados com quantidade de compra independente, teclado numérico Neves, +/−, copiar, compartilhar, remover um produto, limpar tudo com confirmação e proteção contra perda de quantidades na rotação.
- A quantidade sistêmica guardada junto da marcação é identificada na tela de Compras como **REFERÊNCIA NA CONFERÊNCIA** (não saldo atual após salvar).

## Web implementado depois

- `getCategoryConferenceSetup` reaproveita o estoque derivado já consultado por Produtos; a referência é exibida ao lado da unidade.
- Checkbox “Pedir” em cada linha da Conferência por categoria e botão de compra na lista de categorias.
- `offlineDb` usa versão IndexedDB **5**, adicionando somente a tabela `conferencePurchaseSelections`; rascunhos, snapshots e pendências existentes preservados. Seleção indexada por usuário, **somente local ao navegador**, independente do Android, sem sincronização entre dispositivos.
- `/conferencias/pedido` reaproveita o `PurchaseListEditor` com itens pré-selecionados, quantidades digitáveis, copiar/compartilhar, remoção e limpeza explícita. Saldo guardado na marcação identificado como **Referência na contagem**.
- A seleção não transforma contagem em compra, nem gera Entrada ou pedido no banco.

## Versões, CI e testes

- Versões inalteradas: Web `package.json 0.26.1`; Android `0.37.0-alpha01` / versionCode `37`. Android `SYSTEM_VERSION` e barra inferior continuam A VERIFICAR como no CP257.
- Web CI `40386444282` **SUCCESS** para commit `083bce7`, após typecheck, lint, testes e build. Falhas intermediárias de CI por tipagem opcional de `currentQuantity` e atualização síncrona em efeito React foram corrigidas antes do verde; preservar esta trilha, não tratá-las como falhas atuais.
- Android CI inicial `40384531939` **SUCCESS** em commit anterior `06bfa82`; builds das mudanças posteriores ainda em andamento na última leitura, especialmente `40386444285` contra `083bce7`. **Não extrapolar o verde inicial para o HEAD final**.
- Workflow Purchases UI (Playwright) do HEAD `083bce7`: **EM EXECUÇÃO** na última leitura `40386444274`; resultados prévios incluem success `40385907679`.
- Sem testes manuais Web autenticado ou Android físico neste bloco; estados **A VERIFICAR**: multi-categorias, offline sem cache, rotação e retorno do pedido, clipboard/compartilhar, persistência entre relançamentos e troca de usuário.
- Modelo da IA: GPT-6; esforço não verificado.

## Dados, schema e publicação

- Nenhum dado operacional de produção lido/modificado, nenhuma RPC de gravação nova, **nenhuma migration PostgreSQL/Supabase criada ou aplicada**.
- Única evolução de estrutura local: tabela IndexedDB V5 Web; pasta separada Android. Seleções são lembretes de compra, não pedido persistido, nem histórico, nem vínculo Produto/Fornecedor.
- Não houve `main`, merge em `develop`, deploy Cloudflare, publicação APK/Release, instalação em aparelho nem alteração de versão.
- **ÚLTIMO ESTADO FUNCIONAL SEGURO PUBLICADO:** continua CP257 (19/10) até publicação e teste desta PR. Commits na branch de trabalho não substituem esse checkpoint.

## Próximo passo

1. Conferir CI Android e Purchases UI **do último SHA funcional**, corrigindo falhas objetivas sem mexer na base operacional.
2. Conservar PR draft até CI final; confirmar semanticamente Web/Android e testar no Galaxy + navegador autenticado, distinguindo CI de TESTADO.
3. Com autorização explícita do responsável, planejar integração/publicação separada, sem mudança automática de `main` ou ambiente operacional.
4. A consolidação em Contexto Mestre, BabyCat-OS e Notion aguarda solicitação expressa após verificação de checkpoint.
