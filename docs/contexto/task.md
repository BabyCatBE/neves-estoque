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
- Validação local: `git diff --check` e comparação de escopo; Gradle/SDK/emulador indisponíveis. Lint/testes/build e identidade/certificado do APK signed devem ser verificados pela CI antes da entrega.
- HEAD final, resultado efetivo da CI e artifact signed: consultar o checkpoint corrente em https://github.com/BabyCatBE/neves-estoque/pull/75. CI não equivale a teste funcional.
- Pendente em aparelho: atualização sobre 29 sem desinstalar; categorias abertas/fechadas, fornecedores, A–Z, listas curtas/longas, nomes grandes, fonte ampliada, tela pequena, rolagem/safe area e retorno preservando pesquisa/grupos; demais cards e seleção de Compras.
- Sem merge em main, Release, mudança Web, Supabase/migrations/dados ou atualização do Contexto Mestre/Notion. Próximo passo: teste de Elias e correções, com consolidação somente quando solicitada.
