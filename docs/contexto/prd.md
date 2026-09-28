# PRD — Neves Estoque

> Estado documentado em 28/09/2026. Este arquivo descreve o produto atual; o estado operacional imediato fica em `task.md`.

## Produto

O **Neves Estoque** é o aplicativo interno de controle de estoque da Panificadora Neves. No V1 ele atende um único estoque: **Panificadora Neves de Nordestina**. O sistema existe para substituir gradualmente a experiência operacional da planilha por uma solução própria, mais segura e confortável no celular e no computador, sem perder regras e histórico importantes do controle atual.

## Problema que resolve

- reduzir dependência de fórmulas e estruturas frágeis de planilha;
- permitir consulta rápida e confiável do estoque atual;
- registrar Entradas e Conferências físicas preservando histórico;
- inferir diferenças/consumo entre checkpoints físicos sem exigir saída individual rotineira;
- apoiar compras, preços, relatórios e acompanhamento do valor do estoque;
- funcionar bem em Web/PWA e Android, inclusive com comportamento Offline controlado.

## Usuários

- uso interno da Panificadora Neves;
- acesso fechado a usuários previamente autorizados;
- Elias e Eychila possuem privilégios administrativos equivalentes no modelo atual;
- autenticação não substitui autorização: `app_users` decide se a conta pode usar o sistema.

## Regra central do estoque

**Última Conferência Física válida + Entradas posteriores = Estoque Atual do sistema.**

Não há registro rotineiro de saídas individuais no V1. Entre dois checkpoints físicos, a diferença pode ser inferida por:

`checkpoint físico anterior + entradas do período - próximo checkpoint físico`

Esse valor pode representar consumo, perda, diferença de contagem, entrada esquecida ou outro ajuste; o sistema não deve classificá-lo automaticamente como consumo produtivo.

## Fluxos principais atuais

1. autenticar e validar autorização interna;
2. abrir sempre na Home;
3. consultar Estoque Atual e detalhes do Produto;
4. registrar Entrada de mercadoria efetivamente recebida;
5. realizar Conferência física por Categoria ou por Produto;
6. manter Produtos, Categorias e Fornecedores;
7. consultar históricos, Relatórios e Alertas;
8. simular necessidade de Compras;
9. recuperar itens na Lixeira dentro da janela prevista ou executar exclusão definitiva quando aplicável;
10. no Android, consultar dados Offline e preparar pendências locais de Entrada/Conferência; o envio só ocorre depois de reconectar e confirmar conscientemente.

## Funcionalidades existentes

### Web/PWA — `0.26.0`

- autenticação Google e contas secundárias suportadas pelo backend atual;
- Home com seis módulos: Estoque Atual, Conferência, Entrada, Compras, Produtos e Fornecedores;
- Estoque Atual, Produtos/Categorias, Fornecedores, Entradas, Conferências, Compras, Relatórios, Lixeira e Alertas implementados;
- PWA com cache/consulta Offline e pendências locais; edição de pendência Web ainda não alcançou paridade com o Android;
- impressão/PDF de relatórios e folhas A4 de Conferência;
- navegação responsiva para celular e desktop.

### Android nativo — `0.21.0-alpha01` (versionCode 21)

- Kotlin + Jetpack Compose;
- autenticação/autorização, Home e navegação nativa;
- Estoque Atual, Produtos/Categorias, Fornecedores, Entradas, Conferências, Lixeira, Relatórios, Compras, Alertas e Offline Android implementados no escopo consolidado;
- Offline Android com cache de leitura, pendências locais de Entrada/Conferência, edição/exclusão/manutenção e envio confirmado com idempotência;
- testes automáticos/CI aprovados no baseline funcional `10ec3ab...`; testes funcionais, Offline e de performance em aparelho continuam **A VERIFICAR**.

## Regras funcionais essenciais

- Entrada representa mercadoria **realmente recebida**;
- fornecedor é obrigatório na Entrada;
- quantidade de item de Entrada deve ser maior que zero;
- preço em branco significa preço não informado; `R$ 0,00` significa bonificação;
- total de Entrada é calculado pelos itens;
- Conferência aceita quantidade zero e proíbe negativa;
- Entrada e Conferência usam cabeçalho + itens, com uma observação opcional no cabeçalho;
- Produto usa UUID interno e invisível; nome duplicado é bloqueado ignorando caixa e espaços excedentes;
- Unidade é a unidade de apresentação/controle; kg, g, L e ml permanecem no nome/apresentação no V1;
- preço atual deriva do último preço real válido de Entrada; bonificação ou preço ausente não substituem o preço de referência;
- relação Produto ↔ Fornecedor nasce das Entradas reais; não existe fornecedor principal fixo;
- Compras é simulação temporária, não pedido persistido;
- exclusões preservam o histórico necessário; Lixeira segue as regras de restauração/exclusão definitiva vigentes.

## Offline

- dados locais nunca concedem permissão;
- nada é enviado automaticamente ao reconectar;
- pendência local usa ID temporário; UUID oficial nasce no envio confirmado;
- envio confirmado usa `idempotency_key` para evitar duplicação;
- Android armazena cache, pendências e registro de acesso em arquivos JSON internos separados;
- o comportamento das pendências ao trocar de usuário no mesmo aparelho está **A DEFINIR** e não deve ser alterado sem decisão explícita.

## Escopo e limites do V1

- um único estoque: Panificadora Neves de Nordestina;
- Web/PWA permanece como acesso sem instalação;
- Android nativo é plataforma aprovada e em evolução;
- Windows instalado fica para etapa futura; iOS somente se surgir necessidade real;
- sem servidor próprio no V1: backend gerenciado pelo Supabase;
- banco atual contém dados de teste até preparação explícita para go-live;
- não migrar nem apagar dados reais sem confirmação de Elias;
- CI aprovada não significa teste funcional em aparelho.

## Próximas entregas já aprovadas

- **Bloco Android 11 de 13** — experiência, fluidez e performance; sub-blocos 11.1 e 11.2 concluídos, próximo = 11.3;
- **Bloco Android 12 de 13** — atualização/distribuição do APK e acabamentos finais;
- **Bloco Android 13 de 13** — fechamento para produção e bateria final de validações.

## Fora de escopo imediato

- reconstruir partes funcionais sem necessidade;
- envio automático de pendências Offline;
- migração real/go-live sem confirmação;
- preparar distribuição de APK antes do Bloco 12;
- transformar ideias antigas ou históricas em requisito atual sem validação.
