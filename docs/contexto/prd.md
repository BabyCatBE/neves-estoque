# PRD — Neves Estoque

> Estado consolidado em 30/09/2026. Último estado funcional seguro: CHECKPOINT 254. O estado operacional imediato fica em `task.md`; histórico completo e decisões substituídas ficam no Contexto Mestre/arquivo histórico.

## Produto

O **Neves Estoque** é o aplicativo interno de controle de estoque da Panificadora Neves. No V1 atende um único estoque: **Panificadora Neves de Nordestina**.

Objetivos:
- substituir gradualmente a experiência operacional da planilha;
- manter histórico e integridade do estoque;
- operar bem em Web/PWA e Android;
- permitir consulta, Entrada, Conferência, Compras, cadastros, Relatórios, Alertas, Lixeira e Offline controlado.

## Plataformas atuais

- **Web/PWA:** versão `0.26.1`.
- **Android nativo:** `0.35.0-alpha01`, versionCode 35.
- **Backend:** Supabase/PostgreSQL único para as plataformas.
- Windows instalado fica para evolução futura.
- iOS somente se surgir necessidade real.

## Regra central do estoque

**Última Conferência Física válida + Entradas posteriores = Estoque Atual do sistema.**

Não há registro rotineiro de saídas individuais no V1.

Entre dois checkpoints físicos:

`checkpoint físico anterior + entradas do período - próximo checkpoint físico`

O resultado pode incluir consumo, perda, diferença de contagem, Entrada esquecida ou outro ajuste. O sistema não deve classificá-lo automaticamente como consumo produtivo.

## Usuários e autorização

- uso interno da Panificadora Neves;
- acesso fechado a usuários autorizados;
- autenticação não substitui autorização;
- `app_users` decide acesso ao sistema;
- Google é usado para contas mestre;
- contas secundárias podem usar usuário/senha no escopo já implementado.

## Fluxos principais

1. autenticar e validar autorização;
2. abrir na Home;
3. consultar Estoque Atual e Produto;
4. registrar Entrada de mercadoria efetivamente recebida;
5. realizar Conferência por Categoria ou Produto;
6. manter Produtos, Categorias e Fornecedores;
7. consultar históricos, Relatórios e Alertas;
8. simular necessidade de Compras;
9. usar Lixeira dentro das regras vigentes;
10. no Android, consultar cache Offline e preparar pendências locais de Entrada/Conferência;
11. enviar pendências somente após reconexão e confirmação consciente.

## Regras funcionais essenciais

- Entrada representa mercadoria **realmente recebida**;
- fornecedor é obrigatório na Entrada;
- quantidade de Entrada deve ser maior que zero;
- preço em branco = não informado;
- preço zero = bonificação;
- total de Entrada é calculado pelos itens;
- Conferência aceita zero e proíbe negativo;
- Entrada e Conferência usam cabeçalho + itens;
- Produto usa UUID interno e invisível;
- nome duplicado é bloqueado ignorando caixa e espaços excedentes;
- preço atual deriva do último preço real válido de Entrada; quando ainda não existe Entrada ativa com preço real, pode usar o Preço inicial como referência;
- Preço inicial pode ser adicionado, corrigido ou removido na edição do Produto enquanto não houver Entrada ativa com `unit_price > 0`; preço em branco, bonificação (0) e Entrada excluída não bloqueiam;
- depois da primeira Entrada ativa com preço real, correção histórica de preço é feita na Entrada, não no cadastro do Produto;
- Produto ↔ Fornecedor nasce de Entradas reais;
- Compras é simulação temporária, não pedido persistido;
- exclusões devem preservar o histórico necessário;
- detalhes matemáticos de Compras, Relatórios, Lixeira e demais regras especializadas continuam nas fontes técnicas/históricas oficiais.

## Compras

O módulo pode operar por fornecedor, estoque ou categoria.

Diretrizes consolidadas:
- recomendações usam estoque, histórico, prazo, frequência e margem;
- histórico insuficiente deve ser explicitado;
- digitar quantidade válida maior que zero seleciona o Produto no fluxo atual;
- limpar, zerar ou invalidar a quantidade desmarca de forma coerente;
- Web e Android preservam paridade funcional quando aplicável.

## Offline

- dados locais nunca concedem permissão;
- cache e pendências permanecem separados;
- nada é enviado automaticamente ao reconectar;
- envio exige confirmação consciente e revalidação online;
- idempotência evita duplicação;
- Android mantém arquivos JSON internos privados para cache, pendências e acesso;
- comportamento das pendências ao trocar de usuário no mesmo aparelho continua **A DEFINIR**.

## UX operacional atual

- Home mantém os módulos principais;
- Android usa teclado numérico próprio Neves em **campos exclusivamente numéricos**;
- texto, datas e outros formatos usam entrada apropriada;
- datas visíveis no Android usam `DD/MM/AAAA`, com ISO internamente;
- Claro/Escuro é preferência local do aparelho;
- **barra de navegação inferior no Android: A VERIFICAR**. Há decisões históricas conflitantes e o app atual não a implementa; preservar a navegação atual até decisão explícita.

## Estado comprovado mais recente

CHECKPOINT 256 — 07/10/2026:
- Preço inicial editável antes da primeira Entrada ativa com preço real, com proteção no backend;
- migration `20261007111815_editable_initial_price_before_priced_entry` aplicada;
- Web `0.26.1`;
- Android `0.36.0-alpha01` / versionCode 36;
- CI #157 e Android CI #137: SUCCESS;
- Release oficial `android-v0.36.0-alpha01` publicada com APK assinado/checksum;
- fluxo testado e aprovado em aparelho por Elias;
- `main` e Web PROD não foram promovidos.

## Estado dos dados na consolidação documental

Supabase **Neves Estoque**, revalidado em 07/10/2026 após o teste em aparelho:
- 43 migrations aplicadas;
- 13 Categorias;
- 146 Produtos ativos;
- 9 Produtos com Preço inicial;
- 0 Fornecedores ativos;
- 0 Entradas ativas;
- 0 itens de Entrada ativos.

Os demais contadores históricos de 30/09/2026 devem ser revalidados quando forem necessários.

Estado: **CATÁLOGO REAL PREPARADO**.

O primeiro registro operacional real que Elias decidir manter muda o estado para **DADOS REAIS EM USO**. Não executar reset, limpeza, substituição de catálogo ou teste destrutivo sem autorização explícita.

## Pendências atuais

- próximo bloco funcional: **A DEFINIR**;
- smoke test autenticado Web 0.26.1: opcional para classificar esse escopo como TESTADO manualmente;
- testes Offline finais/agrupados continuam separados;
- pendências Offline ao trocar usuário: A DEFINIR;
- `SYSTEM_VERSION` Android ainda é `0.26.0` enquanto Web/package é `0.26.1`: A VERIFICAR;
- barra inferior Android: A VERIFICAR;
- proteção contra senhas vazadas no Supabase Auth: A DEFINIR;
- splash Android 12+ claro em tema Escuro: limitação conhecida não bloqueante.

## Limites

- não mover `main`, publicar PROD ou Release automaticamente;
- não migrar/apagar dados reais sem confirmação;
- CI aprovada não significa teste funcional em aparelho;
- histórico antigo não deve ser relido integralmente por formalidade; aprofundar seletivamente quando necessário.
