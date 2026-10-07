# Estado operacional — Neves Estoque

> Atualizado em 30/09/2026 após consolidação documental oficial.

## Fase atual

`BLOCO PREÇO INICIAL EDITÁVEL — IMPLEMENTADO / CI E RELEASE ANDROID 0.36.0-alpha01 / AGUARDANDO TESTE EM APARELHO`

## Bloco atual — Preço inicial editável antes da primeira Entrada com preço

Regra: Preço inicial pode ser adicionado, corrigido ou removido na edição de Produto enquanto não existir Entrada ativa do Produto com `unit_price > 0`. Preço em branco (NULL), bonificação (0) e Entrada excluída não bloqueiam. Depois de Entrada com preço real, a correção é feita na Entrada.

- backend: migration `20261007111815_editable_initial_price_before_priced_entry` (aplicada e verificada no Supabase Neves Estoque); nova RPC `update_product_details_with_initial_price` (nome + categoria + preço, atômica). Valida na gravação com o Produto travado; valor inalterado é no-op. `initial_price_at` preservado na correção; na primeira definição usa `created_at` do Produto; remoção zera par preço/data. Auditoria pela trigger de linha de `products`. `update_product_details` antigo permanece para clientes instalados.
- Web: campo na edição de Produto (`ProductDetailPage`); somente leitura com orientação quando bloqueado.
- Android: `NevesNumericField` + teclado Neves na tela Editar Produto; somente leitura quando bloqueado.
- versão Android: `0.36.0-alpha01` / versionCode 36 (`SYSTEM_VERSION` mantido em `0.26.0`, ainda A VERIFICAR).
- observação: a última Release oficial anterior era `android-v0.28.0-alpha01`; versões 29–35 não tinham Release.
- teste em aparelho: **PENDENTE**.

## Último estado funcional seguro

- CHECKPOINT 254 — Teclado numérico global + fluxo de Compras.
- `develop` funcional: `c0e2ad41a8ad5d64db700453a28826eaf03b8594`.
- PR #78: MERGED / CLOSED.
- origem: `fix/numeric-keyboard-purchases`.
- HEAD final da origem: `9ab9f98b1748d46c192cca87922a8850af333db0`.
- Web: `0.26.1`.
- Android: `0.35.0-alpha01` / versionCode 35.
- `main`: `e0c15eb45fda2f6c68f58ed6f4ccdd24feb20635`, sem promoção deste bloco.

**Importante:** commits documentais posteriores podem avançar `develop` sem alterar o último estado funcional. Sempre conferir o HEAD atual e distinguir documentação de mudança funcional.

## CI comprovada do CHECKPOINT 254

Pós-merge:
- CI #152 / run `36761735944`: SUCCESS;
- Android CI #135 / run `36761735901`: SUCCESS.

Pré-merge/reconciliação:
- Android CI #134: SUCCESS;
- CI #151: SUCCESS;
- Purchases UI #5: SUCCESS.

## Teste em aparelho

No escopo registrado por Elias:
- quantidade em Compras seleciona automaticamente;
- teclado numérico/Próximo funciona no cenário testado;
- + e − ficaram visualmente coerentes com o checkbox.

Estado do escopo: **TESTADO / APROVADO EM APARELHO**.

Não interpretar isso como teste de todos os cenários do aplicativo.

## Supabase — fotografia da consolidação

Projeto **Neves Estoque**, verificado em 30/09/2026:
- ACTIVE_HEALTHY;
- 42 migrations;
- 13 Categorias;
- 146 Produtos;
- 0 Fornecedores;
- 0 Entradas;
- 0 itens de Entrada;
- 0 Conferências;
- 0 itens de Conferência;
- 3 app_users;
- 17 devices;
- 10 tabelas públicas principais com RLS ativa.

Estado dos dados: **CATÁLOGO REAL PREPARADO**.

Não resetar, limpar ou substituir catálogo sem nova autorização explícita.

## Consolidação documental oficial

A antiga versão do Contexto Mestre tinha 373 páginas e 783.722 caracteres.

A reorganização:
- preservou o conteúdo original integralmente;
- confirmou igualdade literal por SHA-256;
- criou ponto de entrada operacional compacto;
- manteve histórico e evidências separados;
- promoveu o novo Contexto Mestre após confirmação explícita de Elias.

O arquivo antigo permanece preservado como arquivo histórico.

## Pendências vigentes

- próximo bloco funcional: **A DEFINIR**;
- `SYSTEM_VERSION` Android = `0.26.0` enquanto Web/package = `0.26.1`: A VERIFICAR;
- barra inferior Android: A VERIFICAR;
- pendências Offline ao trocar usuário: A DEFINIR;
- smoke test autenticado Web 0.26.1: opcional para classificação manual;
- testes Offline finais/agrupados: pendentes;
- proteção contra senhas vazadas no Supabase Auth: A DEFINIR;
- splash claro Android 12+ no tema Escuro: limitação conhecida;
- imagem órfã de teste no Storage: eventual limpeza futura, sem autorização automática;
- backup/monitoramento e demais itens pré-produção: revisar quando a fase exigir.

## Próximo passo

**A DEFINIR conforme prioridade real e uso do sistema.**

Não inventar novo bloco só porque a consolidação documental terminou.

Não mover `main`, publicar PROD, criar Release ou realizar operação destrutiva no Supabase sem autorização explícita.

## Retomada por nova IA/chat

1. ler o Contexto Mestre oficial;
2. ler este `task.md`;
3. conferir `develop` atual no GitHub;
4. distinguir último HEAD funcional de commits documentais;
5. ler os arquivos especializados aplicáveis;
6. consultar Supabase somente quando a tarefa depender do ambiente;
7. aprofundar no histórico apenas quando necessário.
