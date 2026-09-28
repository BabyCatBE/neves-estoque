# Neves Estoque — Android

Aplicativo Android nativo do Neves Estoque, no mesmo repositório da Web.

## Estado atual

- applicationId: `com.babycatbe.nevesestoque`.
- Android: `0.2.0-alpha01`.
- Sistema: `0.26.0`.
- Kotlin + Jetpack Compose.
- Supabase Kotlin 3.8.0 + Ktor Android 3.5.1.
- Autenticação nativa preparada para usuário/senha e Google.
- Sessão persistente e auto-refresh pelo Auth do Supabase.
- Android reutiliza `claim_app_access`, `app_users` e `register_device` da Web.
- Um helper PostgREST público está preparado para adicionar `x-device-id` às operações funcionais após o registro do aparelho; os repositórios de cada módulo devem usá-lo para preservar a auditoria por dispositivo.
- Home navega de verdade para os seis módulos; as telas dos módulos ainda são placeholders.
- Testes manuais Offline continuam deferidos.

## Configuração

Crie `android/local.properties` a partir de `android/local.properties.example` ou forneça `SUPABASE_URL` e `SUPABASE_PUBLISHABLE_KEY` por Gradle/environment no build.

Nenhuma chave, senha, token, service role ou secret deve ser commitado.

## Google OAuth

Callback Android previsto:

`nevesestoque://auth`

Ele precisa estar permitido nas Redirect URLs do Supabase Auth antes do teste real do Google.

## Validação

```bash
gradle -p android --no-daemon :app:lintDebug :app:testDebugUnitTest :app:assembleDebug
```

O APK de distribuição ainda depende de configuração pública de build e assinatura estável fora do repositório.
