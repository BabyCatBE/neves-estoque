# CHECKPOINT 260 — Release Android 0.38 publicada; Web 0.26.2 em produção

**Data:** 26/10/2026
**Estado:** ANDROID RELEASE PUBLICADA / CI APROVADA / WEB PROD PUBLICADA / TESTE NO APARELHO A VERIFICAR.

## Autorizações e escopo

Elias autorizou explicitamente publicar a nova Web e Release Android e, após bloqueio, solicitou de novo: “Poste a realease”. Não houve autorização para atualizar `main`, limpar dados de usuário ou migrar banco; nada disso foi feito.

## Android Release — evidência final

- Versão: **0.38.0-alpha01**, `versionCode=38`; package `com.babycatbe.nevesestoque`.
- URL oficial: https://github.com/BabyCatBE/neves-estoque/releases/tag/android-v0.38.0-alpha01
- GitHub `releases/latest`: confirmado `android-v0.38.0-alpha01`, `draft=false`, `prerelease=false`, publicado em **2026-10-26T20:31:39Z**.
- Assets públicos confirmados: `neves-estoque-android.apk` (13.362.199 bytes) e `SHA256SUMS` (92 bytes).
- Tag `refs/tags/android-v0.38.0-alpha01` aponta **exatamente** para commit aprovado `812ff87be84f67c77b2dacebd4248ae801e941fc`, sem tag móvel; ref `release/android-0.38.0-alpha01-approved-source` também aponta exatamente a esse SHA.
- Android CI fonte `40391731301`: **SUCCESS**, APK assinado com certificado permanente SHA-256 `9901a1ea1fac3b993449a981770f63759b3b74e8c007f3ff9f56f1cbc7e6017a` e `versionCode 38`.
- Workflow de publicação `40396370593`: **SUCCESS**. Baixou o artefato original da CI; verificou `SHA256SUMS`, assinatura, pacote e versão; copiou sem recompilar; criou Release; verificou assets/versionCode/body.
- Checksum do APK: `7d0fd18f5218583cfc538105acb1927d1730ad75a9b3b6294f35ad3ec5717a81`.
- O botão Android **Atualizar** consulta `https://api.github.com/repos/BabyCatBE/neves-estoque/releases/latest` e compara `versionCode`; agora deverá detectar 38 em aparelhos ainda em 37, sujeito a conexão/estado local. **Nenhum teste real no aparelho realizado neste bloco.**
- **Nunca desinstalar ou apagar dados locais para atualizar.**

## Web PROD

- Web versão `0.26.2` publicada por GitHub Actions `40391306876` (**SUCCESS**) em Cloudflare Pages `https://neves-estoque.pages.dev`, deployment `a139ff7e-0d7a-45a0-a906-987f20498af1`. Ver CP259 para verificação de configuração pública Supabase e correção intermediária no guard.
- Teste funcional Web autenticado do novo fluxo continua **A VERIFICAR**.

## Histórico de falhas relevantes durante a publicação Android

- CP259 documentou sucessivas falhas intermediárias: asserção incorreta do code 37, job antigo, validação de evento incorreta, falha no output de versão e, principalmente, resposta `HTTP 403 Resource not accessible by integration` do GitHub em `gh release create` com destino a SHA sem ref na ponta da branch `main`.
- Tentativa `40396129404` criou tag via `gh api /git/refs`, mas também foi bloqueada por 403 devido à permissão do `GITHUB_TOKEN`.
- Solução: GitHub connector autorizado criou somente a branch imutável de origem `release/android-0.38.0-alpha01-approved-source`, HEAD exatamente `812ff87...`, **sem modificar main**. O publisher revalidou a branch, e `gh release create --target release/android-0.38.0-alpha01-approved-source` publicou com `Contents: write`. Workflow `40396370593` finalizou SUCCESS.
- Mantidas falhas como trilha do processo. Nenhuma versão anterior substituída; sem tag modificada.

## Continuidade

- `main` permaneceu no histórico anterior, sem alteração; integração funcional ocorreu via PR #92 em `develop`.
- Não houve mudanças destrutivas, migrations Supabase nem escrita em dados produtivos.
- Versão Android prévia `0.37.0-alpha01` continua disponível como histórico/rollback; Web antiga pode ser consultada por deploy anterior no Cloudflare.
- Próximo passo: confirmar detecção da atualização pelo botão, instalação sem perder rascunhos/pendências, teste de Conferência + referência de estoque + marcação em várias categorias + pedido no Android real; repetir no Web autenticado, inclusive offline e troca de usuário quando aplicável.
- Consolidação de Contexto Mestre, BabyCat-OS e Notion depende de solicitação separada, conforme regras do projeto.
- Modelo: GPT-6; esforço não verificado.
