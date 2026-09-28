import java.util.Properties
import java.net.URI
import java.util.Base64
import groovy.json.JsonSlurper

plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.plugin.compose")
    id("org.jetbrains.kotlin.plugin.serialization")
}

val localProperties = Properties().apply {
    val localFile = rootProject.file("local.properties")
    if (localFile.exists()) localFile.inputStream().use { load(it) }
}

fun appConfig(name: String): String =
    providers.gradleProperty(name).orNull
        ?: providers.environmentVariable(name).orNull
        ?: localProperties.getProperty(name, "")

fun quotedBuildConfig(value: String): String =
    "\"" + value.replace("\\", "\\\\").replace("\"", "\\\"") + "\""

val releaseUrl = appConfig("SUPABASE_URL")
val releaseKey = appConfig("SUPABASE_PUBLISHABLE_KEY")

val validateReleaseConfig = tasks.register("validateReleaseConfig") {
    group = "verification"
    description = "Reject missing/unsafe runtime configuration before building a release."
    doLast {
        val uri = runCatching { URI(releaseUrl) }.getOrNull()
        check(uri != null && uri.scheme == "https" && !uri.host.isNullOrBlank() &&
            uri.userInfo == null && uri.query == null && uri.fragment == null &&
            (uri.path.isNullOrEmpty() || uri.path == "/") &&
            !releaseUrl.contains("SEU-PROJETO", ignoreCase = true)) {
            "Release requires a valid HTTPS SUPABASE_URL (no credentials, query or placeholder)."
        }
        val isPublishable = releaseKey.startsWith("sb_publishable_") &&
            releaseKey.length > "sb_publishable_".length &&
            releaseKey.none { it.isWhitespace() } &&
            !releaseKey.contains("SUBSTITUA", ignoreCase = true)
        val isLegacyAnon = runCatching {
            val parts = releaseKey.split('.')
            if (parts.size != 3) false else {
                val payload = String(Base64.getUrlDecoder().decode(parts[1]), Charsets.UTF_8)
                val claims = JsonSlurper().parseText(payload) as? Map<*, *>
                claims?.get("role") == "anon"
            }
        }.getOrDefault(false)
        check(isPublishable || isLegacyAnon) {
            "Release requires a public Supabase publishable/anon key; private or invalid keys are forbidden."
        }
    }
}

tasks.matching { it.name == "preReleaseBuild" || it.name == "generateReleaseBuildConfig" }
    .configureEach { dependsOn(validateReleaseConfig) }

android {
    namespace = "com.babycatbe.nevesestoque"
    compileSdk = 37

    defaultConfig {
        applicationId = "com.babycatbe.nevesestoque"
        minSdk = 26
        targetSdk = 36
        versionCode = 23
        versionName = "0.23.0-alpha01"

        buildConfigField("String", "SYSTEM_VERSION", "\"0.26.0\"")
        buildConfigField("String", "ANDROID_VERSION", "\"0.23.0-alpha01\"")
        buildConfigField("String", "SUPABASE_URL", quotedBuildConfig(appConfig("SUPABASE_URL")))
        buildConfigField("String", "SUPABASE_PUBLISHABLE_KEY", quotedBuildConfig(appConfig("SUPABASE_PUBLISHABLE_KEY")))
    }

    buildFeatures {
        compose = true
        buildConfig = true
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    buildTypes {
        release {
            isMinifyEnabled = false
            proguardFiles("proguard-rules.pro")
        }
    }
}

dependencies {
    val composeBom = platform("androidx.compose:compose-bom:2026.09.00")
    val supabaseBom = platform("io.github.jan-tennert.supabase:bom:3.8.0")

    implementation(composeBom)
    androidTestImplementation(composeBom)
    implementation(supabaseBom)

    implementation("androidx.activity:activity-compose:1.13.0")
    implementation("androidx.compose.foundation:foundation")
    implementation("androidx.compose.material3:material3")
    implementation("androidx.compose.ui:ui")
    implementation("androidx.compose.ui:ui-tooling-preview")
    implementation("androidx.lifecycle:lifecycle-runtime-compose:2.11.0")
    implementation("androidx.lifecycle:lifecycle-viewmodel-compose:2.11.0")
    implementation("androidx.navigation:navigation-compose:2.10.0")

    implementation("io.github.jan-tennert.supabase:auth-kt")
    implementation("io.github.jan-tennert.supabase:postgrest-kt")
    implementation("io.github.jan-tennert.supabase:storage-kt")
    implementation("io.ktor:ktor-client-android:3.5.1")

    debugImplementation("androidx.compose.ui:ui-tooling")

    testImplementation("junit:junit:4.13.2")
}
