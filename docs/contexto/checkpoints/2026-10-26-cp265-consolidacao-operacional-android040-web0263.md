# CHECKPOINT 265 — Consolidação de continuidade após Web 0.26.3 e Android 0.40

**Data:** 26/10/2026 (fuso America/Bahia).
**Solicitação:** Elias pediu explicitamente “Consolide o projeto” antes de apagar este chat.
**Estado:** CONSOLIDADO NAS FONTES PERMANENTES / IMPLEMENTADO / CI APROVADA / WEB PROD PUBLICADA / ANDROID RELEASE PUBLICADA / TESTE REAL EM APARELHO PARCIAL / PEDIDO DA CONFERÊNCIA A VERIFICAR.

## 1. Último estado seguro e autoridades

**Código canônico**: `BabyCatBE/neves-estoque`, `develop`.
- Android última funcionalidade implementada: PR #95 MERGED, merge SHA completo `d8ce748cce30f71f251e5bb8236423e78ccbdd46`; origem CI do PR `34951d66799ae2c34162853b491f3e148c7e5178`, árvore Git igual ao commit sintético CI `6df90d25636b9432bc0e8df36fffef4828cf9e32`. Android 0.40 publicado e parcialmente testado pelo usuário.
- Web última funcionalidade publicada: PR #93 MERGED, merge SHA `0450b1135efa8b77f6c402019840c603f129b796`, origem `525fc807475fa0bf54d42c8ccd0d5ca6cc82ce6f`; publicada no Cloudflare Pages `develop`.
- PR #92 criou referência de estoque e seleção para pedidos (Web + Android); #93 corrigiu a apresentação como carrinho arredondado; #95 corrigiu pendência com catálogo desatualizado e ripple Android.
- `develop` HEAD **documental após atualizações de `task/prd/architecture/design/memory` e antes da criação deste checkpoint**: `c4ec92f1b534a8a9dd1e8192f8ac4392504e38a0`. O commit que cria este arquivo (visível no histórico Git) é **documental**, não atualização de aplicativo.
- `main` do app confirmada sem movimentação: `e0c15eb45fda2f6c68f58ed6f4ccdd24feb20635`.
- A última evidência de teste real relatado antes da consolidação está em [CP264](2026-10-26-cp264-retorno-teste-em-aparelho-android-040.md); CP258–263 detalham implementações, publicações e falhas intermediárias. CP257 é estado anterior, não vigente.

**Web**:
- `package.json` **0.26.3**; [https://neves-estoque.pages.dev](https://neves-estoque.pages.dev).
- Projeto Cloudflare Pages `neves-estoque` / environment `production`, production branch `develop`; leitura real da API de Cloudflare em 26/10: deploy `6136621e-0d2b-4538-af2c-40a7804fcf36` / URL `https://6136621e.neves-estoque.pages.dev`, código publicado origem `b58c3ae2182b5a764f40881ca3800a0c8b2148bc`, estágio `deploy=success`.
- CI Web no código novo `40399328785 SUCCESS`; publicador `40400225641 SUCCESS`. Login Web pós-hotfix do CP257 confirmado naquela data, não extrapolar para o fluxo novo da lista.

**Android**:
- Release oficial [`android-v0.40.0-alpha01`](https://github.com/BabyCatBE/neves-estoque/releases/tag/android-v0.40.0-alpha01), `versionCode=40`, aplicação `com.babycatbe.nevesestoque`, asset `neves-estoque-android.apk` + `SHA256SUMS`.
- GitHub `releases/latest` confirmado `android-v0.40.0-alpha01`, não draft/não prerelease, publicada em 26/10 às 22:17:31Z. Tag e branch `release/android-0.40.0-alpha01-approved-source` apontam à fonte aprovada `34951d66799ae2c34162853b491f3e148c7e5178`.
- CI Android `40408834376 SUCCESS` (lint, testes e APK assinado), publicador `40411239982 SUCCESS`; publicação aproveitou o **APK original da CI**, verificando SHA-256 e certificado permanente, sem recompilar.
- Última Release 0.39 anterior permanece preservada no histórico como eventual rollback, mas o estado operacional mais recente publicado é 0.40. `SYSTEM_VERSION` Android × Web/package e barra inferior Android seguem A VERIFICAR.

## 2. Funcionalidade e validação REAL

**Entregue**:
- Em Conferência por Categoria, Estoque de referência discreto ao lado da unidade.
- Carrinho arredondado no topo direito do card, sem nome/checkbox; estado selecionado e ripple de mesmo contorno Android corrigidos na 0.40.
- Seleção local por usuário/dispositivo, separada dos números da Conferência, rascunhos e pendências; Web IndexedDB/Dexie, Android JSON privado. **Não sincroniza os marcados entre Android e Web**.
- Botão `Fazer pedido dos selecionados (N)` no **fim da lista de categorias** (só aparece com N>0). A tela é lista de Compras temporária, com quantidade de pedido independente e Copiar/Compartilhar. **Não abre automaticamente depois de salvar Conferência**, não gera Entrada nem altera estoque.
- Android 0.40 recupera mesma pendência de Conferência com catálogo de produtos anterior: consulta online os IDs ativos antes de confirmar/enviar, indica novos produtos, requer quantidade explicitamente informada (zero aceito somente se digitado), preserva quantidades originais, `localId`, idempotência, data e responsável; bloqueia ID excluído/movido, duplicado ou divergente sem envio automático.

**TESTADO em aparelho — relato de Elias**: correções de recuperação/edição da pendência, visual/ripple do carrinho e botão de seleção. **Não houve prova de auditoria do registro enviado ao Supabase** nem validação das quantidades no banco decorrentes daquele envio. Não transformar relato de teste em comprovação de escrita.

**A VERIFICAR**: caminho completo seleção → rodapé da lista de categorias → lista de pedidos → quantidade de compra → Copiar/Compartilhar e múltiplas categorias (Android e Web). O usuário já havia realizado a Conferência física do dia e inicialmente pretendia testar só na próxima; o código foi consultado e confirmado que é possível testar sem salvar outra Conferência: marcar itens, voltar sem salvar e abrir o botão ao final da lista. Caso retome depois, não solicitar reprodução de contagem existente.

**Web**: CI e deploy aprovados; teste manual autenticado específico da nova seleção/pedido continua A VERIFICAR. Paridade da **recuperação de catálogo desatualizado em pendência Web** foi apenas sinalizada para avaliação; não afirmar implementada.

## 3. Supabase — fotografia real

Projeto correto `bwostqcdwfueapfemrwn` — Neves Estoque. Consulta real de `list_migrations` em 26/10 confirmou **43 migrations aplicadas**, última `20261007111815_editable_initial_price_before_priced_entry`. Consulta SELECT real:
- Categorias ativas: **13**
- Produtos ativos: **152**
- Entradas ativas: **18**
- Conferências ativas: **69**

Números são uma fotografia datada, não classificação de cada registro como real/teste. **Há dados ativos; não executar reset/limpeza/migração sem aprovação explícita.** Nenhuma migration foi criada ou aplicada em CP258–CP265, e esta consolidação **não escreveu no banco**.

Falha investigada: categoria MANTEIGAS, MARGARINAS E REQUEIJOES passou a 11 produtos ativos após cadastro de `Delicia 250g` em 21/10, enquanto a pendência tinha 10. Regra no banco exige conjunto atual. CP262 detalha causa e proteção no Android 0.40.

## 4. Falhas, testes e histórico

- Web CP258/CP261: falhas intermediárias por tipagem opcional e validação de commit de deploy, corrigidas antes da CI/Cloudflare verdes.
- Android CP259/CP260: CI intermediária usando versionCode antigo, bug de workflow e resposta GitHub 403 para criação direta de Release; solução controlada de branch aprovada + reutilização do APK assinado sem mudar `main`.
- Android CP262: execuções de CI intermediárias falharam por expectativa textual de fuso na suíte Kotlin e depois quebra de linha literal; corrigidas antes de `40408834376 SUCCESS`.
- CI aprovada **não é teste funcional E2E** nem demonstra que PWA/offline/Web autenticada passaram. Última CI geral após commits documentais antes deste arquivo: `40425597339 SUCCESS` no HEAD `c4ec92f...`.
- Não houve novo deploy Web nem Release Android durante **a consolidação documental**.

## 5. Fontes permanentes atualizadas nesta consolidação

1. Google Drive: [Contexto Mestre OPERACIONAL](https://docs.google.com/document/d/1MpX2xXuwjifR4Qj4sHnVE44L8X_zhSrAEEoNJAmsxis/edit) — seção 0 passou a CP265, introdução atualizada, texto antigo CP257/CP256 preservado e identificado como histórico, estilo de títulos saneado.
2. Google Drive: [Índice documental](https://docs.google.com/document/d/1uBhI9QGFd79Z4ROZVzh5oSAcFrKiE5yeLYqDq076lfg/edit) — ponto de entrada e autoridade CP265 atualizados, rota histórica preservada.
3. GitHub técnico `docs/contexto/`: `task.md`, `prd.md`, `architecture.md`, `design.md`, `memory.md` atualizados para versões/fluxos/testes. `rules.md` **mantido sem alteração**: seus guardrails gerais de não envio automático, zero vs. branco, validação backend e proteção de dados já permanecem; detalhe de comparação por ID e zero explícito da 0.40 consta em `task.md` e `memory.md`.
4. BabyCat OS: em `BabyCatBE/BabyCat-OS` `main`, documentação empresarial `STATUS.md`, `PENDENCIAS.md`, `CONTEXTO.md` e `FONTES.md` sincronizada ao CP265 (último HEAD documental da pasta antes deste arquivo `9e06a199cfbd52beba42176c4a8207bb9b0e7edc`). Corrigida divergência: Contexto Mestre Google Drive é autoridade para continuidade geral; BabyCat OS é mapa/status empresarial, não substitui código/banco.
5. Notion: [painel Neves Estoque](https://app.notion.com/p/3e26aebea386816dacbce7dd2438319f) com seção CP265 no topo; antigo callout CP257 marcado como histórico. Editado e lido novamente.
6. O Contexto Mestre **histórico de ~800 mil caracteres** e materiais legados foram mantidos intactos; não recriar documento monstruoso. Fotos originais do chat não foram copiadas como binários para a documentação; os fatos operacionais extraídos dos prints estão descritos nos CP262 e CP264.

## 6. Pendências e próximo passo

**Próximo passo prioritário:** no Android 0.40 já instalado, em Conferência → Fazer conferência, marcar alguns itens de 1–2 categorias, voltar **sem salvar nova Conferência física**, rolar ao fim da lista de categorias e abrir `Fazer pedido dos selecionados (N)`; validar quantidade de compra, Copiar e Compartilhar; depois repetir/observar Web 0.26.3. Se usuário já tiver marcado produtos, basta abrir o botão ao fim da lista. Registrar teste real / screenshot se apresentar falha.

Outras pendências: verificar envio efetivo de pendência antiga no banco se necessário; teste Web autenticado, PWA/offline, paridade do reparo de catálogo na Web, comportamento ao trocar usuário, `SYSTEM_VERSION`, barra inferior Android, backup/segurança e fluxo sustentável de deploy. Não enviar pendências automaticamente nem alterar PROD/Release/`main` sem nova instrução explícita.

### Critério de retomada sem chat

Outra IA consegue ler Contexto Mestre seção 0 + `docs/contexto/task.md` e este CP265, consultar HEAD/CI/Release Web/Android e dados/migrations reais, saber **o último estado publicado**, os **commits documentais posteriores**, os **testes reais limitados**, as **falhas intermediárias relevantes**, a **única pendência de teste prioritária** e as próximas ações — sem reler chats antigos.
