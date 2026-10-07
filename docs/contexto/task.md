# Estado operacional — Neves Estoque

> Atualizado em 07/10/2026 após consolidação do CHECKPOINT 256.

## Fase atual

`CHECKPOINT 256 — PREÇO INICIAL EDITÁVEL ANTES DA PRIMEIRA ENTRADA COM PREÇO — IMPLEMENTADO / CI APROVADA / RELEASE ANDROID PUBLICADA / TESTADO EM APARELHO / CONSOLIDADO`

## CHECKPOINT 256 — 07/10/2026

### Regra funcional consolidada

O Preço inicial pode ser adicionado, corrigido ou removido na edição de Produto enquanto não existir Entrada ativa desse Produto com `unit_price > 0`.

- Entrada com preço em branco (`NULL`) não bloqueia;
- bonificação (`0`) não bloqueia;
- Entrada excluída não bloqueia;
- depois da primeira Entrada ativa com preço real (> 0), a correção de preço deve ser feita na própria Entrada;
- vazio continua `NULL`; zero informado explicitamente continua zero;
- ao corrigir preço inicial existente, `initial_price_at` é preservado;
- ao definir o primeiro preço inicial em Produto já existente, a referência usa `created_at`;
- ao remover, `initial_price` e `initial_price_at` ficam coerentemente vazios;
- a validação final é do backend/banco, protegendo tela desatualizada e gravação concorrente.

### Implementação

- Backend: migration `20261007111815_editable_initial_price_before_priced_entry`; nova RPC `update_product_details_with_initial_price`; RPC antiga mantida para compatibilidade.
- Web: edição de Produto permite Preço inicial enquanto elegível; quando bloqueado, mostra somente leitura com orientação.
- Android: edição de Produto usa `NevesNumericField` e teclado numérico próprio Neves; quando bloqueado, mostra somente leitura.
- Web/package: `0.26.1`.
- Android: `0.36.0-alpha01` / versionCode 36.
- `SYSTEM_VERSION` Android continua `0.26.0`: **A VERIFICAR**; não alterar apenas para alinhar documentação.

### GitHub / CI / Release

- branch integrada: `develop`;
- commit funcional + ajuste de identidade CI: `e146c50aee843e9616fa024a9c0d07e75a6e70c3`;
- HEAD técnico/documental antes desta consolidação: `c4006f19ef133d51b495d8b2a49309b40fb257f9`;
- CI #157 / run `37614753146`: SUCCESS;
- Android CI #137 / run `37614753154`: SUCCESS;
- Android CI #136 falhou somente porque o workflow ainda esperava 0.35/35; expectativa atualizada para 0.36/36 e a rodada seguinte passou;
- Release oficial publicada: `android-v0.36.0-alpha01`;
- tag resolve para `c4006f19ef133d51b495d8b2a49309b40fb257f9`;
- APK oficial: `neves-estoque-android.apk`;
- checksum oficial: `SHA256SUMS`;
- SHA-256 do APK publicado: `7ad35e2558f1bad5baac2b7293c4542ef2f796eec2c4cb4d84c54619f8a78432`;
- Release pública, não draft e não prerelease.

### Supabase / dados

Projeto **Neves Estoque**, revalidado após o teste em aparelho em 07/10/2026:

- 43 migrations aplicadas;
- 13 Categorias;
- 146 Produtos ativos;
- 9 Produtos com Preço inicial;
- 0 Fornecedores ativos;
- 0 Entradas ativas;
- 0 itens de Entrada ativos.

A migration foi aplicada e os cenários técnicos de banco foram validados sem deixar dados artificiais. Após o teste em aparelho e o início do preenchimento das referências históricas por Elias, 9 Produtos possuem Preço inicial; continuam 0 Entradas ativas. O estado permanece **CATÁLOGO REAL PREPARADO**.

Não criar Entrada artificial nem preencher preços em massa só para testar. Elias preencherá referências iniciais reais conforme necessidade e fontes históricas.

### Teste em aparelho

**TESTADO / APROVADO EM APARELHO por Elias em 07/10/2026.**

Confirmado no Android:

- atualizador interno encontrou/instalou a Release 0.36.0-alpha01;
- edição de Produto sem Entrada com preço exibiu Preço inicial;
- salvar refletiu o valor como preço atual;
- nova edição/correção do Preço inicial funcionou enquanto não existe Entrada com preço real.

Não interpretar isso como teste manual de todos os cenários Web/Android.

### Limitação de Release descoberta

O workflow `.github/workflows/android-release.yml` está em `develop`, mas não em `main`, branch padrão. Por isso o disparo manual automatizado ficou indisponível neste bloco.

A Release 0.36.0-alpha01 foi publicada manualmente usando exclusivamente o APK assinado e validado pela Android CI #137, sem recompilar.

Pendência futura: estruturar um caminho oficial de Release que possa ser disparado sem promover o código do app para `main`, mediante bloco próprio e decisão consciente.

## Último estado funcional seguro

O CHECKPOINT 256 substitui o CHECKPOINT 254 como último estado funcional seguro consolidado.

Referência funcional principal: `e146c50aee843e9616fa024a9c0d07e75a6e70c3`.

Commits documentais posteriores podem avançar `develop` sem mudar o runtime. Sempre distinguir HEAD funcional de commits somente documentais.

## Pendências vigentes

- próximo bloco funcional: **A DEFINIR**;
- automatização correta do fluxo de Android Release sem depender de publicação manual: **A DEFINIR / bloco futuro**;
- `SYSTEM_VERSION` Android = `0.26.0` enquanto Web/package = `0.26.1`: **A VERIFICAR**;
- barra inferior Android: **A VERIFICAR**; preservar navegação atual;
- pendências Offline ao trocar usuário: **A DEFINIR**;
- smoke test autenticado Web 0.26.1: opcional para classificação manual;
- testes Offline finais/agrupados: pendentes;
- proteção contra senhas vazadas no Supabase Auth: **A DEFINIR**;
- splash claro Android 12+ no tema Escuro: limitação conhecida;
- imagem órfã de teste no Storage: eventual limpeza futura, sem autorização automática;
- backup/monitoramento e demais itens pré-produção: revisar quando a fase exigir.

## Próximo passo

**A DEFINIR conforme prioridade real e uso do sistema.**

Não inventar novo bloco apenas porque este foi consolidado.

Não mover `main`, publicar Web PROD, criar outra Release ou realizar operação destrutiva no Supabase sem autorização explícita.

## Retomada por nova IA/chat

1. ler este `task.md`;
2. ler os arquivos especializados aplicáveis;
3. conferir `develop` atual no GitHub;
4. distinguir último HEAD funcional de commits documentais;
5. consultar Supabase somente quando a tarefa depender do ambiente;
6. usar BabyCat OS / Drive / Notion conforme a camada de contexto necessária;
7. aprofundar no histórico apenas quando necessário.
