# Neves Estoque — Android

Aplicativo Android nativo do Neves Estoque, mantido no mesmo repositório da Web.

## Contexto antes de alterar

Leia primeiro os arquivos aplicáveis em `../docs/contexto/`, especialmente `architecture.md`, `rules.md`, `design.md`, `task.md` e `memory.md`.

## Estado atual — 29/09/2026

- applicationId: `com.babycatbe.nevesestoque`;
- Android: `0.34.0-alpha01` (versionCode 34);
- Sistema/Web: `0.26.0`;
- linha de trabalho atual: `feat/android-17-product-maintenance-ux`;
- PR #75: DRAFT / OPEN / NÃO INTEGRADO;
- HEAD atual: consultar o GitHub;
- último commit funcional: `e7928813f757c45f19454f0fd9ac460b76c1aacd` (Android 34 / Modo Escuro);
- Android CI #122: SUCCESS no estado funcional Android 34;
- Kotlin + Jetpack Compose + Material 3;
- Supabase Kotlin 3.8.0 + Ktor Android 3.5.1.

## Estado funcional recente

- Android 31: abertura imediata por acesso local previamente validado, revalidação em segundo plano, preload somente de Estoque Atual/Produtos, teclado numérico próprio e fluxo rápido de Conferência/Entrada. Elias testou os fluxos exercitados com resultado positivo.
- Android 32: campo “Adicionar Produto” da Nova Entrada mantém campo e sugestões visíveis acima do teclado. **APROVADO EM APARELHO**.
- Android 33: datas visíveis em `DD/MM/AAAA`, valor interno ISO, ícone de calendário e seletor visual Material/Android. **APROVADO EM APARELHO**.
- Android 34: **Modo Escuro** — Configurações → Aparência com seletor Sol/Lua, troca imediata com transição de paleta de ~300 ms, preferência local por aparelho e paleta grafite quente aprovada. **TESTADO / APROVADO EM APARELHO**, incluindo persistência após fechar/reabrir.

## Módulos

O Android possui implementação dos módulos de Estoque Atual, Produtos/Categorias, Fornecedores, Entradas, Conferências, Lixeira, Relatórios, Compras, Alertas e Offline Android, além de autenticação/autorização, atualização interna e navegação nativa.

## Offline

- cache de leitura, pendências e registro de acesso ficam em arquivos JSON internos privados e separados;
- pendências locais suportam Entrada/Conferência no escopo aprovado;
- nada é enviado automaticamente ao reconectar;
- envio exige confirmação consciente, revalidação online e idempotência;
- limpar cache não pode apagar pendências;
- comportamento ao trocar de usuário no mesmo aparelho permanece `A DEFINIR`.

## Atualização e distribuição

- o app possui atualizador interno baseado em GitHub Releases públicas;
- o APK oficial é validado por SHA-256 antes da instalação;
- a atualização real 0.24.0-alpha01 → 0.25.0-alpha01 foi instalada por cima e aprovada em aparelho;
- o fluxo de atualização para v28 também funcionou;
- existe workflow de publicação de Release, mas merge/publicação continuam ações conscientes e não automáticas;
- PRs #69–#75 continuam empilhados e abertos; presença na branch atual não equivale a integração em main/develop.

## Configuração

Crie `android/local.properties` a partir de `android/local.properties.example` ou forneça as configurações públicas do Supabase por Gradle/environment.

Nenhuma credencial privada deve ser commitada.

## Estoque atual

O Android não mantém saldo oficial próprio. Ele consome `public.stock_current`, que permanece autoridade para a posição derivada do estoque.

## Validação

```bash
gradle -p android --no-daemon :app:lintDebug :app:testDebugUnitTest :app:assembleDebug
```

CI aprovada não substitui teste funcional em aparelho. Consulte `../docs/contexto/task.md` para saber exatamente o que já foi validado em dispositivo e o próximo bloco.
