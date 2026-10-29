# CP266 — Lançamento sequencial de Entradas Web — publicado

**Data:** 29/10/2026 (America/Bahia)  
**Estado:** IMPLEMENTADO / CI APROVADA / WEB PROD PUBLICADA / TESTE MANUAL AUTENTICADO A VERIFICAR.  
**Consolidação geral Drive/Notion/BabyCat-OS:** PENDENTE DE PEDIDO, sem reclassificar CP265 como obsoleto para Android ou demais fluxos.

## Estado funcional seguro anterior
- CP265 (26/10/2026) é o último **checkpoint geral consolidado**: Web 0.26.3 PROD, Android 0.40.0-alpha01 oficial.
- `main` do aplicativo preservada. Android e demais funções não alterados neste bloco.
- Cloudflare Pages anterior: deployment `6136621e-0d2b-4538-af2c-40a7804fcf36` (26/10). Preservar como referência de eventual retorno.
- Início da implementação: `develop@2adcecbd2ae8820ed07e2c0de5c1aac27fefad3c`.

## Solicitação e decisão
O operador frequentemente lança diversas notas reais seguidas na Web/PC. Preservar `Ctrl+Enter` para Salvar Entrada e o espelho/recibo de Entrada salva, mas oferecer um caminho direto para abrir a próxima Entrada. Após autorização explícita em 29/10, integrar e publicar **somente a Web**.

## Código e escopo
- PR [#96](https://github.com/BabyCatBE/neves-estoque/pull/96) integrada a `develop`; **merge** `1b89dfaac792d152ecad083fc9dd9d00b8fa38c2` em 29/10/2026.
- Branch de origem: `feat/web-entradas-em-sequencia-20261029`; HEAD aprovado `6cd2c357cbbac01a955288d80d4607634e573876`.
- Arquivos de runtime: `src/features/entries/pages/EntryDetailPage.tsx`, `src/features/entries/pages/NewEntryPage.tsx`, `src/features/entries/lib/entrySequenceShortcut.ts`; teste: `entrySequenceShortcut.test.ts`.
- Após o salvamento online oficial, a página `/entradas/:id?saved=1` mantém o espelho e apresenta **+ Lançar outra entrada**; `Ctrl+Shift+Enter` no PC / `Cmd+Shift+Enter` no Mac executa a mesma navegação.
- Nova tela `/entradas/nova?sequence=1` inicia formulário novo e vazio com foco na pesquisa de Fornecedor. Novo ciclo recebe nova chave de idempotência; não reaproveita lista, preços ou fornecedor da Entrada anterior.
- Atalho só vale após criação (`saved=1`) e não é disparado com diálogo de exclusão aberto. Não afeta `Ctrl+Enter` no formulário, edição de Entrada ou a tela de pendências offline.
- **Sem mudanças de Android, código de banco, schema, migrations, RLS, RPCs ou dados reais.**
- `package.json` Web permanece **0.26.3** (não houve incremento do número da versão neste bloco); houve nova publicação de código nessa mesma versão.

## Verificação técnica e CI
- CI do PR de código: run [40949939459](https://github.com/BabyCatBE/neves-estoque/actions/runs/40949939459) SUCCESS.
- CI do HEAD completo da PR: run [40950160720](https://github.com/BabyCatBE/neves-estoque/actions/runs/40950160720) SUCCESS.
- CI do **merge integrado** `1b89df...`: run [40951239386](https://github.com/BabyCatBE/neves-estoque/actions/runs/40951239386) SUCCESS (typecheck/lint/unit/build).
- Commit do workflow explícito para deploy em `develop`: `1750c8acfbedf76d758956bed50af2d5ed841545`; CI desse commit: run [40951370939](https://github.com/BabyCatBE/neves-estoque/actions/runs/40951370939) SUCCESS.
- Workflow de publicação único: `.github/workflows/publish-approved-web-20261029-entry-sequence.yml`; restringe branch/projeto, verifica diff do código Web contra merge aprovado e verifica `head_sha` e conclusão da CI antes de build; conserva injeção verificada de parâmetros públicos Supabase sem exibir valores.
- [Publicação Cloudflare, run 40951370993](https://github.com/BabyCatBE/neves-estoque/actions/runs/40951370993): **SUCCESS**.
- Cloudflare Pages produção `neves-estoque`, branch `develop`, deployment **`700bed58-6125-4fa3-89aa-9cdc8e2ce868`**, URL `https://700bed58.neves-estoque.pages.dev`, criado 29/10/2026 20:42 UTC; API Cloudflare confirmou environment `production`, estágio deploy `success`, commit `1750c8...`.
- URL oficial `https://neves-estoque.pages.dev`. Publicação confirmada pelo provedor; teste da navegação **com usuário autenticado não realizado nesta sessão**. Não alegar E2E testado.

## Testes manuais pendentes
1. No PC autenticado, lançar **somente nota real efetivamente recebida**; `Ctrl+Enter` deve salvar uma vez e abrir o resumo sem perder dados.
2. Na faixa de sucesso, confirmar **+ Lançar outra entrada**; clicar e conferir novo formulário vazio, foco no campo de fornecedor, data apropriada, total zerado.
3. Na próxima Entrada real, repetir usando `Ctrl+Shift+Enter` no resumo, conferir o histórico sem duplicidades.
4. Conferir que `Ctrl+Enter` continua salvando, que edição de Entrada não exibe botão de sequência, e que caso offline/pêndencia não ganhou envio automático.
5. Não criar testes artificiais no Supabase operacional. PWA/cache e atalhos específicos do navegador A VERIFICAR.

## Limites, riscos e retorno
- O código Web foi publicado, mas **CI não substitui teste funcional** no navegador real.
- Banco não foi alterado nem examinado nesta rodada, porque o escopo foi UI pura.
- Caso haja regressão, referência de retorno no Cloudflare: deployment anterior `6136621e-0d2b-4538-af2c-40a7804fcf36`; não executar rollback sem necessidade/decisão.
- Outros itens A VERIFICAR do CP265 (Pedido desde Conferência, envio de pendências, paridade Web/Android, SYSTEM_VERSION, barra inferior) permanecem.

## Continuidade
1. Continuar a partir deste CP266 para o fluxo **Web Entradas em sequência**, sem confundi-lo com CP265 geral.
2. Próxima ação: teste real em navegador por responsável; registrar resultado separado.
3. Consolidação completa Contexto Mestre/Notion/BabyCat-OS somente quando o responsável solicitar.

**Modelo:** GPT-6. Esforço não disponível na sessão.  
**Último HEAD de runtime Web integrado:** `1b89dfaac792d152ecad083fc9dd9d00b8fa38c2`.  
**Último HEAD técnico de deploy antes desta documentação:** `1750c8acfbedf76d758956bed50af2d5ed841545`.
