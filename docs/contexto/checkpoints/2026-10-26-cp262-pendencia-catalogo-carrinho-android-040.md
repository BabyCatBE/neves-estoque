# CHECKPOINT 262 — Recuperação de pendência com catálogo antigo + correção visual do carrinho (Android)

**Data:** 26/10/2026
**Estado:** IMPLEMENTADO NA DEVELOP / CI ANDROID APROVADA / CI GERAL APROVADA / APK 0.40 ASSINADO E VERIFICADO / RELEASE OFICIAL 0.40 **NÃO** PUBLICADA / TESTE EM APARELHO A VERIFICAR.

## Origem operacional e diagnóstico

Usuário apresentou captura de tela do Android 0.39 em **Pendências locais > Confirmar envio**, Categoria **MANTEIGAS, MARGARINAS E REQUEIJOES**, erro do backend: "A Conferência da categoria precisa conter todos os produtos ativos." Outra captura da edição comprovou dez produtos com todas as quantidades, incluindo zero válido, mas sem **Delicia 250g**.

Leitura real da PROD Supabase em 26/10 (somente SELECT): Categoria com **11 produtos ativos**. Dez foram cadastrados em setembro e **Delicia 250g** foi acrescentado em **21/10**. Função efetiva `private.create_category_conference_impl` exige `jsonb_array_length(p_items)` igual a contagem dos produtos ativos da categoria e valida os IDs exatos. O Android monta pendência conforme cache offline de produtos; o editor anterior só permitia alterar itens existentes. A causa foi confirmada como divergência de catálogo **10 itens salvos vs. 11 atuais**, não erro de quantidade.

**Nenhum dado real foi escrito, removido ou migrado no Supabase. Nenhuma pendência do aparelho foi lida/removida remotamente.**

## Código / PR

- Repositório: `BabyCatBE/neves-estoque`.
- PR **#95 MERGED**: https://github.com/BabyCatBE/neves-estoque/pull/95
- Branch de origem: `fix/android-pending-catalog-repair-cart-ripple-20261026`.
- HEAD aprovado/testado da branch: `34951d66799ae2c34162853b491f3e148c7e5178`.
- Merge em `develop`: `d8ce748cce30f71f251e5bb8236423e78ccbdd46`. Árvore Git idêntica à de código do PR e do commit sintético testado `6df90d25636b9432bc0e8df36fffef4828cf9e32`; tree `26cc3b32df410bfcd2b6cc535a763f75d288aa96`.
- `main` preservada; nenhuma alteração no banco, migrations, Web ou produção.

### Correção funcional — Android

- Novo `feature/offline/PendingCatalog.kt`: consulta diretamente **produtos ativos do servidor**, sem `offlineCachedList`, pagina por ID e compara IDs guardados vs. IDs esperados. Detecta ausentes, movidos/excluídos e IDs duplicados, não somente contagem.
- `PendingScreens.kt`: na edição da **mesma pendência**, carrega o catálogo online, explica divergências, mostra campos novos rotulados `NOVO`; `NevesNumericField` com teclado numérico Neves. Nunca sugere quantidade zero, deixa em branco e exige digitação consciente; zero digitado é válido. Se catálogo inacessível, preserva arquivo.
- `PendingEdit.kt`: adicionar somente produtos ativos realmente faltantes com quantidade válida, preservando `localId`, `idempotencyKey`, `createdAt`, responsável, data, observação e todos os demais itens; salva via `PendingStore.save` (escrita local atômica). Caso haja produto removido/movido, duplicata ou categoria vazia, não remove dados automaticamente e mostra aviso.
- `PendingSync.kt`: comparação online ANTES de mostrar confirmação de envio e **nova verificação imediatamente antes do RPC**; divergência bloqueia envio sem remover o arquivo; também recusa objeto local alterado entre abertura de diálogo e envio. Offline continua exigindo envio consciente.
- `CategoryConferenceFormScreens.kt`: o carrinho anterior usava `IconToggleButton` com ripple circular dentro do fundo retangular; trocado por `Surface(onClick)` cujo shape, preenchimento, contorno e efeito de toque são o **mesmo RoundedCornerShape(14.dp)**. A seleção local não muda.

### Versão e validação

- Android `versionName=0.40.0-alpha01` e `versionCode=40`, `android/app/build.gradle.kts` e `android-ci.yml` alinhados.
- **Android CI final 40408834376 SUCCESS** — lint, testes unitários (incluindo seis novos casos: faltante, zero explícito, branco, negativo, catálogo movido, duplicatas e ordem), build/debug/release e verificação da configuração de Release. Run: https://github.com/BabyCatBE/neves-estoque/actions/runs/40408834376
- **CI geral final 40408834312 SUCCESS** (sem alterações Web).
- Falhas intermediárias relevantes: Android CI `40406974787` e `40407090643` FALHARAM exclusivamente em testes por comparação textual de fusos; asserção alterada para comparar instantes. Run `40407724403` FALHOU por inserção acidental de caracteres literais `\\n` na linha da asserção; arquivo Kotlin corrigido antes do run verde. O código funcional compilou nessas tentativas, mas essas CIs não eram suficientes.
- Artefato **assinado pela CI**: `neves-estoque-android-signed-0.40.0-alpha01-6df90d25636b9432bc0e8df36fffef4828cf9e32`, artifact ID `12691839227`. Download do artefato oficial conferido localmente: `sha256sum -c SHA256SUMS` OK, package `com.babycatbe.nevesestoque`, versionCode **40**, versionName **0.40.0-alpha01**, certificado permanente SHA-256 `9901a1ea1fac3b993449a981770f63759b3b74e8c007f3ff9f56f1cbc7e6017a`.
- SHA-256 do APK CI: `68fb0bc1c367764d9e38243b1821c42b6758760e9e939e6a0612fc79297c2bda`, tamanho 13.378.583 bytes. Cópia instalada? **NÃO TESTADA em aparelho**. Cópia entregue como arquivo de conversa, não como Release oficial.

## Estado seguro / próxima ação

- **Último Android oficialmente publicado:** `android-v0.39.0-alpha01` (versionCode39), validado por leitura de `releases/latest` após merge. **Botão Atualizar NÃO oferece 0.40**, porque nenhuma Release 0.40 foi publicada.
- **Web PROD:** versão `0.26.3` permanece publicada no Cloudflare; não houve deploy Web neste bloco. Paridade da recuperação de divergências no Web foi **avaliada superficialmente, A VERIFICAR** em bloco posterior; problema e captura específicos eram Android.
- Próximo passo: obter autorização explícita para Release oficial 0.40, se desejada, usando exatamente o APK aprovado e sem recompilar, depois validar detecção no botão Atualizar e instalação sobre 0.39 preservando dados. Na edição da pendência da categoria, conferir carregamento online de **Delicia 250g**, informar quantidade física verdadeira, salvar a mesma cópia, reabrir confirmação e enviar **somente após confirmação manual**. Verificar carrinho e layout no aparelho. A futura publicação não deve ocorrer automaticamente pela existência deste checkpoint.
- Contexto Mestre e Notion **não consolidados**, aguardam pedido expresso do responsável. `docs/contexto/task.md` não foi atualizado no bloco técnico; esse checkpoint é o registro corrente após CP261.
- Modelo GPT-6; esforço não registrado.
