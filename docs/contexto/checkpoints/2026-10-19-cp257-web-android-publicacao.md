# CHECKPOINT 257 — Consolidação das publicações Web e Android — 19/10/2026

**Estado:** IMPLEMENTADO / CI APROVADA / RELEASE ANDROID PUBLICADA / WEB PROD PUBLICADA / LOGIN WEB CONFIRMADO PELO RESPONSÁVEL / TESTES OPERACIONAIS DIFERIDOS / CONSOLIDADO DOCUMENTALMENTE.

## Último estado seguro versus publicações posteriores

- **Base funcional com teste em aparelho anterior:** CHECKPOINT 256 (07/10/2026), Android `0.36.0-alpha01` validado por Elias na edição de Preço inicial. Este teste **não** cobre as mudanças de 19/10.
- **Código integrado/publicado novo:** `develop@d0b490b138faa0fad0321e431745560622895f50` (merge do hotfix de configuração Web). Web `package.json: 0.26.1`; Android `0.37.0-alpha01`, `versionCode 37`. Android `SYSTEM_VERSION: 0.26.0` continua **A VERIFICAR**, sem alteração para ajustar documentação.
- **`main` permanece:** `e0c15eb45fda2f6c68f58ed6f4ccdd24feb20635`; não equiparar `main` ao runtime publicado no Cloudflare, cuja origem foi `develop`.
- O avanço documental deste checkpoint pode gerar commits mais novos em `develop` **sem alterar código de runtime, APK ou build já implantado**.

## Código funcional incluído

PRs #81, #82, #83 e #85 integradas em `develop` com autorização explícita do responsável para publicação mesmo sem testes práticos prévios.

- **Conferência Android/Web:** salvar/validar em segundo plano após persistência local; distinguir verificação de consumo e alerta de conferência no mesmo dia; correção histórica usa **update** do ID original, não cria outra conferência. Erros e conflitos pedem ação consciente.
- **Rascunho de conferência incompleta:** quantidades podem permanecer em branco; zero continua preenchimento válido. `Guardar e sair`, `Continuar`, `Descartar` e retomada na categoria. Android armazena JSON privado e atômico em `conference-drafts/`, Web em tabela separada IndexedDB/Dexie; rascunhos nunca são pendências enviáveis e **não alteram o estoque**. Proteção contra rotação no Android; `Próximo` permite pular produto sem convertê-lo em zero; envio final exige todos os itens. Rascunhos isolados por usuário/categoria; catálogo alterado/rascunho inválido deve ser preservado, não silenciosamente sobrescrito.
- **Entradas:** informar preço unitário ou total do item da nota; total informado ajuda a calcular unitário com precisão interna, exibido com duas casas. Em branco não equivale à bonificação zero. Web: campo Quantidade → Unitário → Total, Tab e Enter preservando fluxo; Android alternância discreta com teclado numérico Neves, com paridade na edição.
- **Impressão:** categorias longas na impressão A4, limite de 22 itens por página no escopo da PR #83, sem alterar dados operacionais.

## GitHub / CI / Release

- PRs de código: [#81](https://github.com/BabyCatBE/neves-estoque/pull/81), [#82](https://github.com/BabyCatBE/neves-estoque/pull/82), [#83](https://github.com/BabyCatBE/neves-estoque/pull/83), [#85](https://github.com/BabyCatBE/neves-estoque/pull/85).
- PR #86 versão Android: `0.37.0-alpha01`/`versionCode 37`. Android CI **#194**, run `39302190080`, **SUCCESS**, SHA da fonte aprovado `6dc69733da8c1a7966a4ec42f8210b8975fb8f75`. Artefato assinado verificado contra essa árvore.
- PR #87 publicador Android de uso único; run `39303909499` **SUCCESS**. Release GitHub oficial [`android-v0.37.0-alpha01`](https://github.com/BabyCatBE/neves-estoque/releases/tag/android-v0.37.0-alpha01), pública, assets `neves-estoque-android.apk` e `SHA256SUMS`. Atualização interna prevista; **instalação de 0.37 no aparelho ainda não confirmada**.
- PR #88 publicador Web pontual Cloudflare. **Primeira tentativa** run `39304482362` falhou sem iniciar deploy por ausência do segredo `CLOUDFLARE_API_TOKEN`; depois de configurá-lo com segurança pelo responsável, segunda tentativa **SUCCESS**, mas a página mostrou erro de ausência das variáveis públicas Supabase ao tentar login.
- **Falha Web de produção de fato observada:** build direto via GitHub/Wrapper sem `VITE_SUPABASE_URL` e `VITE_SUPABASE_PUBLISHABLE_KEY` incorporadas no bundle Vite. Não foi falha de senha do usuário e não houve evidência de dano no banco.
- PR #89 hotfix `.github/workflows/publish-approved-web-20261019.yml`: recupera somente as duas variáveis **públicas** do ambiente de produção do projeto Cloudflare `neves-estoque`, valida e injeta **antes** da compilação, exige sua presença no JS gerado antes do upload e não imprime valores. Merge funcional `d0b490b138faa0fad0321e431745560622895f50`.
- **Web CI #250 de PR: SUCCESS. Web CI #251** (run `39316512271`) pós-merge: **SUCCESS**. **Cloudflare deploy** run `39316512155`: **SUCCESS**; logs confirmam configuração de build presente e `Deployment complete` em `https://b94d1893.neves-estoque.pages.dev`. Site oficial: **https://neves-estoque.pages.dev**. Login do site **confirmado pelo responsável** após hotfix; outros fluxos não foram testados nesta versão.
- Deploy automático geral do Cloudflare continua desativado; workflow foi publicação pontual; Netlify segue como alternativa histórica com builds pausados.

## Supabase / proteção dos dados

- Projeto operacional **Neves Estoque**, `bwostqcdwfueapfemrwn`, `ACTIVE_HEALTHY` em consulta somente leitura de 19/10/2026.
- **43 migrations aplicadas**, última `20261007111815_editable_initial_price_before_priced_entry`. Alterações destas PRs **não exigiram nem aplicaram migration**. Nenhuma operação de escrita/limpeza/migração de dados foi executada nesta consolidação.
- **Não repetir contagens antigas** (0 entradas/conferências, etc.): números do checkpoint 256 são um retrato de 07/10, não validação dos registros reais atuais.
- Credenciais/tokens não ficam no código, Docs, Notion ou histórico. Segredo Cloudflare configurado diretamente no GitHub pelo responsável; apenas seu *nome* de configuração pode aparecer nesta documentação.

## O que foi testado e o que falta

- **CI APROVADA** cobre compilação, typecheck, lint, testes automáticos configurados e assinatura/publicação como evidenciado nas runs.
- **TESTADO PELO RESPONSÁVEL:** login Web normal após correção de configuração de produção (19/10). Android 0.36 / Preço inicial foi testado anteriormente em 07/10.
- **A VERIFICAR / TESTE REAL PENDENTE:** instalar 0.37 via atualizador Android; no Galaxy fazer conferência, girar/desgirar preservando quantidades, `Guardar e sair`/retomar, pular item sem converter vazio em zero, salvar e tratar alertas/background, conferir correção de histórico; na Web testar rascunho/autosalvamento, impressão A4 e Entrada com preço total/Tab/Enter, cache/PWA/offline e mudança de usuário. A publicação ocorreu com teste operacional adiado por decisão explícita do responsável. Não afirmar que todos os bugs desapareceram apenas porque CI passou.
- Preservar pendências locais e rascunhos; não limpar dados da PWA/browser/aplicativo por conveniência e não reenviar automaticamente ao reconectar.

## Pendências e próximo passo

1. **Próxima utilização real:** acompanhar e registrar o retorno do responsável sobre as correções (sem exigir uma bateria imediata de testes).
2. Se houver regressão, conferir versão/build e registros locais, buscar causa em fonte real; isolar correção, CI e nova publicação somente com autorização.
3. **A VERIFICAR:** `SYSTEM_VERSION` Android × Web, barra inferior Android, offline/PWA em aparelho, comportamento de pendências ao trocar usuário, segurança/backup e fluxo permanente de release/deploy (os jobs de 19/10 foram pontuais).
4. Após este checkpoint, `docs/contexto/`, Contexto Mestre Google Docs, Notion e BabyCat-OS devem apontar ao novo estado; histórico do 256 e 10/10 permanece preservado.

**Modelo/esforço de IA:** GPT-6 no registro atual; esforço detalhado de cada etapa anterior não informado com confiabilidade. **Rollback:** APK anterior `android-v0.36.0-alpha01` preservado como Release histórica (não instalar como downgrade sem avaliar pendências); Web: usar recursos de reversão de deployment do Cloudflare mediante análise e aprovação, sem presumir rollback realizado.
