# Design atual — Neves Estoque

> Este documento registra os padrões visuais observados no código atual. Não propõe redesign.

## Princípios

- interface clara e operacional;
- branco/cinza muito claro como base;
- vermelho Neves como cor principal;
- textos escuros e contraste alto;
- cantos arredondados;
- ações primárias preenchidas em vermelho;
- ações secundárias brancas com borda/texto vermelho;
- celular/Android priorizam cartões, toque confortável e leitura rápida;
- desktop aproveita largura, tabelas e teclado;
- animação deve ajudar percepção sem atrasar o trabalho.

## Web/PWA

### Tipografia

`src/styles/index.css` usa:

`Inter, ui-sans-serif, system-ui, -apple-system, BlinkMacSystemFont, "Segoe UI", sans-serif`

Não há escala tipográfica central separada; os tamanhos são aplicados com utilitários Tailwind nos componentes/telas.

### Cores recorrentes verificadas

- fundo geral: `#F7F7F8` / `zinc-50`;
- texto principal: `#18181B` / `zinc-900`;
- vermelho principal: `red-700`, equivalente ao vermelho base `#B91C1C`;
- hover primário: `red-800`;
- foco: tons `red-600` / `red-500`;
- header: `zinc-950` com texto branco;
- destaque âmbar usado no header/alertas;
- seleção de texto: fundo `#FECACA`, texto `#18181B`;
- estado Offline: faixa âmbar clara.

### Shell Web

`AppShell.tsx` define o padrão estrutural:

- header sticky escuro;
- logo Neves;
- marca `Neves • Estoque` + título da tela;
- botão Voltar quando aplicável;
- atalho Home fora da Home;
- breadcrumbs em fluxos internos;
- faixa decorativa vermelha/âmbar/branca;
- conteúdo central `max-w-6xl`, `px-4` no mobile e `sm:px-6`, com `py-6`;
- faixa Offline abaixo do header quando sem internet.

### Botões

`Button.tsx` possui variantes `primary`, `secondary`, `danger`, `ghost` e tamanhos `sm`/`md`.

Padrões atuais:

- `rounded-xl`;
- altura mínima de 36 px ou 44 px conforme o tamanho;
- peso semibold;
- feedback de toque com pequena redução de escala;
- foco visível;
- loading com spinner;
- estado disabled com contraste reduzido.

### Campos

`TextField.tsx` usa:

- label pequena/média;
- `rounded-xl`;
- borda cinza e fundo branco;
- altura mínima 44 px;
- foco vermelho;
- placeholder cinza;
- erro com borda/mensagem vermelha;
- atributos de acessibilidade para erro e descrição.

### Movimento e acessibilidade

- transições curtas nos componentes base;
- `prefers-reduced-motion: reduce` reduz animações e smooth scroll;
- campos possuem margem de scroll para facilitar uso com teclado/mobile;
- foco deve permanecer visível.

## Android

### Tema atual

`NevesTheme.kt` usa Material 3 com esquema claro:

- `primary`: `#B91C1C`;
- `onPrimary`: branco;
- `primaryContainer`: `#FFE4E4`;
- `onPrimaryContainer`: `#5C0000`;
- `background`: `#F7F7F8`;
- `onBackground`: `#18181B`;
- `surface`: branco;
- `onSurface`: `#18181B`;
- `surfaceVariant`: `#F1F1F3`;
- `onSurfaceVariant`: `#5F5F66`;
- `error`: `#B3261E`.

Não existe tipografia customizada em arquivo próprio no estado atual; o app usa a tipografia padrão do Material 3 com pesos ajustados localmente.

### Home Android

`HomeScreen.kt` estabelece padrões atuais:

- `Scaffold` com fundo do tema;
- header em `Surface` com elevação;
- marca circular vermelha com `N`;
- título `Controle de Estoque`, usuário/role e ações Alertas/Sair;
- grid fixo de 2 colunas;
- padding de conteúdo 16 dp;
- espaçamento do grid 14 dp;
- cards com raio 22 dp, elevação 2 dp e proporção aproximada 1.08;
- marca do módulo em círculo `primaryContainer` de 56 dp;
- versão Android/Sistema no rodapé;
- Voltar na Home abre confirmação para sair.

### Alertas

Âmbar `#B45309` é usado como destaque quando existem alertas.

### Navegação e estados

- destino inicial autenticado = Home;
- Voltar em telas internas retorna um nível;
- telas usam Material 3 e componentes Compose nativos;
- Offline possui banner dedicado;
- estados de carregamento/erro devem permanecer compatíveis com os padrões do módulo.

## Responsividade e consistência

- Web: `max-w-6xl`, breakpoints Tailwind e variações responsivas já são padrão;
- Android: layouts Compose devem privilegiar ergonomia de toque e listas eficientes;
- não criar nova paleta ou linguagem visual por módulo;
- antes de criar componente novo, verificar os componentes compartilhados Web e os padrões Compose existentes.

## Trabalho visual em andamento

O Bloco Android 11 de 13 está revisando experiência, fluidez e performance. Qualquer mudança de animações, háptico, transições ou padrões visuais feita nesse bloco deve atualizar este arquivo quando se tornar padrão permanente.

## Android — correção visual do PR #75 (29/09/2026)

Padrão aprovado por Elias após teste do APK 29: cards operacionais brancos, borda avermelhada discreta, cantos arredondados e faixa vermelha de 4 dp à esquerda. `NevesContentCard` centraliza as variantes de conteúdo e clicável, mantendo a semântica Material. A faixa usa a altura medida do card completo, inclusive durante expansão/recolhimento; não pertence ao cabeçalho.

Aplicado a Estoque (categoria, fornecedor, A–Z e resumo), Produtos/Categorias, Fornecedores, Entrada, Conferência, Compras, Home, Relatórios, Alertas, pendências Offline, Lixeira e card de versão em Configurações. Chips, campos, badges, menus, ilustrações e avisos semânticos conservam o tratamento próprio. Compras preserva o checkbox e distingue a seleção por borda vermelha mais forte sobre fundo branco.

Implementação consolidada documentalmente no CHECKPOINT 245, branch `feat/android-17-product-maintenance-ux`, Android `0.30.0-alpha01 / 30`; Android CI #111 SUCCESS no commit funcional `2a09844ff64aa2592f9bebcf274ddcb610fa77eb`. Em 29/09/2026, Elias instalou o APK 30 no aparelho e aprovou o resultado visual e a fluidez do conjunto. A bateria funcional completa não foi declarada concluída. Web não alterada. Contexto Mestre e Notion sincronizados; isso não representa integração em main nem publicação de Release.
