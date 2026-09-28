# Neves Estoque — Android

Base nativa do Neves Estoque, construída em Kotlin + Jetpack Compose dentro do mesmo repositório da Web.

## Estado deste bloco

- Base Android incremental criada.
- applicationId inicial e estável: `com.babycatbe.nevesestoque`.
- Android: `0.1.0-alpha01`.
- Sistema Neves Estoque de referência: `0.26.0`.
- compileSdk 37, targetSdk 36, minSdk 26.
- AGP 9.3.3, Gradle 9.5.0, JDK 17.
- Compose BOM 2026.09.00.
- Home estrutural com os seis módulos aprovados.
- Ainda não é o primeiro APK de distribuição: os módulos ainda precisam receber paridade funcional e a assinatura estável precisa ser configurada fora do repositório.

## Build de validação

A CI usa Gradle 9.5.0 diretamente, sem depender de wrapper binário versionado nesta primeira etapa:

```bash
gradle -p android --no-daemon :app:lintDebug :app:testDebugUnitTest :app:assembleDebug
```

## Assinatura

APK de distribuição deve manter o mesmo applicationId e a mesma assinatura entre versões. Chaves e senhas não devem ser commitadas. O fluxo de release assinado será preparado com secrets apropriados antes do primeiro APK distribuído.
