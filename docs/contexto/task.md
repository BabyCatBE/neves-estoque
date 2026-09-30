# Estado operacional — Neves Estoque

> Atualizado em 30/09/2026 (Bloco 253). Este arquivo é operacional e deve refletir o trabalho vigente; detalhes históricos ficam nos checkpoints do Contexto Mestre e em `memory.md`.

## Fase atual

`PARIDADE WEB DE HISTÓRICOS CORRIGIDA / GO-LIVE ANDROID MANTIDO` (Bloco 253, 30/09/2026).

## Estado real

- Último checkpoint consolidado: **CHECKPOINT 253 — PARIDADE WEB: LEITURAS HISTÓRICAS SEM TRUNCAMENTO**.
- Estado seguro atual: `develop@7d9cee677353ecbb20d22d45c1467da2a727513c`; `main` permanece intocada em `e0c15eb45fda2f6c68f58ed6f4ccdd24feb20635`.
- PR #77 (`fix/web-history-pagination` → `develop`): **MERGED** em 30/09/2026.
- Web/System: `0.26.1`.
- Android: `0.35.0-alpha01` / versionCode 35 — TESTADO / APROVADO EM APARELHO por Elias no escopo de go-live do CHECKPOINT 252.
- CI final do PR #77: **CI #147 / run 36692488491 — SUCCESS**; typecheck, lint, 32 arquivos / 185 testes e build Web/PWA aprovados.
- Deploy Preview do PR #77 no Netlify: SUCCESS.
- O conector desta consolidação não expôs um workflow de `push` separado para o merge commit; não registrar CI pós-merge adicional sem evidência.
- Supabase *Neves Estoque*: 42 migrations; estado verificado nesta consolidação: 13 Categorias, 146 Produtos, 0 Fornecedores, 0 Entradas, 0 itens de Entrada, 0 Conferências e 0 itens de Conferência; 3 app_users e 16 devices.
- Nenhuma migration, schema, RLS, policy, RPC ou dado operacional foi alterado pelo Bloco 253.
- Estado dos dados: `CATÁLOGO REAL PREPARADO`. Quando Elias mantiver o primeiro registro operacional real, passa a `DADOS REAIS EM USO`; a partir daí é proibido resetar, limpar, substituir catálogo ou rodar teste destrutivo sem nova autorização explícita.

## Correções consolidadas

### Android 35 — CHECKPOINT 252

- Cache do período de testes invalidado uma única vez (`OfflineStore.CACHE_GENERATION = 2`), somente `cache/`; pendências e acesso preservados.
- Leituras históricas críticas passaram a filtrar/paginar no servidor.
- Teste físico de go-live no celular: **APROVADO**.
- PR #76: MERGED em `develop`.

### Web 0.26.1 — CHECKPOINT 253

Foi eliminado o risco de históricos silenciosamente incompletos quando o PostgREST/Supabase devolve menos linhas do que o cliente solicita.

Protegidos:
- resumo de Última Conferência / Conferida hoje;
- histórico de Conferências por Categoria e do mesmo dia;
- detalhe de Conferência com muitos itens;
- conflito Entrada × Conferência, inclusive no fluxo de envio consciente de pendências Offline;
- histórico de Entradas e seus itens;
- detalhe do Produto: histórico de preço e consumo;
- Lixeira de Produtos, Categorias, Fornecedores, Entradas e Conferências.

Infraestrutura:
- `fetchAllByIdKeyset` preservado;
- helpers adicionados para filtros `in (...)` em blocos e paginação por `id` dentro dos blocos;
- erro em qualquer página/bloco falha a leitura inteira em vez de devolver histórico parcial;
- testes de regressão simulam teto de linhas menor que o pedido do cliente.

Falha intermediária relevante:
- CI #144 falhou em um teste novo porque o cliente PostgREST falso não possuía `.gte()`;
- a limitação da infraestrutura de teste foi corrigida;
- CI final #147 passou integralmente.

## Pendências conhecidas

- Web 0.26.1: correção lógica/automatizada **CI APROVADA**, mas smoke test funcional manual autenticado no navegador DEV ainda pode ser executado se Elias quiser classificar esse escopo especificamente como TESTADO manualmente.
- Supabase Auth: proteção contra senhas vazadas desativada (aviso pré-existente do advisor) — A DEFINIR.
- Splash Android 12+ claro com tema Escuro: LIMITAÇÃO CONHECIDA não bloqueante.
- Pendências Offline ao trocar de usuário no mesmo aparelho: A DEFINIR.
- Testes Offline agrupados/finais anteriormente deferidos continuam pendência separada.
- Se a Web for aberta em navegador usado nos testes antigos, conferir cache/IndexedDB e Pendências locais antes de enviar qualquer operação antiga.

## Próximo passo

O Bloco 253 está tecnicamente concluído e integrado.

Próximo passo funcional: **A DEFINIR conforme a prioridade vigente e o uso real do sistema**. Um smoke test manual da Web 0.26.1 em DEV pode ser feito antes de classificá-la como TESTADA manualmente, mas não bloqueia o Android 35 já aprovado para uso.

Não mover `main`, publicar PROD ou criar Release automaticamente. Não executar reset/limpeza/substituição do catálogo ou teste destrutivo no Supabase sem nova autorização explícita de Elias.

## Critério para atualizar este arquivo

Atualizar quando mudar fase, objetivo, prioridade, próxima tarefa, bloqueio, branch de trabalho, pendência operacional ou estado de integração. Histórico detalhado deve ficar no Contexto Mestre/checkpoints; decisões duradouras ficam em `memory.md`.

---

# HISTÓRICO — superado pelos CHECKPOINTS 251/252/253

> As seções abaixo descrevem estados anteriores (branch `feat/android-17-product-maintenance-ux`, PR #75 não integrado etc.). Estão preservadas apenas como histórico e **não** representam o estado atual.

## (histórico) Fase anterior

`IMPLEMENTAÇÃO INCREMENTAL / TESTES / PR EMPILHADO AINDA NÃO INTEGRADO À LINHA PRINCIPAL`

## Último estado seguro atual

- linha de trabalho: `feat/android-17-product-maintenance-ux`;
- PR #75: DRAFT / OPEN / NÃO INTEGRADO; base `feat/android-launcher-icon`;
- HEAD atual: consultar o GitHub; commits documentais posteriores não alteram o estado funcional;
- último commit funcional: `e7928813f757c45f19454f0fd9ac460b76c1aacd` — Android 34 / Modo Escuro;
- Web/System: `0.26.0`;
- Android: `0.34.0-alpha01` / versionCode 34;
- Android CI #122: SUCCESS no último commit funcional;
- nenhuma alteração de Web, Supabase, migrations ou dados nos refinamentos Android 31–34.

## O que já está comprovado em aparelho

- Android 30: visual e fluidez dos cards aprovados.
- Android 31: Elias informou que os fluxos exercitados de abertura/fluidez e operação de Conferência/Entrada funcionaram corretamente.
- Android 32: correção da busca de Produto na Nova Entrada, mantendo campo e sugestões acima do teclado, **APROVADA EM APARELHO**.
- Android 33: calendário visual e formato `DD/MM/AAAA` **APROVADOS EM APARELHO** por Elias.
- Android 34: Modo Escuro **TESTADO / APROVADO EM APARELHO** — visual, transição Claro↔Escuro e persistência após fechar/reabrir confirmados por Elias; CI #122 SUCCESS.

## Trabalho vigente e próximo bloco

- CHECKPOINT 248 fechado no escopo funcional testado: Modo Escuro Android 34 e calendário visual do Android 33 aprovados em aparelho.
- Próximo bloco funcional: **A DEFINIR** a partir da prioridade vigente do projeto e do GitHub real; não inventar nova prioridade só porque o checkpoint atual terminou.
- Limitação conhecida não bloqueante: splash do sistema Android 12+ pode permanecer claro antes do primeiro frame do app.

Referência do Modo Escuro: `design.md` (seção Android — Modo Escuro) e `architecture.md` (Tema / Aparência Android).

## Cadeia de PRs Android

A linha atual é empilhada e ainda não foi integrada à linha principal:

- PR #69 — Bloco 12 / release preparation;
- PR #70 — Bloco 13 / production readiness;
- PR #71 — Android 14 / UX;
- PR #72 — Android 15 / in-app updater;
- PR #73 — Android 16 / release automation;
- PR #74 — launcher icon;
- PR #75 — manutenção de Produto, cards e refinamentos Android 31–33.

Todos permanecem OPEN/DRAFT. Não fechar, fundir ou rebasear silenciosamente.

## Correção de continuidade sobre o Bloco 11

O estado antigo que dizia que 11.3 estava “NÃO INTEGRADO” ficou obsoleto.

- Elias autorizou a integração dos sub-blocos 11.3–11.7;
- PR #67 foi MERGED em `fix/audit-device-id` em 28/09/2026;
- PR temporário #68 validou o HEAD integrado por CI e foi fechado sem merge;
- portanto o Bloco 11 funcional foi integrado antes da cadeia dos Blocos 12+.

Referências antigas abaixo devem ser lidas como histórico do momento em que foram escritas, não como estado atual.

## Pendências conhecidas

- tela de abertura do sistema (splash Android 12+) segue o tema claro do manifesto mesmo com Escuro salvo: `LIMITAÇÃO CONHECIDA / A VERIFICAR EM APARELHO`;
- comportamento das pendências Offline ao trocar de usuário no mesmo aparelho: `A DEFINIR`;
- PRs empilhados #69–#75 ainda não integrados à linha principal;
- CI aprovada não substitui teste funcional em aparelho.

## Critério para atualizar este arquivo

Atualizar quando mudar fase, objetivo, prioridade, próxima tarefa, bloqueio, branch de trabalho, pendência operacional ou estado de integração. Histórico detalhado deve ficar no Contexto Mestre/checkpoints; decisões duradouras ficam em `memory.md`.

## Continuidade Android — correção visual após teste do PR #75 (29/09/2026)

- Baseline deste bloco: `e2d46d8f1e84a948675d93bff34a0c5e1c707e43`, Android `0.29.0-alpha01 / 29`, Android CI #110 SUCCESS.
- Trabalho atual: mesma branch `feat/android-17-product-maintenance-ux`, mesmo PR #75 DRAFT (base `feat/android-launcher-icon`). Android `0.30.0-alpha01 / 30`; Web/System `0.26.0` intacto.
- Correção: `NevesContentCard` branco com borda sutil e faixa vermelha por toda a altura, inclusive categorias expandidas e A–Z; varredura dos cards operacionais. Estado de Compras permanece distinto por checkbox/borda. Sem alteração de lógica de domínio, navegação ou persistência.
- Validação local: `git diff --check` e comparação de escopo; Gradle/SDK/emulador indisponíveis. Android CI #111 / run `36606323893`: SUCCESS, com lint, testes unitários, builds debug/release e verificação de identidade/certificado/checksum do APK signed.
- Commit funcional consolidado: `2a09844ff64aa2592f9bebcf274ddcb610fa77eb`. Artifact signed: `neves-estoque-android-signed-0.30.0-alpha01-61242324520c69b547925e9c65db1fe13a8699d2`. CI não equivale a teste funcional em aparelho.
- Teste em aparelho: APK 30 instalado por Elias e **visual/fluidez APROVADOS** em 29/09/2026. A bateria funcional completa do roteiro continua A VERIFICAR; não inferir que todos os fluxos foram testados apenas pela aprovação visual.
- Contexto Mestre e Notion sincronizados no CHECKPOINT 245. Sem merge em main, Release, mudança Web, Supabase/migrations/dados. Próximo passo: continuar a validação funcional do APK 30 e corrigir apenas achados reais.


## Continuidade Android — CHECKPOINT 246: abertura imediata, preload e fluxo operacional rápido (29/09/2026)

- Branch: `feat/android-17-product-maintenance-ux`; PR #75 DRAFT / OPEN / NÃO INTEGRADO.
- Base do bloco: `485840e4bf974b2b45bdad69b2809eadc03cc4dd`.
- Commit principal: `d3e4ef9568da3358746f798347a332ef0337b5aa`; ajuste final: `e204e06be0bf17db1f625ff9223430503024dd12`.
- Android `0.31.0-alpha01 / versionCode 31`; Web/System `0.26.0`.
- Implementado: abertura pelo acesso local previamente validado com revalidação em segundo plano; preload somente de Estoque Atual e Produtos; snapshot local + refresh oficial; teclado numérico próprio; foco/rolagem em Conferência e Entrada; −/+ somente em Conferência.
- Fluxos: Conferência categoria = Data → Responsável → quantidades → Observação; produto único = Responsável → Quantidade → Observação; Entrada normal = Fornecedor → Data → Produto → Quantidade → Preço → busca; Entrada por Produto = Fornecedor → Data → Quantidade → Preço → Observação.
- Observação da Nova Entrada foi movida para depois dos itens. Corrigir Conferência usa teclado próprio com −/+; Editar Entrada usa teclado próprio sem −/+.
- Enter nas buscas de Fornecedor/Produto seleciona automaticamente apenas se houver exatamente um resultado; comportamento A VERIFICAR em aparelho.
- CI #114 falhou em `LatestLoadTest.newerLoadCancelsOlderAndOlderResultNeverWins`; CI #115 / run `36617635332` passou integralmente. Registrar o teste antigo como risco de intermitência.
- Artifact signed: `neves-estoque-android-signed-0.31.0-alpha01-bf8d45b1ea6ac6ecaf63f52c760898f8b6049778`.
- Nenhuma alteração de Web, Supabase, migrations ou dados.
- Estado: IMPLEMENTADO / CI APROVADA / TESTE FUNCIONAL EM APARELHO A VERIFICAR.
- Próximo passo: instalar APK 31 e validar abertura imediata, preload, teclado, foco/rolagem, buscas e regressões online/offline. Corrigir apenas achados reais.


## Continuidade — CHECKPOINT 247: refinamentos Android 32/33 + Dark Mode aprovado (29/09/2026)

- Branch: `feat/android-17-product-maintenance-ux`; PR #75 DRAFT / OPEN / NÃO INTEGRADO.
- CHECKPOINT 246 / Android 31 foi testado por Elias no aparelho; os fluxos testados funcionaram corretamente. Não interpretar isso como validação de todos os cenários extremos ainda não executados.
- Android 32: `b9cb75b0e462db279e19fe3d564454bcfced1f5c`, CI #117 SUCCESS. Ajuste da busca de Produto na Nova Entrada para manter campo e sugestões visíveis acima do teclado. **APROVADO EM APARELHO** por Elias.
- Android 33: `e2b980dc4d412a91e528a88efcdd75898eb65f35`, CI #118 SUCCESS. Novo seletor visual de data e exibição `DD/MM/AAAA`, mantendo ISO internamente. **APARELHO A VERIFICAR**.
- Artifact signed Android 32: `neves-estoque-android-signed-0.32.0-alpha01-e457360660a0c5f0ac7e2bca29403a670b0a786d`, ID `11058501470`.
- Artifact signed Android 33: `neves-estoque-android-signed-0.33.0-alpha01-11a92345665de52ee7b16bde4827b54fdc8c3d33`, ID `11060590120`.
- Nenhuma alteração de Web, Supabase, migrations ou dados nesses refinamentos.
- Próximo bloco aprovado: **Modo Escuro Android**. Tema claro permanece inalterado; Configurações terá seletor Claro/Escuro com persistência local e troca imediata.
- Paleta Dark aprovada: background `#181614`; surface/card `#211E1B`; secondary surface `#2A2622`; elevated `#302B27`; primary text `#F4F1ED`; secondary text `#B8B0A7`; vermelho Neves como destaque.
- Antes de implementar Dark Mode, auditar cores fixas e componentes semânticos. Não tratar como simples inversão de branco/preto.
- Próximo passo imediato: Elias instalar/testar o APK 33; em seguida iniciar o bloco de Dark Mode sobre o último estado seguro.
