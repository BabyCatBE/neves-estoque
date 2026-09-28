# Arquitetura — Neves Estoque

> Estado real verificado em 28/09/2026 na branch `fix/audit-device-id`. Baseline funcional anterior à implantação documental: `10ec3abfc76185fa75afdc47a3b1cdac8d676433`.

## Visão geral

O repositório contém dois clientes que usam o mesmo backend:

- Web/PWA em React + TypeScript + Vite;
- Android nativo em Kotlin + Jetpack Compose;
- Supabase gerenciado + PostgreSQL como backend e banco;
- Netlify para hospedagem Web;
- GitHub Actions para CI.

Não há servidor de aplicação próprio nem Edge Function ativa no estado atual.

## Estrutura real

```text
/
├─ .github/workflows/
├─ android/app/src/main/java/com/babycatbe/nevesestoque/
│  ├─ app/
│  ├─ data/offline/
│  ├─ feature/
│  │  ├─ alerts/
│  │  ├─ auth/
│  │  ├─ conferences/
│  │  ├─ entries/
│  │  ├─ home/
│  │  ├─ offline/
│  │  ├─ products/
│  │  ├─ purchases/
│  │  ├─ reports/
│  │  ├─ stock/
│  │  ├─ suppliers/
│  │  └─ trash/
│  └─ ui/theme/
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

Stack instalada no `package.json`:

- React 19.3.0;
- TypeScript 5.9.3;
- Vite 8.3.0;
- React Router 7.18.3;
- TanStack Query 5.103.1;
- Supabase JS 2.116.0;
- Dexie 4.4.6 sobre IndexedDB;
- Tailwind CSS 4.3.3;
- React Hook Form + Zod;
- vite-plugin-pwa;
- Vitest + Playwright.

Fluxo principal: `App` → providers → router → páginas por feature → módulos de acesso/lógica → Supabase.

O Offline Web usa IndexedDB/Dexie em `src/shared/offline/` e pendências em `src/features/offline/`.

## Android

Configuração atual observada em `android/app/build.gradle.kts`:

- applicationId `com.babycatbe.nevesestoque`;
- Kotlin + Jetpack Compose + Material 3;
- Navigation Compose 2.10.0;
- Supabase Kotlin 3.8.0;
- Ktor Android 3.5.1;
- Java 17;
- compileSdk 37, targetSdk 36, minSdk 26;
- Android `0.21.0-alpha01`, versionCode 21;
- versão de sistema `0.26.0`.

`MainActivity` inicializa o app e `AuthenticatedApp.kt` concentra o grafo de navegação autenticado.

### Offline Android

`OfflineStore.kt` usa arquivos internos privados separados em:

- `cache/`: cópias reconstruíveis;
- `pending/`: pendências locais;
- `access/`: registro mínimo de último acesso validado.

O sub-bloco 11.2 moveu I/O para execução assíncrona, reforçou escrita/substituição de arquivos, serializou operações concorrentes do mesmo arquivo e preservou pendências separadas do cache. Room/SQLite não faz parte da arquitetura atual.

## Banco Supabase

Projeto operacional: **Neves Estoque**, região `sa-east-1`.

Tabelas públicas verificadas:

- `app_users`;
- `devices`;
- `categories`;
- `products`;
- `suppliers`;
- `entries`;
- `entry_items`;
- `conferences`;
- `conference_items`;
- `audit_log`.

As 10 tabelas públicas estão com RLS ativa.

A view `public.stock_current` fornece a posição derivada do Estoque Atual. Os clientes não mantêm um saldo oficial independente.

As operações de Entrada, Conferência, Produtos/Categorias, Fornecedores, mescla, conversão de unidade, restauração e Lixeira reutilizam funções/RPCs já versionados no projeto.

Não há Edge Functions ativas.

## Migrations

No checkpoint 11.2 havia 42 migrations aplicadas no ambiente. A última era `20260927171450_trash_permanent_delete_v1`.

Existe uma divergência histórica conhecida nas três primeiras migrations: os timestamps dos arquivos Git são diferentes dos identificadores realmente aplicados no Supabase. Os nomes lógicos correspondem. O histórico aplicado do Supabase é a autoridade e esses arquivos não devem ser renomeados retroativamente.

## Acesso

O sistema usa Supabase Auth. Contas mestre usam Google; contas secundárias podem usar usuário/senha. A tabela `app_users` mantém a autorização interna do aplicativo. O banco continua sendo a autoridade para acesso aos dados.

## CI

### Web
`.github/workflows/ci.yml` executa, em `develop` e `main`, instalação reprodutível, typecheck, lint, testes unitários e build.

### Android
`.github/workflows/android-ci.yml` executa lint, testes unitários e build de debug para alterações Android. O baseline funcional `10ec3ab...` teve Android CI #62 com sucesso.

## Branches

- `fix/audit-device-id`: branch operacional atual;
- `develop`: integração/DEV;
- `main`: produção.

Uma migration existente no Git não prova aplicação no ambiente; para isso deve ser consultado o Supabase real.

## Arquivos de referência

- `src/app/router/router.tsx`;
- `src/shared/components/AppShell.tsx`;
- `src/shared/lib/supabase.ts`;
- `src/shared/offline/`;
- `src/features/offline/`;
- `android/.../app/AuthenticatedApp.kt`;
- `android/.../data/offline/OfflineStore.kt`;
- `android/.../ui/theme/NevesTheme.kt`;
- `supabase/migrations/`;
- `src/shared/types/database.types.ts`.


## Trabalho arquitetural ainda não integrado — 11.3

Existe uma evolução implementada e validada na branch isolada `feat/android-11-3-history-pagination`, HEAD `9aa118c16e5f6f91d1a0bd6482b5f5e892792c29`, que **ainda não faz parte da branch operacional**.

Nessa branch:

- a Web introduz helper de paginação histórica por cursor/chave primária;
- o Android introduz `data/supabase/KeysetPagination.kt`;
- as leituras históricas de Compras em Web/Android são paginadas por `id`;
- a leitura termina somente em página vazia e não devolve resultado parcial em falha intermediária;
- não houve migration nem mudança no Supabase.

Até que Elias autorize e a integração seja concluída, essa solução deve ser tratada como **trabalho validado ainda não integrado**, e não como arquitetura operacional já vigente.
