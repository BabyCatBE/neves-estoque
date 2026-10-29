# CP267 — Hotfix do selo de versão da Web 0.26.4

**Data:** 29/10/2026 (America/Bahia)  
**Estado:** IMPLEMENTADO / CI APROVADA / WEB PROD PUBLICADA / TESTE VISUAL EM BRAVE A VERIFICAR.  
**Escopo:** somente UI/versão Web; Android, banco, dados e arquitetura preservados.

## Diagnóstico
Após CP266, o operador fotografou o endereço oficial `neves-estoque.pages.dev` no Brave exibindo o selo **DEV · v0.26.1**, embora `package.json` já declarasse `0.26.3` e o novo deployment de 29/10 estivesse confirmado.

Causa comprovada no GitHub: `src/shared/config/appVersion.ts` tinha `APP_VERSION = "0.26.1"` fixo e a Home imprimia `DEV` independentemente do build. O selo visual não comprovava cache antigo, nem a ausência da funcionalidade CP266. **Cache/PWA do navegador continua A VERIFICAR** separadamente.

## Correção
- PR [#97](https://github.com/BabyCatBE/neves-estoque/pull/97), branch `fix/web-visible-version-20261029` integrada à `develop`.
- Merge de código `76c03501aefc247019a54fed195cc2e6a61b59e4`.
- `src/shared/config/appVersion.ts`: `APP_VERSION` obtida de `package.json`; selo de ambiente via `import.meta.env.PROD`, `PROD` no build oficial e `DEV` no desenvolvimento.
- `HomePage.tsx`: usa o selo de ambiente e versão compartilhados; testes adicionados em `src/shared/config/appVersion.test.ts`.
- `package.json` e `package-lock.json` avançados de `0.26.3` para **`0.26.4`**.
- Nenhuma alteração de Android, migrations, Supabase/RLS, estoque, cadastros ou dado real. `main` preservada em `e0c15eb45fda2f6c68f58ed6f4ccdd24feb20635`.
- A funcionalidade CP266 de **+ Lançar outra entrada** e `Ctrl+Shift+Enter` segue presente; nada foi revertido.

## CI e publicação
- CI de PR: [40952280725](https://github.com/BabyCatBE/neves-estoque/actions/runs/40952280725) SUCCESS.
- CI do merge aprovado `76c035...`: [40952435010](https://github.com/BabyCatBE/neves-estoque/actions/runs/40952435010) SUCCESS.
- Workflow de publicação controlada `.github/workflows/publish-approved-web-20261029-version-0264.yml`; commit `6715de1504f52b9b08858c06f4b29c9516f093cc` em `develop`. O workflow confere diferença de código Web contra o merge aprovado, CI e parâmetros públicos de build no Cloudflare antes do deploy.
- CI do commit publicador: [40952606368](https://github.com/BabyCatBE/neves-estoque/actions/runs/40952606368) SUCCESS.
- Publicador: [40952606514](https://github.com/BabyCatBE/neves-estoque/actions/runs/40952606514) SUCCESS.
- Cloudflare Pages oficial: `neves-estoque`, production `develop`. Deployment **`47c6a1a4-ecea-4040-ab4a-4ffa0ed86d52`**, URL imutável `https://47c6a1a4.neves-estoque.pages.dev`. Cloudflare API confirmou environment=production, stage deploy=success, commit `6715de...`, criado em 29/10 20:51 UTC.
- Domínio oficial: https://neves-estoque.pages.dev. O servidor confirmou o deploy; a sessão não incluiu teste visual em navegador autenticado.
- Deployment anterior para eventual rollback: `700bed58-6125-4fa3-89aa-9cdc8e2ce868` do CP266, anterior ainda `6136621e-0d2b-4538-af2c-40a7804fcf36` do CP265. Não executar rollback sem decisão.

## Próxima ação e limites
1. Abrir/atualizar no Brave a Web oficial e conferir selo **PROD · v0.26.4**. Se necessário, atualização forçada `Ctrl+Shift+R`. **Não limpar dados do site ou armazenamento offline** sem verificar pendências locais.
2. Com **nota real**, conferir `Ctrl+Enter`, resumo de Entrada salva, botão **+ Lançar outra entrada** e atalho `Ctrl+Shift+Enter`, sem duplicidade.
3. Teste em navegador autenticado e PWA/cache ainda **A VERIFICAR**, sem marcar TESTADO E2E.
4. CP265 segue como última consolidação geral (Android, dados e demais módulos); CP266 e CP267 registram entregas Web posteriores. Contexto Mestre Drive/Notion/BabyCat-OS **não consolidados** nesta rodada, conforme regra do projeto: somente quando pedido.

**Modelo:** GPT-6; esforço não informado. **Status da Web:** PROD PUBLICADA e CI APROVADA; teste real pendente. **Dados reais alterados:** nenhum.
