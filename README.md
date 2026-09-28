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

## Estado atual

**EM IMPLEMENTAÇÃO / TESTES**.

- Web: `0.26.0`;
- Android: `0.21.0-alpha01` (versionCode 21);
- branch operacional: `fix/audit-device-id`;
- Bloco Android 11 de 13 em andamento;
- sub-blocos 11.1 e 11.2 concluídos;
- próximo trabalho: definir/executar 11.3 a partir do diagnóstico;
- testes funcionais, Offline e performance em aparelho continuam `A VERIFICAR`.

Para o estado mais recente, use `docs/contexto/task.md` e o cabeçalho do Contexto Mestre; não use snapshots históricos deste README como substituto.

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
- armazenamento Offline atual em arquivos JSON internos privados

## Branches

- `fix/audit-device-id`: branch operacional atual;
- `develop`: integração/DEV;
- `main`: produção.

Não fazer merge ou release sem seguir o estado consolidado e a instrução explícita da etapa.

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
