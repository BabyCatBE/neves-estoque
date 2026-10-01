# Arquitetura — Neves Estoque

> Estado consolidado em 30/09/2026. Último estado funcional seguro: CHECKPOINT 254 / `develop@c0e2ad41a8ad5d64db700453a28826eaf03b8594`.

## Visão geral

O repositório contém dois clientes que usam o mesmo backend:

- Web/PWA em React + TypeScript + Vite;
- Android nativo em Kotlin + Jetpack Compose;
- Supabase gerenciado + PostgreSQL;
- Netlify para hospedagem Web;
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

Versão do pacote: **0.26.1**.

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

O Offline Web usa IndexedDB/Dexie. Pendências locais não devem ser enviadas automaticamente ao reconectar.

## Android

Configuração atual observada em `android/app/build.gradle.kts`:
- applicationId `com.babycatbe.nevesestoque`;
- Kotlin + Jetpack Compose + Material 3;
- Java 17;
- compileSdk 37;
- targetSdk 36;
- minSdk 26;
- Android `0.35.0-alpha01`;
- versionCode 35;
- `SYSTEM_VERSION = 0.26.0`.

### Divergência de versão

Web/package está em `0.26.1`, enquanto o `SYSTEM_VERSION` compilado no Android permanece `0.26.0`.

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
- cache é reconstruível;
- pendências são persistentes;
- reconectar não envia automaticamente;
- envio exige confirmação consciente;
- revalidação online e idempotência;
- limpeza de cache não apaga pendências.

## Supabase

Projeto operacional: **Neves Estoque**, região `sa-east-1`.

Estado verificado em 30/09/2026:
- status ACTIVE_HEALTHY;
- 42 migrations aplicadas;
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

- `develop`: integração/DEV;
- `main`: produção;
- último HEAD **funcional** consolidado: `c0e2ad41a8ad5d64db700453a28826eaf03b8594`;
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
