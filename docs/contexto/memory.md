# Memória técnica — Neves Estoque

Este arquivo registra apenas decisões e aprendizados que continuam úteis. O histórico completo permanece no Contexto Mestre.

## 2026-09-21 — modelo central e arquitetura do V1

**Decisão:** adotar o modelo `última Conferência válida + Entradas posteriores = Estoque Atual`, sem saída individual rotineira; Web/PWA em React/TypeScript/Vite e backend gerenciado Supabase/PostgreSQL.

**Motivo:** preservar histórico relacional, reduzir fragilidade da planilha e permitir evolução Web/mobile sem servidor próprio.

**Impacto:** Estoque Atual é derivado de fatos históricos; clientes não devem manter um saldo oficial independente.

## 2026-09-21 — identidade e autorização

**Decisão:** IDs oficiais são UUIDs internos; autenticação e autorização são separadas; `app_users` decide acesso ao sistema e ações carregam identidade de dispositivo quando aplicável.

**Impacto:** possuir sessão autenticada não basta, por si só, para autorizar uma operação.

## 2026-09-24 — Conferência e Estoque Atual

**Decisão consolidada:** Conferências são fatos históricos com cabeçalho + itens; Conferência aceita zero e proíbe negativo. `stock_current` permanece a autoridade derivada para a posição atual.

**Aprendizado:** correções históricas precisam recalcular dependências sem criar duplicidade indevida.

## 2026-09-27 — evolução do domínio

**Decisões implementadas:** autenticação secundária, Conferência unitária por Produto, conversão de unidade de Produto, mescla de Produtos e ciclo completo de Lixeira/exclusão definitiva.

**Impacto:** novos clientes devem reutilizar a semântica e os fluxos já existentes em vez de recriar regras localmente.

## 2026-09-28 — roteiro Android expandido

**Decisão:** o roteiro Android foi ampliado de 12 para 13 blocos para criar um bloco específico de experiência, fluidez e performance.

**Impacto:** referências novas usam 13 blocos; referências históricas antigas como `9 de 12` permanecem históricas e não devem ser reescritas.

## 2026-09-28 — arquitetura Offline Android

**Decisão:** usar arquivos JSON internos privados, separados em cache reconstruível, pendências e registro de acesso; Room/SQLite não foi adotado.

**Decisão:** nada é enviado automaticamente ao reconectar; envio exige confirmação consciente, revalidação online e idempotência.

**Motivo:** manter o Offline simples, auditável e compatível com o modelo aprovado sem transformar dados locais em fonte de autoridade.

**Ponto em aberto:** pendências ao trocar de usuário no mesmo aparelho continuam `A DEFINIR`.

## 2026-09-28 — diagnóstico 11.1

**Achados:** I/O/serialização Offline poderiam bloquear UI; havia riscos de durabilidade em gravação/exclusão; Compras repetia cargas/cálculos; Alertas/Compras permitiam recargas concorrentes; consultas/listas/recomposições tinham oportunidades de otimização.

**Resultado:** nenhum bug crítico, vulnerabilidade crítica ou perda de dados foi confirmado; performance real continuou dependente de aparelho.

## 2026-09-28 — sub-bloco 11.2

**Decisão/implementação:** fortalecer persistência Offline antes de animações/otimizações cosméticas.

**Mudanças:** I/O fora da thread de UI, escrita temporária + substituição segura, sincronização física da escrita, save/delete com resultado real, preservação de arquivo inválido e serialização das mutações concorrentes da mesma pendência.

**Resultado:** IMPLEMENTADO / CI APROVADA no baseline `10ec3ab...`; aparelho ainda `A VERIFICAR`.

## Divergência histórica de migrations — preservar

**Fato:** as três migrations iniciais têm timestamps diferentes:

- Supabase aplicado: `20260922001400`, `20260922001543`, `20260922001654`;
- Git: `20260921201500`, `20260921202500`, `20260921203500`.

Os nomes lógicos correspondem. O Contexto Mestre já registra essa divergência como conhecida.

**Regra:** Supabase é autoridade do histórico efetivamente aplicado; não renomear retroativamente migrations já aplicadas.

## 2026-09-28 — método dos seis arquivos de contexto

**Decisão:** manter `prd.md`, `architecture.md`, `rules.md`, `design.md`, `task.md` e `memory.md` em `docs/contexto/`, com leitura obrigatória antes de mudanças técnicas relevantes.

**Motivo:** reduzir reconstrução de contexto entre chats/IAs e aproximar o contexto técnico do código real.

**Impacto:** mudanças relevantes devem atualizar o arquivo correspondente; o Contexto Mestre continua sendo a fonte consolidada de continuidade geral.

## Aprendizado documental recorrente

README e documentação auxiliar podem ficar atrás do código. Em 28/09/2026, antes de criar estes arquivos, `README.md` e `android/README.md` continham snapshots antigos enquanto Contexto Mestre, GitHub e Supabase já estavam muito à frente.

**Regra:** nunca usar uma única documentação auxiliar como prova de estado; cruzar com código, CI e banco e atualizar docs quando a divergência puder induzir o próximo agente ao erro.


## 2026-09-28 — sub-bloco 11.3 em branch isolada

**Estado:** `IMPLEMENTADO / CI APROVADA / AUDITORIA INDEPENDENTE APROVADA`, porém **NÃO INTEGRADO** na branch operacional.

**Branch/HEAD:** `feat/android-11-3-history-pagination` em `9aa118c16e5f6f91d1a0bd6482b5f5e892792c29`.

**Decisão técnica implementada nessa branch:** leituras históricas usadas por Compras passam a usar paginação por cursor/chave primária `id`, terminando somente com página vazia e falhando sem devolver resultado parcial quando a leitura não puder ser garantida.

**Validação:** CI Web run `36473536301` SUCCESS e Android CI run `36472525419` SUCCESS. Sem migration e sem alteração no Supabase.

**Ponto separado:** Relatórios não foram alterados no 11.3; um risco latente de paginação foi identificado e deve ser tratado conscientemente em etapa posterior.

## 2026-09-28 — concorrência entre trabalho funcional e documentação

O mini-checkpoint 11.3 registrou que a integração poderia ser fast-forward enquanto `fix/audit-device-id` permanecesse em `10ec3ab...`. Em seguida, a implantação dos seis arquivos de contexto adicionou commits exclusivamente documentais à branch operacional.

**Aprendizado:** em trabalho com múltiplas IAs/branches, distinguir sempre:
- último HEAD funcional integrado e comprovado;
- HEAD atual da branch operacional, que pode conter apenas documentação;
- trabalho funcional posterior em branch isolada ainda não integrado.

**Regra:** não forçar nem reescrever histórico para recuperar um fast-forward. Preservar as duas linhas de trabalho e definir o método de integração somente quando Elias autorizar a integração.


## Divergência intencional com o sistema legado — regra do Estoque Atual

O Contexto Mestre e a planilha legados registram como regra do sistema antigo: **a última Conferência física, sozinha, é a autoridade do Estoque Atual**, e Entradas não devem simplesmente somar ao saldo exibido.

O aplicativo novo possui uma decisão posterior e aprovada no Contexto Mestre atual: **última Conferência Física válida + Entradas posteriores = Estoque Atual do sistema**.

**Interpretação correta:** não é inconsistência a ser “corrigida” automaticamente. A planilha legado continua sendo autoridade para o comportamento do sistema antigo e para dados/migração; o Contexto Mestre atual é autoridade para a regra funcional vigente do aplicativo novo.

**Regra:** não alterar o legado para fazê-lo combinar com o aplicativo e não ressuscitar a regra antiga dentro do app sem nova decisão explícita.
