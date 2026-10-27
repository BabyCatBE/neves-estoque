# Design atual — Neves Estoque

> Consolidado em 26/10/2026 — CP265. Controle do carrinho/reparo testado parcialmente em Android; fluxo completo do pedido ainda A VERIFICAR.

## Princípios

- interface operacional e clara;
- vermelho Neves como identidade;
- alto contraste e leitura rápida;
- cantos arredondados;
- ações primárias destacadas;
- toque confortável no celular/Android;
- desktop aproveita largura e teclado;
- movimento deve ajudar percepção sem atrasar trabalho;
- preservar acessibilidade, foco e estados claros.

## Web/PWA

- base clara em cinza muito suave;
- texto escuro;
- vermelho institucional como destaque;
- header escuro;
- conteúdo responsivo;
- modo Offline indicado explicitamente;
- botões e campos com foco/erro/disabled claros;
- transições curtas e respeito a reduced motion.

## Android — Home

Estado atual do código:
- Home é destino inicial autenticado;
- header escuro com identidade Neves;
- grid principal em 2 colunas;
- cards operacionais com faixa vermelha;
- Alertas/Sair no header;
- Configurações e versão no rodapé.

## Barra inferior

**A VERIFICAR.**

Há decisão histórica sem barra inferior e outra posterior propondo barra para áreas mais usadas. O app atual não implementa barra inferior global.

Até decisão explícita de Elias:
- preservar a navegação atual;
- não adicionar nem remover barra inferior como efeito colateral de outra tarefa.

## Cards

Padrão aprovado:
- superfície coerente com o tema;
- borda discreta;
- cantos arredondados;
- faixa vermelha lateral;
- chips, badges, campos e avisos semânticos mantêm tratamento próprio.

## Tema Claro/Escuro

- Claro preserva identidade original;
- Escuro usa grafite/carvão quente;
- escolha manual em Configurações;
- preferência local ao aparelho;
- transição curta;
- vermelho Neves preservado como identidade;
- erro, alerta e sucesso têm semântica própria.

## Datas

- exibição: `DD/MM/AAAA`;
- armazenamento/API: ISO;
- seletor visual Material/Android;
- datas futuras continuam bloqueadas onde a regra exigir.

## Teclado numérico próprio

Regra global Android:
- campo exclusivamente numérico usa o teclado numérico Neves;
- texto, data e outros formatos usam entrada apropriada;
- Próximo/Concluir segue o fluxo lógico;
- manter campo ativo visível acima do teclado;
- o último campo não salva automaticamente.

## Controles de quantidade

Não existe regra global “somente Conferência”.

Estado atual:
- Conferência usa controles de menos/mais;
- Entrada não usa esses controles;
- Compras usa esses controles no fluxo atual;
- qualquer expansão a outros módulos exige avaliação consciente.

## UX de Conferências e Entradas — 19/10/2026

- Conferência Android permite `Próximo` com campo vazio para pular produto, mas salvar definitivamente exige a categoria completa.
- Voltar em conferência incompleta oferece `Guardar e sair`, `Continuar` e `Descartar`; a lista de categorias sinaliza rascunho e progresso. Rotação não pode apagar valores digitados.
- Salvar não mantém usuário preso em verificação remota após proteção local; acompanhamento de envio e alertas acontece em segundo plano.
- Entrada Web: Quantidade → Unitário → Total da nota; Tab passa para total, Enter mantém navegação anterior. Android: alternância discreta unitário/total e teclado numérico Neves. Unitário visível com duas casas sem perder a precisão da divisão.
- Estado: código IMPLEMENTADO/CI APROVADA; teste visual/funcional dos fluxos novos **A VERIFICAR**.

## Compras

- seleção por checkbox/quantidade permanece clara;
- quantidade válida maior que zero pode selecionar automaticamente;
- zero, vazio ou valor inválido desmarca;
- controles de quantidade usam destaque coerente com o vermelho institucional;
- Copiar/Compartilhar preservam o texto da lista.

## Conferência → pedido (Web e Android) — CP265

- Card da Conferência por Categoria exibe **Estoque de referência** discretamente ao lado da unidade.
- Ícone **único de carrinho** em botão pequeno no canto superior direito; **sem checkbox quadrado e sem texto “Pedir”**.
- Desmarcado: botão discreto e contornado; marcado: destaque vermelho, ícone contrastante. No Android, o **fundo, borda e animação/ripple** devem seguir o mesmo contorno arredondado; evitar preenchimento circular dentro do quadrado. Solução atual: `Surface(onClick)` com `RoundedCornerShape`.
- Alvo de toque Android de 48dp, estados semânticos; Web usa `aria-label`, `aria-pressed`, foco acessível e responsividade.
- `Fazer pedido dos selecionados (N)` aparece no fim da lista de categorias quando houver seleção, não automaticamente após salvar a Conferência. Pedido independe da gravação da contagem física.
- Editor de pendência Android com catálogo desatualizado destaca Produto **NOVO** e usa teclado numérico Neves, exigindo quantidade física real e nunca presumindo zero.

## Responsividade e acessibilidade

- Android prioriza ergonomia de toque;
- Web mantém responsividade;
- foco deve permanecer visível;
- ações operacionais importantes devem permanecer acessíveis;
- estados Offline, erro e sucesso devem ser reconhecíveis.

## Limitação conhecida

- splash Android 12+ pode aparecer claro antes do primeiro frame mesmo com tema Escuro salvo.

## Referências

- código atual em `src/` e `android/`;
- `rules.md`;
- Contexto Mestre oficial;
- histórico visual antigo para aprofundamento seletivo.
