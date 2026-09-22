# Neves Estoque

Aplicativo interno de controle de estoque da **Panificadora Neves — Nordestina**.

## Estado

**EM IMPLEMENTAÇÃO**

O planejamento funcional e a arquitetura principal estão aprovados. Este repositório inicia a implementação técnica. O único projeto Supabase do Neves Estoque será usado primeiro com dados de teste e só passará a operar dados reais após validação completa, auditoria de segurança e preparação para produção.

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
- RLS deve existir desde a primeira migration exposta ao cliente.
- Segredos, tokens, service role e senhas nunca entram no Git.
- Apenas variáveis públicas apropriadas ao navegador podem usar prefixo `VITE_`.
- Entradas e Conferências oficiais devem ser gravadas por operações transacionais seguras.
- Antes de PROD haverá auditoria completa de segurança, correções e retestes.

## Ambiente local

1. Copie `.env.example` para `.env.local`.
2. Preencha apenas os valores públicos do projeto Supabase `Neves Estoque`.
3. Instale as dependências com `npm install`.
4. Execute `npm run dev`.

> O `package-lock.json` será gerado pelo npm e deve ser versionado antes de esta base ser considerada validada/testada.

## Regra central do estoque

**Última Conferência Física válida + Entradas posteriores = Estoque Atual do sistema.**
