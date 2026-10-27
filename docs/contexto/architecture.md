# Arquitetura — Neves Estoque

> Consolidada em 26/10/2026 — CP265. Referência operacional: `task.md`; snapshots de CP254/CP257 abaixo são históricos quando explicitamente datados.

## Visão geral

O repositório contém dois clientes que usam o mesmo backend:

- Web/PWA em React + TypeScript + Vite;
- Android nativo em Kotlin + Jetpack Compose;
- Supabase gerenciado + PostgreSQL;
- Cloudflare Pages para Web oficial desde 10/10/2026; Netlify anterior com builds pausados como retorno;
- GitHub Actions para CI.

Não há servidor de aplicação próprio no V1.

## Estrutura principal

```text
/
├─ .github/workflows/
├─ android/
│  └─ app/src/main/java/com/babycatbe/nevesestoque/
├─ docs/contexto/
├─ public/
├─ scripts/
├─ src/
│  ├─ app/
│  ├─ features/
│  ├─ shared/
│  └─ styles/
├─ supabase/migrations/
└─ tests/
```

## Web/PWA

Versão do pacote: **0.26.3** (Cloudflare Pages PROD).

Stack principal observada:
- React 19.3.0;
- TypeScript 5.9.3;
- Vite 8.3.0;
- React Router 7.18.3;
- TanStack Query 5.103.1;
- Supabase JS 2.116.0;
- Dexie 4.4.6 / IndexedDB;
- Tailwind CSS 4.3.3;
- React Hook Form + Zod;
- Vitest + Playwright.

O Offline Web usa IndexedDB/Dexie. Pendências locais não devem ser enviadas automaticamente ao reconectar. Conferências incompletas ficam na tabela `conferenceDrafts`, separada de `pendingOperations` e do cache.

## Android

Configuração atual observada em `android/app/build.gradle.kts`:
- applicationId `com.babycatbe.nevesestoque`;
- Kotlin + Jetpack Compose + Material 3;
- Java 17;
- compileSdk 37;
- targetSdk 36;
- minSdk 26;
- Android `0.40.0-alpha01`;
- versionCode 40;
- `SYSTEM_VERSION = 0.26.0`.

### Divergência de versão

Web/package está em `0.26.3`, enquanto `SYSTEM_VERSION` no Android historicamente era `0.26.0` (**verificar o código compilado antes de decidir alteração**).

Estado: **A VERIFICAR**.

Não alterar código somente para fazer a documentação coincidir.

### Navegação

O código atual usa `Navigation Compose` com Home como destino inicial autenticado.

A aplicação atual **não implementa barra inferior global**.

Há decisões históricas conflitantes sobre barra inferior. Estado: **A VERIFICAR**. Preservar a navegação atual até decisão explícita de Elias.

### Tema/Aparência

- tema centralizado;
- Claro/Escuro manual;
- preferência local ao aparelho;
- sem sincronização de tema no Supabase;
- tema escuro aprovado/testado no escopo registrado;
- datas visíveis `DD/MM/AAAA`, ISO internamente.

### Teclado numérico

Campos exclusivamente numéricos usam o componente numérico próprio do app quando aplicável.

No CHECKPOINT 254 o padrão foi expandido para os campos numéricos restantes do Android. Campos de texto, data e outros formatos continuam com a entrada apropriada.

### Offline Android

Arquivos internos privados separados em:
- `cache/`;
- `pending/`;
- `access/`.

Room/SQLite não faz parte da arquitetura vigente.

Regras:
- conferências incompletas em JSON atômico privado `conference-drafts/`, separado de cache e pendências enviáveis;
- cache é reconstruível;
- pendências são persistentes;
- reconectar não envia automaticamente;
- envio exige confirmação consciente;
- revalidação online e idempotência;
- limpeza de cache não apaga pendências.

## Supabase

Projeto operacional: **Neves Estoque**, região `sa-east-1`.

Fotografia **histórica** de 30/09/2026 (não usar como estado vigente):
- status ACTIVE_HEALTHY à época;
- 42 migrations aplicadas à época;
- 10 tabelas públicas principais com RLS ativa;
- 13 Categorias;
- 146 Produtos;
- 0 Fornecedores;
- 0 Entradas;
- 0 itens de Entrada;
- 0 Conferências;
- 0 itens de Conferência;
- 3 app_users;
- 17 devices.

A view `public.stock_current` fornece o estoque derivado.

Uma migration no Git não prova aplicação. O Supabase real é a autoridade operacional.

### Divergência histórica das primeiras migrations

Os três primeiros arquivos Git usam timestamps diferentes dos identificadores aplicados no Supabase, mas os nomes lógicos correspondem.

Regra: não renomear retroativamente migrations já aplicadas.

## Componentes confirmados — 26/10/2026 (CP265)

- **Web PROD 0.26.3:** `src/features/conferences/` mostra estoque de referência e botão de carrinho, `src/features/conferences/lib/conferencePurchaseSelection.ts` persiste seleção por usuário em IndexedDB/Dexie (schema de seleção separado dos rascunhos e pendências); rota `/conferencias/pedido` monta simulação local.
- **Android 0.40:** seleção local privada em `ConferencePurchaseSelectionStore` e diretório `conferencePurchasesDir`, separados das contagens/pendências; `ConferencePurchaseOrderRoute` é simulação de quantidade de compra independente. `PendingCatalogRepository` consulta o catálogo ativo online diretamente, compara IDs de pendência e permite completar registros com quantidade explicitamente digitada; `PendingStore.save` efetua escrita local atômica; `PendingSyncRepository` revalida antes do RPC.
- A UI Android do carrinho usa `Surface(onClick)` com `RoundedCornerShape` único para borda/fundo/ripple. Web e Android têm paridade da seleção/pedido, **não sincronização da seleção**.
- **Banco real somente lido em 26/10:** 43 migrations aplicadas, última `20261007111815_editable_initial_price_before_priced_entry`; 13 Categorias, 152 Produtos, 18 Entradas, 69 Conferências ativas. Não pressupor que sejam dados descartáveis; nenhum schema/migration alterado nos CP258–264.
- Cloudflare Pages `neves-estoque`/production `develop`, deploy confirmado `6136621e-0d2b-4538-af2c-40a7804fcf36` (Web 0.26.3); GitHub Android Release `android-v0.40.0-alpha01` é Latest e tem APK e SHA256SUMS. O workflow da Release reutiliza o artefato assinado aprovado na CI, sem recompilar. `main` segue preservada.
- A paridade Web do **reparo de pendências cujo catálogo mudou** ainda é A VERIFICAR (não declarar implementada); testes reais de pedido e PWA/offline permanecem pendentes.

## Autenticação e autorização

- Supabase Auth;
- Google para contas mestre;
- usuário/senha para contas secundárias no escopo implementado;
- `app_users` controla autorização interna;
- dados locais não concedem acesso;
- banco permanece autoridade para autorização de dados.

## Históricos e paginação

Web e Android possuem correções consolidadas para evitar truncamento silencioso de históricos:
- filtros no servidor;
- paginação por cursor/chave primária;
- blocos de IDs quando necessário;
- falha intermediária deve falhar a leitura inteira, não devolver resultado parcial silencioso.

## Cloudflare/Vite — falha e proteção consolidadas em 19/10/2026

Web oficial em https://neves-estoque.pages.dev, deployment via Wrangler a partir de `develop`. Publicação automática geral permanece desativada. `npm run build` no GitHub deve receber **antes da compilação** `VITE_SUPABASE_URL` e `VITE_SUPABASE_PUBLISHABLE_KEY`, ambas públicas e validadas; upload de `dist/` não injeta variáveis depois. PR #89 recupera os dois valores da configuração pública de produção do Cloudflare, valida-os, exige sua presença no bundle e bloqueia deploy ausente. Nunca usar chaves secretas/service_role no frontend ou na documentação.

Android 0.37.0-alpha01, versionCode 37, foi publicado via GitHub Release com APK da Android CI #194 já assinado e verificado, sem recompilar. Teste no aparelho desta versão permanece pendente. Fluxo de publicação de 19/10 é pontual, não automação de todas as versões.

## CI

### Web

CI pós-merge do CHECKPOINT 254:
- run 36761735944;
- CI #152;
- HEAD `c0e2ad41a8ad5d64db700453a28826eaf03b8594`;
- typecheck, lint, testes unitários e build: SUCCESS.

### Android

Android CI pós-merge:
- run 36761735901;
- Android CI #135;
- mesmo HEAD;
- lint/testes/build debug e release/verificações do APK: SUCCESS.

## Branches

- `develop`: integração e fonte da Web Cloudflare publicada nesta rodada;
- `main`: branch preservada, não é o runtime efetivamente publicado em Cloudflare nesta rodada;
- último HEAD de **código funcional publicado** CP257: `d0b490b138faa0fad0321e431745560622895f50` (histórico CP254 `c0e2ad41a8ad5d64db700453a28826eaf03b8594`);
- commits documentais posteriores podem avançar `develop` sem alterar o estado funcional: sempre distinguir os dois.

## Fontes de referência

- `src/app/`;
- `src/shared/`;
- `src/features/`;
- `android/app/src/main/java/com/babycatbe/nevesestoque/`;
- `supabase/migrations/`;
- `docs/contexto/`;
- Contexto Mestre oficial no Google Drive.

O histórico arquitetural completo permanece no arquivo histórico do Contexto Mestre e deve ser consultado seletivamente.
