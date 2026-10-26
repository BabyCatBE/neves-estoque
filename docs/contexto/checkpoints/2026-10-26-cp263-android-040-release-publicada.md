# CHECKPOINT 263 — Release Android 0.40.0-alpha01 publicada

**Data:** 26/10/2026
**Estado:** ANDROID RELEASE PUBLICADA / CI APROVADA / TESTE REAL NO APARELHO A VERIFICAR / WEB SEM MUDANÇA.
**Autorização:** Elias respondeu explicitamente **“Sim”** à pergunta de publicar Release oficial Android 0.40 após CP262.

## Autoridade e último estado seguro

- **Última Release Android publicada anteriormente:** `android-v0.39.0-alpha01` (versionCode 39), preservada para rastreabilidade. Release 0.40 foi publicada somente após os testes automatizados e integridade do APK. **Teste operacional no dispositivo e recuperação da pendência original permanecem A VERIFICAR**, não confundir CI com teste real.
- Repositório `BabyCatBE/neves-estoque`: código corrigido via PR #95, merge SHA `d8ce748cce30f71f251e5bb8236423e78ccbdd46`. HEAD `develop` antes deste checkpoint `67c8cf7580f4385446a7a1201f74e0deaf72ae01` (workflow documental posterior ao merge). `main` preservada em `e0c15eb45fda2f6c68f58ed6f4ccdd24feb20635`.
- Web PROD permanece versão **0.26.3**, sem novo deploy neste bloco.
- Banco Supabase: **nenhuma migration, escrita de dados, limpeza ou alteração operacional** neste bloco.

## Correções da 0.40

1. Pendência local de Conferência por Categoria com catálogo antigo (10 produtos salvos; atualmente 11 em MANTEIGAS, MARGARINAS E REQUEIJOES): ao editar online, mostra `Delicia 250g` como NOVO, exige quantidade física digitada conscientemente (inclusive zero explícito), preserva todos os valores anteriores, data, responsável, `localId`, `idempotencyKey` e arquivo local. Validação online por IDs ativos antes de permitir envio e novamente antes do RPC. Não envia automaticamente. Ver CP262 / Issue #94.
2. Carrinho da Conferência Android: formato arredondado uniforme para contorno, cor de seleção e efeito de toque/ripple via `Surface(onClick)`. Sem mudança na lógica de pedidos.
3. Versionamento: `versionName=0.40.0-alpha01`, `versionCode=40` no Gradle e CI.

## Evidências de CI, artefato e publicação

- CI Android aprovada **40408834376 SUCCESS**, fonte aprovada `34951d66799ae2c34162853b491f3e148c7e5178`.
- CI geral **40408834312 SUCCESS**.
- Artefato Android assinado aprovado: `neves-estoque-android-signed-0.40.0-alpha01-6df90d25636b9432bc0e8df36fffef4828cf9e32` da CI `40408834376`, artifact ID `12691839227`. Árvore Git do commit sintético de PR é igual à fonte de CI e ao código integrado, conforme CP262.
- SHA-256 original do APK (CP262): `68fb0bc1c367764d9e38243b1821c42b6758760e9e939e6a0612fc79297c2bda`; certificado permanente verificado SHA-256 `9901a1ea1fac3b993449a981770f63759b3b74e8c007f3ff9f56f1cbc7e6017a`.
- Branch de origem da Release `release/android-0.40.0-alpha01-approved-source`, apontando para SHA aprovado `34951d66799ae2c34162853b491f3e148c7e5178`. Tag `android-v0.40.0-alpha01` confirmada diretamente no mesmo commit, sem modificar `main`.
- Workflow de publicação única `.github/workflows/publish-approved-android-20261026-pendingfix.yml`, execução **40411239982 SUCCESS**: verifica CI, árvore Git, SHA256SUMS, certificado, package, versionCode/Name, existência única da tag, origem e release. Usa APK testado sem recompilar.
- URL oficial: https://github.com/BabyCatBE/neves-estoque/releases/tag/android-v0.40.0-alpha01
- `releases/latest` confirmado `android-v0.40.0-alpha01` com `draft=false`, `prerelease=false`; assets únicos `neves-estoque-android.apk` (13.378.583 bytes) e `SHA256SUMS` (92 bytes). Corpo confirma `versionCode: 40`. Publicação GitHub em `2026-10-26T22:17:31Z`.
- APK direto: https://github.com/BabyCatBE/neves-estoque/releases/download/android-v0.40.0-alpha01/neves-estoque-android.apk
- Agora o botão `Atualizar` que consulta `releases/latest` deverá detectar 40 em aparelhos com versionCode menor; confirmação no aparelho A VERIFICAR.

## Continuidade — próximo passo

- Instalar por cima do aplicativo anterior, **não desinstalar, não limpar cache/dados de pendências**.
- Em `Pendências locais > Editar` na categoria com onze produtos, verificar se `Delicia 250g · NOVO` aparece; registrar quantidade real conscientemente, salvar e conferir se os dez itens antigos permaneceram intactos. Só então enviar com nova confirmação manual e validar no estoque oficial.
- Testar visualmente o ripple e o preenchimento uniforme do carrinho selecionado/desmarcado, e garantir ausência de efeito circular nas bordas.
- Reavaliar a paridade Web para o reparo de pendências se houver cenário de catálogo desatualizado; **Web não foi alterada neste bloco**.
- Contexto Mestre, `docs/contexto/task.md` e Notion ainda não consolidados neste bloco; aguardar pedido expresso de Elias conforme regras do Projeto.
- Modelo GPT-6; esforço de raciocínio não confirmado.
