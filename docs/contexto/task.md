# Estado operacional — Neves Estoque

> Atualizado em 19/10/2026. **CHECKPOINT 257 — [publicações Web/Android e hotfix de login](checkpoints/2026-10-19-cp257-web-android-publicacao.md)**. Histórico anterior: CP256 (07/10/2026) documentado no Contexto Mestre/versões Git; não substituir teste real por aprovação de CI.

## Fase atual

**IMPLEMENTADO / CI APROVADA / ANDROID RELEASE PUBLICADA / WEB PROD PUBLICADA / LOGIN WEB CONFIRMADO / TESTE OPERACIONAL DOS NOVOS FLUXOS A VERIFICAR.**

O responsável autorizou explicitamente publicar sem teste manual imediato e confirmou que **o login Web voltou a funcionar** depois do hotfix de configuração Supabase.

## Fontes de código, versões e ambientes

- Repositório: `BabyCatBE/neves-estoque`; branch integrada `develop` no último **HEAD funcional publicado** `d0b490b138faa0fad0321e431745560622895f50` (PR #89). Commits de documentação deste CP avançam HEAD mas **não alteram runtime**.
- `main`: `e0c15eb45fda2f6c68f58ed6f4ccdd24feb20635` (**não atualizada**). No Cloudflare a publicação Web saiu de `develop`; nunca presumir que `main` corresponda ao site publicado.
- Web/PWA `package.json`: **0.26.1**; hospedagem oficial **https://neves-estoque.pages.dev** (Cloudflare Pages), último deploy verificado `https://b94d1893.neves-estoque.pages.dev`. Netlify com builds pausados permanece fallback histórico.
- Android `0.37.0-alpha01` / **versionCode 37**; [Release `android-v0.37.0-alpha01`](https://github.com/BabyCatBE/neves-estoque/releases/tag/android-v0.37.0-alpha01), APK oficial `neves-estoque-android.apk` e `SHA256SUMS`. Atualização pelo próprio app, **instalação de 0.37 em aparelho ainda não confirmada**.
- Android `SYSTEM_VERSION = 0.26.0` × Web `0.26.1`: **A VERIFICAR**. Barra inferior Android: **A VERIFICAR**; preservar navegação.

## Entrega funcional da rodada de 19/10

- **Conferências Web e Android:** rascunhos incompletos locais, `Guardar e sair`, retomar categoria, preservação de quantidades na rotação Android, pular item em branco com `Próximo` sem transformá-lo em zero; salvamento/verificações em segundo plano; correção histórica atualiza ID original. Rascunho não altera estoque e não é pendência de envio. Salvar definitivo continua exigindo todos os produtos preenchidos; envio posterior offline permanece **manual/consciente**.
- **Entradas Web e Android:** aceitar preço unitário ou total do item da nota e calcular unitário; **exibir 2 casas**, manter maior precisão interna; preço em branco `NULL` distinto de zero/bonificação. Enter/Tab Web e teclado Neves Android preservados.
- **Impressão:** correção de paginação A4 de categorias grandes.
- Origem: PRs integradas #81, #82, #83 e #85; preparação/release #86, #87; deploy Cloudflare #88; hotfix #89.

## Verificações reais

- Android CI **#194**, run `39302190080`: **SUCCESS**. Publicação APK assinado run `39303909499`: **SUCCESS** (0.37.0-alpha01).
- Web CI **#251**, run `39316512271`: **SUCCESS**. Deploy Cloudflare run `39316512155`: **SUCCESS**. Logs confirmaram `VITE_SUPABASE_URL` e `VITE_SUPABASE_PUBLISHABLE_KEY` presentes no build; deployment concluído.
- Falhas relevantes do caminho: primeiro deploy Web falhou por falta de `CLOUDFLARE_API_TOKEN` GitHub; com token cadastrado, deploy saiu **sem variáveis públicas Supabase no build**, gerando tela de login bloqueada. PR #89 corrigiu as variáveis **antes** da compilação, falhando seguro quando faltarem; usuário confirmou login normal depois. Não publicar apenas porque `wrangler deploy` teve SUCCESS: verificar configuração do bundle.
- **Supabase:** projeto Neves Estoque `bwostqcdwfueapfemrwn` ACTIVE_HEALTHY; **43 migrations aplicadas**, última de 07/10. Verificado somente em leitura em 19/10. Sem novas migrations, alterações de schema ou dados reais durante essas publicações. **Não tratar contagens de dados do CP256 como estado atual.**
- **Testes funcionais do novo fluxo Android/Entradas/Conferências e Web PWA:** **PENDENTES**. Apenas login Web pós-hotfix foi confirmado nesta rodada pelo responsável. CP256/Android 0.36 continua último bloco anterior com teste de aparelho.

## Próximo passo

Esperar relato do próximo uso real e **verificar eventuais regressões**, sem exigir que o responsável faça teste agora. Preservar rascunhos/pendências locais, não limpar cache/dados PWA nem alterar dados reais para investigar. Se necessário, corrigir em branch, CI, checkpoint e obter autorização antes de publicar nova Release/Cloudflare.

Acompanhar depois: comportamento de PWA instalada/offline, atualização Android 0.37, edição pendências Web, pendências ao trocar usuário, fluxo duradouro de deploy/release (workflow 19/10 pontual), segurança/backup e `SYSTEM_VERSION`/barra inferior A VERIFICAR.

## Retomada por outra IA

1. Este `task.md` e [CP257 detalhado](checkpoints/2026-10-19-cp257-web-android-publicacao.md).
2. Contexto Mestre atual Google Docs, [índice do projeto BabyCat-OS](https://github.com/BabyCatBE/BabyCat-OS/tree/main/neves-os/empresas/panificadora-neves/panificadora-neves-nordestina/projetos/estoque) e documentação especializada pertinente.
3. Conferir `develop` real, GitHub Actions, Release e deployment oficial; Supabase real quando pertinente.
4. Preservar CP256/10-10 como **histórico datado**, separar HEAD funcional de commits documentais. Notion é resumo, não prova de CI/DB.
