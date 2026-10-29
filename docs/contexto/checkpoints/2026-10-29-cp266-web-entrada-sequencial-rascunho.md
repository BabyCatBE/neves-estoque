# CP266 — Web: lançamento de Entradas em sequência (rascunho)

**Data:** 29/10/2026  
**Estado:** EM IMPLEMENTAÇÃO (branch isolada) / TESTE MANUAL A VERIFICAR. **Não integrado e não publicado.**

## Continuidade e último estado seguro
- Contexto Mestre operacional e `docs/contexto/task.md` continuam apontando CP265 (26/10) como base consolidada.
- Web PROD permanece **0.26.3**, no Cloudflare Pages; Android Release **0.40.0-alpha01**. Nenhuma atualização dessas versões neste bloco.
- GitHub `BabyCatBE/neves-estoque`, integração `develop` observada no início em `2adcecbd2ae8820ed07e2c0de5c1aac27fefad3c`. `main` preservada. Branch do trabalho `feat/web-entradas-em-sequencia-20261029`, PR draft #96: https://github.com/BabyCatBE/neves-estoque/pull/96.
- HEAD de código antes deste checkpoint: `7b4880502f9908bee5ac3a15227ae4f737ce1c9c`. Conjunto de 4 commits: `a287ec9cc943e8b34b62ea08f3fb0335c298135e`, `6ed89ee5a2a0a741cb7768463911ef4be5265a4c`, `321c4ffb28678db05f6d6535c047715afd52a759`, `7b4880502f9908bee5ac3a15227ae4f737ce1c9c`.

## Necessidade real
No PC, o operador lança várias notas consecutivas. Hoje `Ctrl+Enter` salva uma Entrada e navega para o espelho `/entradas/:id?saved=1`. Para repetir é preciso voltar à área Entradas e abrir Nova Entrada.

## Mudança proposta/implementada na branch
- Em `EntryDetailPage.tsx`, apenas no retorno de novo salvamento (`saved=1`), mensagem de sucesso mantém o espelho e mostra **+ Lançar outra entrada**.
- Atalho `Ctrl+Shift+Enter` (ou `Cmd+Shift+Enter`) apenas nesse contexto, sem substituir `Ctrl+Enter` do formulário. Atalho desabilitado se confirmação de exclusão aberta.
- Ação encaminha para `/entradas/nova?sequence=1`, onde `NewEntryPage.tsx` coloca foco na busca de fornecedor. O formulário é recriado vazio, com nova chave de idempotência; nota anterior permanece no histórico e não é copiada.
- Teste unitário de reconhecimento do atalho (`entrySequenceShortcut.test.ts`) cobre Ctrl/Cmd, ausência de Shift, Alt e evento repetido.
- Escopo exclusivo da **Web/PWA**. Android não foi alterado. Não há migration, alteração de schema, RPC, banco, dados ou regra de Estoque.

## Validação
- **CI do código APROVADA:** PR #96 draft, run https://github.com/BabyCatBE/neves-estoque/actions/runs/40949939459, HEAD de código `7b4880502f9908bee5ac3a15227ae4f737ce1c9c`, conclusão **SUCCESS** (typecheck, lint, unit test e build do workflow). Commits documentais posteriores devem ter CI mais recente revalidada antes de integrar.
- Teste visual, atalho em navegador e fluxo real com notas do operador: **A VERIFICAR**. Não criar Entradas de teste em banco real.
- Não confundir commit de código com ambiente publicado, CI com teste funcional, ou PR draft com integração.

## Próximos passos
1. Confirmar CI mais recente da PR #96 e corrigir falhas se houver.
2. Verificar manualmente no navegador autenticado: `Ctrl+Enter` salva; resumo preservado; botão e `Ctrl+Shift+Enter` abrem formulário limpo focado no fornecedor; nenhuma nota duplicada; fluxo de edição não exibe a ação.
3. Com autorização expressa do responsável, integrar/publicar com fluxo protegido (sem promover `main` e sem deploy automático).
4. **Consolidar** Contexto Mestre, `docs/contexto/` e painel Notion **somente quando solicitado**, após validar o estado efetivo.

**Modelo:** GPT-6; esforço da sessão não disponível de forma confiável.  
**Impacto em dados reais:** nenhum.
