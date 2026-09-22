# Neves Estoque

Aplicativo interno de controle de estoque da **Panificadora Neves — Nordestina**.

## Estado

**EM IMPLEMENTAÇÃO**

O planejamento funcional e a arquitetura principal estão aprovados. A base técnica, o núcleo inicial do banco e o fluxo frontend de autenticação Google já foram implementados. O único projeto Supabase do Neves Estoque será usado primeiro com dados de teste e só passará a operar dados reais após validação completa, auditoria de segurança e preparação para produção.

### Marco atual
- schema inicial, RLS, auditoria e RPCs operacionais aplicados no Supabase;
- Google OAuth configurado no Google Cloud e Supabase;
- duas contas V1 autorizadas em `app_users`;
- frontend de login Google, autorização interna, registro de dispositivo, proteção de rotas e logout implementado em `develop`;
- CI validando typecheck, lint, testes unitários e build;
- login OAuth real ainda precisa ser testado de ponta a ponta em um ambiente com URL de redirecionamento configurada.

## Stack aprovada

- React + TypeScript + Vite
- Tailwind CSS + shadcn/ui
- React Router
- TanStack Query
- React Hook Form + Zod
- Supabase + PostgreSQL
- PWA com vite-plugin-pwa
- IndexedDB + Dexie
- Vitest + Playwright
- Netlify

## Branches

- `develop`: integração e validação durante o desenvolvimento
- `main`: linha aprovada para produção

## Segurança

- O frontend nunca é autoridade de autorização.
- RLS existe desde a primeira migration exposta ao cliente.
- Segredos, tokens, service role e senhas nunca entram no Git.
- Apenas variáveis públicas apropriadas ao navegador podem usar prefixo `VITE_`.
- Entradas e Conferências oficiais são gravadas por operações transacionais seguras.
- O login Google autentica a identidade; `app_users` decide se a conta pode acessar o app.
- Antes de produção haverá auditoria completa de segurança, correções e retestes.

## Ambiente local

1. Copie `.env.example` para `.env.local`.
2. Preencha apenas os valores públicos do projeto Supabase `Neves Estoque`.
3. Instale as dependências com `npm ci`.
4. Execute `npm run dev`.

O `package-lock.json` está versionado e o CI usa `npm ci`.

## Regra central do estoque

**Última Conferência Física válida + Entradas posteriores = Estoque Atual do sistema.**
