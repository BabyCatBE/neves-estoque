# Neves Estoque

Aplicativo interno de controle de estoque da **Panificadora Neves — Nordestina**.

## Contexto obrigatório para agentes

Antes de realizar alterações técnicas relevantes neste projeto, leia os arquivos aplicáveis em `docs/contexto/`:

- `prd.md` — produto e escopo atual;
- `architecture.md` — arquitetura real;
- `rules.md` — restrições obrigatórias;
- `design.md` — padrões visuais atuais;
- `task.md` — trabalho operacional vigente;
- `memory.md` — decisões e aprendizados que precisam sobreviver entre sessões.

O **Contexto Mestre no Google Drive** continua sendo a fonte consolidada de continuidade geral. GitHub é autoridade para código, CI e migrations versionadas; Supabase é autoridade para o estado operacional real do banco.

## Estado atual — 29/09/2026

**IMPLEMENTAÇÃO INCREMENTAL / TESTES / PR EMPILHADO AINDA NÃO INTEGRADO À LINHA PRINCIPAL**.

- Web/System: `0.26.0`;
- Android: `0.34.0-alpha01` (versionCode 34);
- linha de trabalho atual: `feat/android-17-product-maintenance-ux`;
- PR atual: #75 — DRAFT / OPEN / NÃO INTEGRADO, base `feat/android-launcher-icon`;
- o HEAD atual deve ser consultado diretamente no GitHub; commits documentais posteriores ao último commit funcional não alteram o estado funcional;
- último commit funcional: `e7928813f757c45f19454f0fd9ac460b76c1aacd`;
- Android CI funcional #122: SUCCESS;
- commits exclusivamente documentais também podem disparar Android CI; a referência funcional permanece a CI do último commit funcional;
- Android 32: busca de Produto da Nova Entrada APROVADA EM APARELHO;
- Android 33: seletor visual de data `DD/MM/AAAA` IMPLEMENTADO / CI APROVADA / APARELHO A VERIFICAR;
- Android 34: **Modo Escuro** (Configurações → Aparência, seletor Sol/Lua, transição de ~300 ms, preferência local) IMPLEMENTADO / CI APROVADA / APARELHO A VERIFICAR.

A cadeia atual é empilhada: PRs #69–#75 permanecem abertos/em rascunho em suas bases sucessivas. Não confundir “presente na branch atual” com “integrado em main/develop”.

Para o estado mais recente, use `docs/contexto/task.md` e o cabeçalho do Contexto Mestre.

## Stack

### Web/PWA

- React + TypeScript + Vite
- Tailwind CSS
- React Router
- TanStack Query
- React Hook Form + Zod
- Supabase + PostgreSQL
- IndexedDB + Dexie
- Vitest + Playwright
- Netlify

### Android

- Kotlin + Jetpack Compose + Material 3
- Navigation Compose
- Supabase Kotlin + Ktor Android
- armazenamento Offline em arquivos JSON internos privados
- atualizador interno por GitHub Releases públicas, com SHA-256 obrigatório
- workflow de publicação de Release versionada e assinada, acionado conscientemente

## Branches e PRs

- `feat/android-17-product-maintenance-ux`: linha de trabalho atual / PR #75;
- `feat/android-launcher-icon`: base do PR #75 / PR #74;
- `feat/android-16-release-automation`: PR #73;
- `feat/android-15-in-app-updater`: PR #72;
- `feat/android-14-ux-design-motion`: PR #71;
- `feat/android-13-production-readiness`: PR #70;
- `feat/android-12-release-preparation`: PR #69;
- `fix/audit-device-id`: base operacional histórica que recebeu a integração do Bloco 11;
- `develop`: integração/DEV;
- `main`: produção.

Não fazer merge, release ou reescrita da cadeia sem seguir o estado consolidado e uma decisão explícita de Elias.

## Segurança

- cliente Web/Android não é autoridade final de autorização;
- as proteções de acesso do backend devem permanecer preservadas;
- segredos e credenciais privadas nunca entram no Git;
- pendências Offline são revalidadas online e não são enviadas automaticamente;
- antes de produção haverá auditoria final de segurança e retestes.

## Ambiente Web local

1. Copie `.env.example` para `.env.local`.
2. Preencha somente os valores públicos do projeto Supabase **Neves Estoque**.
3. Rode `npm ci`.
4. Use `npm run local` para o fluxo local padronizado ou `npm run dev` quando apropriado.

## Regra central do estoque

**Última Conferência Física válida + Entradas posteriores = Estoque Atual do sistema.**
