# CHECKPOINT 259 — Publicação Web 0.26.2 e Android 0.38 (Release GitHub bloqueada)

Data: 26/10/2026. Responsável autorizou explicitamente publicação Web e nova Android Release na conversa deste bloco.

## Estado consolidável deste bloco

- **Web:** PUBLICADO no Cloudflare Pages PROD; CI APROVADA; teste funcional autenticado A VERIFICAR.
- **Android:** IMPLEMENTADO, CI APROVADA, APK assinado validado e disponibilizado para download manual; **Release pública GitHub NÃO PUBLICADA** por 403 de permissão; atualização automática in-app continua apontando à versão antiga; instalação/teste em aparelho A VERIFICAR.
- **Não houve** alterações de schema/migrations ou dados Supabase, exclusão/limpeza de dados, push na `main`, nem publicação de outra funcionalidade.

## Integração

- PR #92: https://github.com/BabyCatBE/neves-estoque/pull/92 — MERGED em `develop`.
- Merge: `41c42ceda3dc3f08ab1a85c3ec422ede11224a40`; `develop` HEAD após tentativas de publicação: `0b284533a8be76a6687fac064f31af2626ab0d45`.
- Versão Web: `0.26.2` (`package.json`/`package-lock.json`).
- Android: `versionName=0.38.0-alpha01`, `versionCode=38`, package `com.babycatbe.nevesestoque`; campo `SYSTEM_VERSION` não foi alinhado automaticamente (A VERIFICAR, decisão histórica).

## Web PROD

- `https://neves-estoque.pages.dev`, projeto Cloudflare Pages `neves-estoque`, production branch `develop`.
- Workflow one-time `.github/workflows/publish-approved-web-20261026.yml`, execução GitHub Actions **40391306876 SUCCESS**, commit `09e7e0e14d9620d185a34495f57e3b624e6b4af4`.
- Cloudflare deployment de produção ID `a139ff7e-0d7a-45a0-a906-987f20498af1`, preview `https://a139ff7e.neves-estoque.pages.dev`; deploy confirmado em leitura via API.
- Variáveis públicas de configuração Supabase verificadas sem expor valores; build verificou presença no bundle.
- Primeira tentativa `40391119755` FALHOU no guard de commit antigo; foi corrigida antes do deploy verde. Não houve publicação parcial da tentativa malsucedida.
- Testes manuais autenticados do novo fluxo e atualização da PWA continuam A VERIFICAR.

## Android — compilação e artefato de Release

- Android CI `40391731301` **SUCCESS**, source SHA `812ff87be84f67c77b2dacebd4248ae801e941fc`. Artefato assinado `neves-estoque-android-signed-0.38.0-alpha01-812ff87be84f67c77b2dacebd4248ae801e941fc`, artifact ID `12685185910`.
- Revalidado localmente o ZIP oficial de CI: `sha256sum -c SHA256SUMS` OK, badging de APK `com.babycatbe.nevesestoque`, versionCode 38, versionName 0.38.0-alpha01 e assinatura permanente (SHA-256 cert) `9901a1ea1fac3b993449a981770f63759b3b74e8c007f3ff9f56f1cbc7e6017a`.
- SHA-256 do APK exatamente assinado: `7d0fd18f5218583cfc538105acb1927d1730ad75a9b3b6294f35ad3ec5717a81`.
- Versão 0.38 disponibilizada manualmente como anexo desta conversa, sem instalação comprovada no aparelho.
- O APK não foi reconstruído nem reassinado depois da CI.

## Release pública — BLOQUEADA / ação humana

- Workflow aprovado `.github/workflows/publish-approved-android-20261026.yml` tenta entregar exatamente o APK aprovado na CI com ativos `neves-estoque-android.apk` e `SHA256SUMS`.
- Falhas relevantes: CI intermediária `40390612568` falhou após build por asserção de versionCode 37 obsoleta (corrigida); primeiro publisher aguardou essa CI antiga; outra tentativa herdou bug de leitura do output de passo; tentativa seguinte exigia origem pull_request mas CI validada ocorreu em push.
- Workflow publisher `40393184507`: fonte exata verificada, artefato assinado baixado, certificado, versão e hash aprovados. Falhou apenas no `gh release create`: **HTTP 403 Resource not accessible by integration**, na rota POST `/repos/BabyCatBE/neves-estoque/releases`.
- Token da GitHub Actions mostrava `Contents: write` e `Actions: read`, ou seja, NÃO atribuir automaticamente a falha à ausência de `contents: write`.
- Causa compatível com exigência atual documentada pelo GitHub: o target `812ff87...` adiciona/modifica `.github/workflows/*` em relação à branch default `main` muito antiga. Neste cenário a API de criação de Releases exige também **Workflows: write**, que o `GITHUB_TOKEN` automatizado não disponibiliza. Comparação `main` → `812ff87...` revelou oito workflows adicionados. Não contornar alterando `main`, fingindo target antigo ou expondo secrets.
- `releases/latest` CONTINUA `android-v0.37.0-alpha01`; **NÃO dizer que botão Atualizar já oferece 0.38**.
- Solução segura: publicação manual no GitHub UI, por pessoa autorizada, da release `android-v0.38.0-alpha01` com target exato `812ff87...`, título igual, não draft e não prerelease, marcar como Latest, subir os dois arquivos canônicos `neves-estoque-android.apk` e `SHA256SUMS`, incluir `versionCode: 38` no corpo; ou fornecer credencial GitHub App/PAT restrita ao repo com `Contents: write` + `Workflows: write` via Secret da Actions (nunca colar na conversa).
- Assets válidos preparados nesta conversa também em ZIP com instruções. No GitHub ainda não foram anexados.

## Fonte segura / próximos passos

- **Último estado Android publicado e funcional seguro permanece CP257**: `android-v0.37.0-alpha01` / 37. Site Web atual publicado é 0.26.2, pendente teste funcional autenticado.
- Para completar atualização via botão do Android, publicar Release 0.38 no GitHub, verificar `releases/latest`, assets, corpo, SHA-256, versão e resposta do atualizador no aparelho. Não desinstalar app/limpar dados.
- Após teste manual e publicação, registrar evidências/resultado; consolidação no Contexto Mestre/Notion aguarda solicitação de Elias.
- Modelo IA GPT-6; esforço não verificado.
