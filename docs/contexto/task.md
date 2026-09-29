# Estado operacional — Neves Estoque

> Atualizado em 29/09/2026. Este arquivo é operacional e deve refletir o trabalho vigente; detalhes históricos ficam nos checkpoints do Contexto Mestre e em `memory.md`.

## Fase atual

`IMPLEMENTAÇÃO INCREMENTAL / TESTES / PR EMPILHADO AINDA NÃO INTEGRADO À LINHA PRINCIPAL`

## Último estado seguro atual

- linha de trabalho: `feat/android-17-product-maintenance-ux`;
- PR #75: DRAFT / OPEN / NÃO INTEGRADO; base `feat/android-launcher-icon`;
- HEAD atual: `85ef0f11c7eda95a6b12eafe325eaec4328dfc72` — somente documentação de consolidação;
- último commit funcional: `e2b980dc4d412a91e528a88efcdd75898eb65f35`;
- Web/System: `0.26.0`;
- Android: `0.33.0-alpha01` / versionCode 33;
- Android CI #118: SUCCESS no último commit funcional;
- Android CI #119: SUCCESS no HEAD documental;
- nenhuma alteração de Web, Supabase, migrations ou dados nos refinamentos Android 31–33.

## O que já está comprovado em aparelho

- Android 30: visual e fluidez dos cards aprovados.
- Android 31: Elias informou que os fluxos exercitados de abertura/fluidez e operação de Conferência/Entrada funcionaram corretamente.
- Android 32: correção da busca de Produto na Nova Entrada, mantendo campo e sugestões acima do teclado, **APROVADA EM APARELHO**.
- Android 33: calendário visual e formato `DD/MM/AAAA` ainda **A VERIFICAR EM APARELHO**.

## Trabalho vigente e próximo bloco

1. instalar/testar o APK 33 e validar o seletor de calendário;
2. corrigir somente achados reais, se houver;
3. iniciar o bloco de **Modo Escuro Android**, já APROVADO e ainda NÃO IMPLEMENTADO;
4. preservar o tema claro atual;
5. Configurações deve oferecer seletor manual Claro/Escuro, com troca imediata e persistência local por aparelho.

Paleta Dark aprovada:
- `#181614` background;
- `#211E1B` cards/surface;
- `#2A2622` secondary surface;
- `#302B27` elevated;
- `#F4F1ED` texto principal;
- `#B8B0A7` texto secundário;
- vermelho Neves como destaque.

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

- Android 33 calendário visual: `A VERIFICAR EM APARELHO`;
- Dark Mode: `APROVADO / NÃO IMPLEMENTADO`;
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
