# Estado operacional — Neves Estoque

> **Consolidação de 26/10/2026 — CP265** (base técnica: CP258–CP264). Fonte de continuidade: Contexto Mestre operacional no Google Drive; detalhes e testes em `docs/contexto/checkpoints/`. Não reabrir o Contexto Mestre histórico como ponto de entrada.

## Fase e último estado funcional seguro

**IMPLEMENTADO / CI APROVADA / WEB PROD PUBLICADA / ANDROID RELEASE PUBLICADA / TESTADO EM APARELHO PARCIALMENTE / FLUXO DE PEDIDO COMPLETO A VERIFICAR.**

- **Web/PWA PROD:** versão **0.26.3**, publicada em https://neves-estoque.pages.dev pelo workflow **40400225641 SUCCESS**; Cloudflare Pages `neves-estoque` production, deployment `6136621e-0d2b-4538-af2c-40a7804fcf36`, origem `b58c3ae2182b5a764f40881ca3800a0c8b2148bc`. CI de código **40399328785 SUCCESS**. Teste manual do pedido em navegador autenticado **A VERIFICAR**.
- **Android RELEASE oficial:** **0.40.0-alpha01 / versionCode 40**, tag [android-v0.40.0-alpha01](https://github.com/BabyCatBE/neves-estoque/releases/tag/android-v0.40.0-alpha01), `releases/latest` verificado, APK e `SHA256SUMS` publicados. Android CI **40408834376 SUCCESS**, publicador **40411239982 SUCCESS**; APK assinado originalmente na CI sem recompilar, SHA/certificado validados. Usuário relatou **TESTADO em aparelho**: reparo/edição de pendência com produto ausente, correção visual do ripple e botão de marcar produtos para compra. **Envio final e efeito no banco não auditados neste relato.**
- **Último código funcional Android integrado:** PR #95 / merge `d8ce748cce30f71f251e5bb8236423e78ccbdd46`; código testado da PR `34951d66799ae2c34162853b491f3e148c7e5178` (mesma árvore). **Último código funcional Web publicado:** PR #93 / merge `0450b1135efa8b77f6c402019840c603f129b796` (origem `525fc807475fa0bf54d42c8ccd0d5ca6cc82ce6f`). `develop` possui commits adicionais de workflows/checkpoints/documentação que **não são nova funcionalidade**.
- `main` preservada no commit `e0c15eb45fda2f6c68f58ed6f4ccdd24feb20635`; Web PROD é publicada de `develop` por procedimento consciente. Não pressupor promoção automática de `main`.

## Funções entregues em CP258–CP264

- Conferência por categoria mostra **Estoque de referência** discretamente junto à unidade. Marcação independente da quantidade física com **botão de carrinho arredondado no canto superior direito** (Android e Web). No Android, o preenchimento e efeito de toque usam a mesma forma arredondada (0.40).
- Seleção de produtos de diversas categorias **persistida localmente por usuário/dispositivo**, não sincronizada entre Web e Android. Ao fim da lista **Conferência → Fazer conferência**, aparece `Fazer pedido dos selecionados (N)` quando houver itens marcados. O pedido é **simulação temporária**, separa quantidade de compra da contagem, permite copiar/compartilhar, não cria Entrada ou movimentação.
- Android 0.40: pendência local com catálogo desatualizado compara **IDs do servidor (sem cache)**; editor mostra produtos novos, exige contagem real digitada (inclusive zero explícito), mantém itens anteriores e `localId`/`idempotencyKey`; bloqueia envio se faltarem/duplicarem/moverem produtos. Sem sincronização automática.
- **TESTADO pelo usuário:** reparo da pendência, ripple do carrinho e seleção de produtos. **A VERIFICAR:** ação ao fim das categorias → abertura do Pedido → quantidades independentes → Copiar/Compartilhar (Android e Web). Não é preciso gerar outra Conferência oficial só para isso: marcar itens e voltar sem salvar contagem; o botão fica no fim da lista de categorias, não abre automaticamente após salvar.

## Supabase real — conferência em 26/10/2026 (somente leitura)

Projeto `bwostqcdwfueapfemrwn` (Neves Estoque): **43 migrations aplicadas**, última `20261007111815_editable_initial_price_before_priced_entry`; **13 Categorias ativas, 152 Produtos ativos, 18 Entradas ativas e 69 Conferências ativas**. Esses números são fotografia datada; não inferir que os registros sejam testes nem apagar dados. A natureza real/de teste de cada registro não foi auditada. **Nenhuma migration ou modificação de dados feita nos CP258–264/consolidação.**

Caso real investigado: categoria **MANTEIGAS, MARGARINAS E REQUEIJOES** passou a 11 produtos quando `Delicia 250g` foi cadastrado em 21/10. Pendência antiga tinha dez; 0.40 implementa reparo seguro. Comprovação de envio oficial ao banco dessa pendência **A VERIFICAR**, não presumir.

## Pendências e próximo passo

1. **Prioridade de teste:** fluxo Pedido dos selecionados ao fim da lista de categorias, inclusive múltiplas categorias, edição de quantidade de compra, Copiar/Compartilhar. Não criar Conferência duplicada: a seleção local é independente do salvamento físico.
2. Observar uso real da recuperação de pendências: conferir que as quantidades existentes ficaram intactas e, se houve envio conscientemente confirmado, verificar histórico/estoque no backend antes de marcar TESTADO E2E.
3. **Web:** confirmar visualmente o botão e o fluxo da lista; avaliar paridade da correção de catálogo antigo no editor de pendências Web (não implementada neste bloco).
4. Monitorar PWA/offline, troca de usuário com pendências, backup, fluxo sustentável de publicação. `SYSTEM_VERSION` Android × Web/package e barra inferior Android seguem **A VERIFICAR**; não mudar navegação nem código para alinhar documentação.
5. Respeitar confirmação explícita antes de nova Release/PROD/`main`, banco ou operações destrutivas.

## Retomada

1. Contexto Mestre operacional Google Drive + **este `task.md`**; **CP265** (consolidação) e [CP264](checkpoints/2026-10-26-cp264-retorno-teste-em-aparelho-android-040.md) para última evidência de teste. CP258–263 guardam implementações, falhas e publicações.
2. Conferir GitHub `develop`/CI/Release e Cloudflare para estado de runtime; Supabase **ao vivo** para operações/dados/migrations.
3. Separar último estado publicado, commits documentais posteriores e trabalho novo; não depender deste chat nem tratar CI como teste em aparelho.
