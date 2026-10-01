# Design atual — Neves Estoque

> Estado consolidado em 30/09/2026. Registra padrões visuais e de UX vigentes.

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

## Compras

- seleção por checkbox/quantidade permanece clara;
- quantidade válida maior que zero pode selecionar automaticamente;
- zero, vazio ou valor inválido desmarca;
- controles de quantidade usam destaque coerente com o vermelho institucional;
- Copiar/Compartilhar preservam o texto da lista.

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
