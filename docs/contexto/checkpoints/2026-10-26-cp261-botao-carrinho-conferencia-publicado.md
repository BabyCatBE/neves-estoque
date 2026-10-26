# CHECKPOINT 261 — Seletor de compra virou botão de carrinho; Web/Android publicados

**Data:** 26/10/2026.
**Estado:** IMPLEMENTADO / CI WEB APROVADA / CI ANDROID APROVADA / WEB PROD PUBLICADA / RELEASE ANDROID PUBLICADA / TESTE FUNCIONAL EM APARELHO A VERIFICAR.
**Autorização:** Elias pediu explicitamente alteração Android e Web, Release e deploy, com aviso quando concluído.

## Escopo exato (somente visual)

- Conferência por Categoria Android: checkbox + texto **Pedir** substituídos por botão compacto de carrinho no **canto superior direito de cada card**, 48 dp, formato arredondado; vermelho quando selecionado, discreto quando livre. Usa `IconToggleButton` e ícone já existente `NevesIcons.Cart`.
- Conferência Web: checkbox + texto **Pedir** substituídos por botão compacto arredondado com SVG de carrinho, top-right no layout mobile e coluna direita responsiva no desktop, `aria-label` dinâmico, `aria-pressed`, teclado/foco e disabled preservados.
- Toggle, persistência local por usuário, quantidade física da Conferência, estoque de referência, seleção para pedido e exportação permanecem **sem mudança lógica**.
- Não houve alteração em banco, migrations, autorização backend, Supabase, dados reais nem envio automático offline.

## GitHub e versões

- Repositório `BabyCatBE/neves-estoque`; `develop` integração após PR.
- PR **#93**, MERGED: https://github.com/BabyCatBE/neves-estoque/pull/93
- SHA código proposto/CI: `525fc807475fa0bf54d42c8ccd0d5ca6cc82ce6f`.
- SHA merge na `develop`: `0450b1135efa8b77f6c402019840c603f129b796`.
- SHA HEAD `develop` antes deste checkpoint: `760fd707beb93b767391bb89a53427142ee59636`, após workflows documentados. `main` NÃO alterada.
- Web **0.26.3**, `package.json` e lockfile alinhados.
- Android **0.39.0-alpha01 / versionCode 39**. `SYSTEM_VERSION` Android não foi alterado (A VERIFICAR separado).

## Validação e publicação Web

- CI Web código PR: **40399328785 SUCCESS**.
- Deploy Cloudflare: **40400225641 SUCCESS**, source SHA `b58c3ae2182b5a764f40881ca3800a0c8b2148bc`.
- Endpoint oficial https://neves-estoque.pages.dev, implantação de produção confirmada no projeto Cloudflare Pages `neves-estoque`, URL de deploy `https://6136621e.neves-estoque.pages.dev`.
- Workflow one-time: `.github/workflows/publish-approved-web-20261026-cart.yml`; diff do código Web conferido com SHA aprovado e CI validada, variáveis públicas Supabase conferidas durante build sem expor valores.
- Teste manual no navegador autenticado **A VERIFICAR**.

## Validação e publicação Android

- Android CI do PR **40399329355 SUCCESS**; build assinado APK, lint e testes automatizados segundo pipeline. Run em evento `pull_request`, head SHA `525fc807475fa0bf54d42c8ccd0d5ca6cc82ce6f`.
- Artefato assinado `neves-estoque-android-signed-0.39.0-alpha01-1edd4915cf9ba41b646599a2ec8e1748602987d3` (run CI 40399329355). Commit sintético PR `1edd4915cf9ba41b646599a2ec8e1748602987d3` tem a **mesma árvore** de `525fc807...`, verificado em GitHub commits antes de publicar.
- Workflow one-time `.github/workflows/publish-approved-android-20261026-cart.yml`, run **40400718241 SUCCESS**; verificou CI/head/árvore, baixou o APK assinado original sem recompilar, conferiu SHA256SUMS, certificado permanente, pacote e versionCode.
- Release oficial **https://github.com/BabyCatBE/neves-estoque/releases/tag/android-v0.39.0-alpha01**.
- `releases/latest` confirmado em `android-v0.39.0-alpha01`, `draft=false`, `prerelease=false`. Assets públicos únicos: `neves-estoque-android.apk` e `SHA256SUMS`; versão no corpo `versionCode: 39`.
- Tag `refs/tags/android-v0.39.0-alpha01` e branch `release/android-0.39.0-alpha01-approved-source` apontam exatamente a `525fc807475fa0bf54d42c8ccd0d5ca6cc82ce6f`, sem mover `main`.
- O atualizador embutido busca `releases/latest` e deve detectar `versionCode 39` em instalação anterior com code menor, mas **instalação e teste real no aparelho ainda NÃO REALIZADOS**.

## Estado seguro, falhas e próximo passo

- Último APK anteriormente publicado: 0.38.0-alpha01 (CP260), mantido e acessível como rollback de Release. Web anterior: 0.26.2, implantação Cloudflare histórica preservada.
- Desta rodada não houve falha relevante de CI final: ambas aprovadas; houve somente duas tentativas abortadas durante montagem de texto do workflow em ferramenta antes de criar arquivo (nenhum workflow parcial/errado executado).
- Próximo passo: teste em aparelho Android (abrir Configurações > Atualizar, instalar versão 39 sobre a 38, preservar rascunhos e seleção) e teste Web autenticado, conferindo estado selecionado/não selecionado, canto superior direito, acessibilidade e comportamento da seleção.
- Nada exige migração Supabase ou mudança de banco.
- Contexto Mestre e Notion ainda não consolidados; aguardar solicitação, conforme regras do Projeto.
- Modelo IA GPT-6; esforço de raciocínio não informado.
