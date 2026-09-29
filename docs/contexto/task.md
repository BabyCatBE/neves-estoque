# Estado operacional — Neves Estoque

> Atualizado em 28/09/2026. Este arquivo é operacional e deve mudar com frequência.

## Fase atual

`IMPLEMENTAÇÃO INCREMENTAL / TESTES`

## Último ponto funcional integrado e comprovado

- branch operacional: `fix/audit-device-id`;
- último HEAD funcional integrado: `10ec3abfc76185fa75afdc47a3b1cdac8d676433`;
- Web: `0.26.0`;
- Android: `0.21.0-alpha01` (versionCode 21);
- Android CI funcional #62: `SUCCESS`;
- sub-blocos 11.1 e 11.2: concluídos;
- testes Offline, performance e aparelho: `A VERIFICAR`.

## Branch operacional após a implantação documental

A branch `fix/audit-device-id` avançou após o ponto funcional seguro apenas por documentação. A linhagem documental inclui a criação/manutenção dos seis arquivos em `docs/contexto/` e a atualização de `README.md` e `android/README.md`.

Como este próprio arquivo faz parte dessa linhagem, o SHA atual da branch deve ser consultado diretamente no GitHub; não é duplicado aqui para evitar uma referência que se torna obsoleta a cada atualização de `task.md`.

Nenhum código funcional, migration ou dado foi alterado por essa implantação documental.

## Trabalho atual posterior ao ponto seguro — 11.3

O **SUB-BLOCO 11.3 — Integridade das leituras históricas e paginação** foi implementado e auditado em branch isolada, mas **ainda NÃO está integrado** na branch operacional.

- branch isolada: `feat/android-11-3-history-pagination`;
- HEAD: `9aa118c16e5f6f91d1a0bd6482b5f5e892792c29`;
- estado: `IMPLEMENTADO / CI APROVADA / AUDITORIA INDEPENDENTE APROVADA`;
- CI Web final: run `36473536301` — SUCCESS;
- Android CI final: run `36472525419` — SUCCESS;
- Supabase: sem alteração e sem migration nova;
- testes manuais Web/Android: `A VERIFICAR`.

### O que o 11.3 fez

- corrigiu risco de truncamento silencioso de leituras históricas em Compras;
- Web e Android passaram, nessa branch isolada, a usar paginação por cursor/chave primária `id`;
- leitura termina somente quando chega página vazia;
- falhas intermediárias não devolvem histórico parcial;
- regra de negócio de Compras foi preservada;
- Relatórios não foram alterados nesse sub-bloco e permanecem como ponto de atenção separado.

## Divergência de branches após a implantação documental

O mini-checkpoint 11.3 foi criado quando `fix/audit-device-id` ainda estava em `10ec3ab...` e registrou que um fast-forward seria possível enquanto isso permanecesse verdadeiro.

Depois disso, a implantação dos arquivos de contexto avançou a branch operacional para `a7478baa...` com commits exclusivamente documentais. Portanto:

- a hipótese de fast-forward direto do 11.3 **não vale mais**;
- não reescrever histórico nem forçar branch;
- preservar tanto os commits documentais quanto os três commits funcionais do 11.3;
- o método exato de integração deve ser verificado no momento da integração;
- **não integrar o 11.3 sem autorização de Elias**.

## Próximo passo

1. aguardar autorização de Elias para integrar o 11.3 na branch operacional;
2. no momento da integração, reconciliar as duas linhas de commits sem perder `docs/contexto/` nem o trabalho funcional do 11.3;
3. rodar novamente as CIs aplicáveis na branch operacional;
4. só então decidir conscientemente o tratamento do bug latente de paginação de Relatórios e o próximo sub-bloco do Bloco 11.

## Pendências conhecidas

- **A DEFINIR:** comportamento das pendências Offline ao trocar de usuário no mesmo aparelho;
- **BUG confirmado Web:** Conferências na Lixeira Web ainda não filtram `permanently_deleted_at`; Android já filtra;
- paginação de Relatórios: problema latente identificado no trabalho do 11.3, fora do escopo daquele sub-bloco; precisa de decisão/execução posterior;
- testes funcionais Android em aparelho: `A VERIFICAR`;
- teste manual Offline agrupado: `A VERIFICAR`;
- preparação/atualização do APK pertence ao Bloco 12, não ao Bloco 11.

## Ordem macro aprovada

1. concluir Bloco 11 de 13;
2. Bloco 12 de 13 — atualização/distribuição do APK + acabamentos finais;
3. Bloco 13 de 13 — fechamento para produção e bateria final;
4. somente após validação completa e confirmação, realizar transição para dados reais/go-live.

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
