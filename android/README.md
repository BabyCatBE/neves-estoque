# Neves Estoque — Android

Aplicativo Android nativo do Neves Estoque, no mesmo repositório da Web.

## Estado atual

- applicationId: `com.babycatbe.nevesestoque`.
- Android: `0.3.0-alpha01`.
- Sistema: `0.26.0`.
- Kotlin + Jetpack Compose.
- Supabase Kotlin 3.8.0 + Ktor Android 3.5.1.
- Autenticação nativa por usuário/senha e Google preparada.
- Sessão persistente e auto-refresh pelo Auth do Supabase.
- Android reutiliza `claim_app_access`, `app_users` e `register_device` da Web.
- O helper PostgREST adiciona `x-device-id` às operações funcionais.
- Home possui seis rotas reais.
- **Estoque atual** já possui consulta funcional ao backend, pesquisa, organização por categoria/fornecedor/alfabética, valores e atualização manual.
- Os demais módulos continuam como placeholders e permanecem EM IMPLEMENTAÇÃO.
- Testes manuais Offline continuam deferidos.

## Configuração

Crie `android/local.properties` a partir de `android/local.properties.example` ou forneça `SUPABASE_URL` e `SUPABASE_PUBLISHABLE_KEY` por Gradle/environment no build.

Nenhuma chave, senha, token, service role ou secret deve ser commitado.

## Google OAuth

Callback Android previsto: `nevesestoque://auth`.

Ele precisa estar permitido nas Redirect URLs do Supabase Auth antes do teste real do Google.

## Estoque atual

O Android não recalcula estoque. Ele consome a view `public.stock_current`, que permanece como autoridade para:

- última Conferência válida + Entradas posteriores;
- preço atual;
- valor atual;
- estado `stock_requires_conference` após mescla.

Também consulta Categorias e Fornecedores apenas para reproduzir os agrupamentos da Web.

## Validação

```bash
gradle -p android --no-daemon :app:lintDebug :app:testDebugUnitTest :app:assembleDebug
```

O APK de distribuição ainda depende de configuração pública de build e assinatura estável fora do repositório.
