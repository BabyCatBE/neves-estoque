# Neves Estoque — Android

Aplicativo Android nativo do Neves Estoque, mantido no mesmo repositório da Web.

## Contexto antes de alterar

Leia primeiro os arquivos aplicáveis em `../docs/contexto/`, especialmente `architecture.md`, `rules.md`, `design.md`, `task.md` e `memory.md`.

## Estado atual — 28/09/2026

- applicationId: `com.babycatbe.nevesestoque`;
- Android: `0.21.0-alpha01` (versionCode 21);
- Sistema/Web: `0.26.0`;
- branch operacional: `fix/audit-device-id`;
- Kotlin + Jetpack Compose + Material 3;
- Supabase Kotlin 3.8.0 + Ktor Android 3.5.1;
- Android CI #62: SUCCESS no último baseline funcional;
- testes funcionais, Offline e performance em aparelho: `A VERIFICAR`.

## Módulos

No estado consolidado atual, o Android possui implementação dos módulos de Estoque Atual, Produtos/Categorias, Fornecedores, Entradas, Conferências, Lixeira, Relatórios, Compras, Alertas e Offline Android, além de autenticação/autorização e navegação nativa.

O trabalho corrente está no **Bloco Android 11 de 13 — experiência, fluidez e performance**. Os sub-blocos 11.1 (diagnóstico) e 11.2 (fundação de performance/durabilidade do Offline) estão concluídos; o próximo passo é definir/executar 11.3.

## Offline

- cache de leitura, pendências e registro de acesso ficam em arquivos JSON internos privados e separados;
- pendências locais suportam Entrada/Conferência no escopo aprovado;
- nada é enviado automaticamente ao reconectar;
- envio exige confirmação consciente, revalidação online e idempotência;
- limpar cache não pode apagar pendências;
- comportamento ao trocar de usuário no mesmo aparelho permanece `A DEFINIR`.

## Configuração

Crie `android/local.properties` a partir de `android/local.properties.example` ou forneça as configurações públicas do Supabase por Gradle/environment.

Nenhuma credencial privada deve ser commitada.

## Estoque atual

O Android não mantém saldo oficial próprio. Ele consome `public.stock_current`, que permanece autoridade para a posição derivada do estoque.

## Validação

```bash
gradle -p android --no-daemon :app:lintDebug :app:testDebugUnitTest :app:assembleDebug
```

CI aprovada não substitui teste funcional em aparelho. Distribuição/atualização do APK pertence ao Bloco 12 de 13.
