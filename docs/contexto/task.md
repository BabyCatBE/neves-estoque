# Estado operacional — Neves Estoque

> Atualizado em 28/09/2026. Este arquivo é operacional e deve mudar com frequência.

## Fase atual

`IMPLEMENTAÇÃO INCREMENTAL / TESTES`

## Referência técnica segura

- branch operacional: `fix/audit-device-id`;
- Web: `0.26.0`;
- Android: `0.21.0-alpha01` (versionCode 21);
- último commit **funcional** seguro antes da implantação destes documentos: `10ec3abfc76185fa75afdc47a3b1cdac8d676433`;
- Android CI #62: `SUCCESS`;
- Supabase: `ACTIVE_HEALTHY`, 42 migrations aplicadas no checkpoint 11.2, última `20260927171450_trash_permanent_delete_v1`;
- testes Offline, performance e aparelho: `A VERIFICAR`.

Alterações exclusivamente documentais podem mover o HEAD do repositório sem mudar esse baseline funcional.

## Objetivo atual

Continuar o **BLOCO ANDROID 11 DE 13 — EXPERIÊNCIA, FLUIDEZ E PERFORMANCE ANDROID**.

### Concluído dentro do Bloco 11

- **11.1 — Diagnóstico:** CONCLUÍDO como auditoria; sem alteração funcional;
- **11.2 — Fundação de performance e durabilidade do Offline:** IMPLEMENTADO / CI APROVADA.

### Próximo trabalho

Definir e executar **somente o SUB-BLOCO 11.3**, a partir dos achados ainda abertos do diagnóstico 11.1, sem reabrir a arquitetura Offline já fortalecida.

## Backlog aberto do diagnóstico para 11.3+

- recargas concorrentes em Alertas/Compras;
- cargas repetidas e cálculos repetidos em Compras;
- consultas mais amplas do que o necessário;
- processamento pesado fora da UI quando aplicável;
- listas/recomposições e renderização eficiente;
- estados visuais e mudanças bruscas de layout;
- animações/transições coerentes e rápidas;
- feedback háptico quando fizer sentido;
- validar fluidez, frames, rolagem e resposta a toque em aparelho real.

## Pendências conhecidas

- **A DEFINIR:** comportamento das pendências Offline ao trocar de usuário no mesmo aparelho; não alterar sem decisão de Elias;
- **BUG confirmado Web:** Conferências na Lixeira Web ainda não filtram `permanently_deleted_at`; Android já filtra;
- testes funcionais Android em aparelho permanecem `A VERIFICAR`;
- teste manual Offline agrupado permanece `A VERIFICAR`;
- preparação/atualização do APK pertence ao Bloco 12, não ao Bloco 11.

## Ordem lógica aprovada

1. concluir Bloco 11;
2. Bloco 12 de 13 — atualização/distribuição do APK + acabamentos finais;
3. Bloco 13 de 13 — fechamento para produção e bateria final;
4. somente após validação completa e confirmação, realizar a transição para dados reais/go-live.

## Restrições enquanto este estado estiver vigente

- não preparar APK de distribuição ainda;
- não apagar dados de teste;
- não iniciar migração real;
- não mudar a regra de troca de usuário das pendências;
- não tratar CI como teste funcional em aparelho;
- não corrigir a pendência Web da Lixeira dentro de um bloco Android sem decisão de escopo.

## Critério para atualizar este arquivo

Atualizar quando mudar fase, objetivo, prioridade, próxima tarefa, bloqueio, pendência operacional ou conclusão relevante. Histórico detalhado deve ficar no Contexto Mestre/checkpoints; decisões duradouras ficam em `memory.md`.
