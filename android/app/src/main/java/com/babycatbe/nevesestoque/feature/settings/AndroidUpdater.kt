package com.babycatbe.nevesestoque.feature.settings

import android.app.DownloadManager
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Environment
import android.provider.Settings
import androidx.core.content.FileProvider
import com.babycatbe.nevesestoque.BuildConfig
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import java.io.File
import java.net.HttpURLConnection
import java.net.URL
import java.security.MessageDigest

data class ReleaseAsset(val name: String, val url: String)
data class UpdateRelease(val versionCode: Int, val versionName: String, val apk: ReleaseAsset, val checksum: ReleaseAsset, val sha256: String)

sealed interface UpdateState {
    data object Idle : UpdateState
    data object Checking : UpdateState
    data object UpToDate : UpdateState
    data class Available(val release: UpdateRelease) : UpdateState
    data class Downloading(val release: UpdateRelease, val progress: Int?) : UpdateState
    data class Ready(val release: UpdateRelease, val file: File) : UpdateState
    data class Error(val message: String, val release: UpdateRelease? = null) : UpdateState
}

object ReleaseParser {
    const val OFFICIAL_APK = "neves-estoque-android.apk"
    const val CHECKSUMS = "SHA256SUMS"
    private val versionCodePattern = Regex("""(?im)^\s*versionCode\s*[:=]\s*(\d+)\s*$""")
    private val shaPattern = Regex("""(?im)^([a-f0-9]{64})\s+\*?(.+?)\s*$""")

    fun parse(json: String, checksumText: String): UpdateRelease {
        val root = Json.parseToJsonElement(json).jsonObject
        val draft = root["draft"]?.jsonPrimitive?.content?.toBooleanStrictOrNull() ?: false
        val prerelease = root["prerelease"]?.jsonPrimitive?.content?.toBooleanStrictOrNull() ?: false
        require(!draft) { "A Release mais recente ainda está em rascunho." }
        require(!prerelease) { "A Release mais recente é pré-release e não é distribuível." }
        val body = root["body"]?.jsonPrimitive?.content.orEmpty()
        val versionCode = versionCodePattern.find(body)?.groupValues?.get(1)?.toIntOrNull()
            ?: error("Release sem versionCode explícito no corpo.")
        val versionName = root["tag_name"]?.jsonPrimitive?.content?.removePrefix("v")?.trim().orEmpty()
        require(versionName.isNotBlank()) { "Release sem versão identificável." }
        val assets = root["assets"]?.jsonArray ?: error("Release sem assets.")
        val mapped = assets.mapNotNull { element ->
            val obj = element.jsonObject
            val name = obj["name"]?.jsonPrimitive?.content ?: return@mapNotNull null
            val url = obj["browser_download_url"]?.jsonPrimitive?.content ?: return@mapNotNull null
            ReleaseAsset(name, url)
        }
        val apks = mapped.filter { it.name == OFFICIAL_APK }
        require(apks.size == 1) { "APK oficial ausente ou ambíguo: esperado exatamente $OFFICIAL_APK." }
        val checksums = mapped.filter { it.name == CHECKSUMS }
        require(checksums.size == 1) { "Checksum oficial ausente ou ambíguo: esperado exatamente $CHECKSUMS." }
        val sha = shaPattern.findAll(checksumText).firstOrNull { it.groupValues[2].trim() == OFFICIAL_APK }?.groupValues?.get(1)?.lowercase()
            ?: error("SHA-256 do APK oficial não encontrado em $CHECKSUMS.")
        return UpdateRelease(versionCode, versionName, apks.single(), checksums.single(), sha)
    }

    fun isUpdateAvailable(installedVersionCode: Int, remoteVersionCode: Int) = remoteVersionCode > installedVersionCode

    fun sha256(file: File): String {
        val digest = MessageDigest.getInstance("SHA-256")
        file.inputStream().buffered().use { input ->
            val buffer = ByteArray(DEFAULT_BUFFER_SIZE)
            while (true) {
                val read = input.read(buffer)
                if (read < 0) break
                digest.update(buffer, 0, read)
            }
        }
        return digest.digest().joinToString("") { "%02x".format(it) }
    }

    fun checksumMatches(file: File, expected: String) = sha256(file).equals(expected.trim(), ignoreCase = true)
}

class AndroidUpdater(private val context: Context) {
    companion object {
        private const val LATEST_RELEASE = "https://api.github.com/repos/BabyCatBE/neves-estoque/releases/latest"
        private const val PREFS = "android_updater"
        private const val KEY_DOWNLOAD_ID = "download_id"
        private const val KEY_FILE = "download_file"
        private const val KEY_VERSION_CODE = "version_code"
        private const val KEY_VERSION_NAME = "version_name"
        private const val KEY_APK_URL = "apk_url"
        private const val KEY_SHA_URL = "sha_url"
        private const val KEY_SHA = "sha"
    }
    private val downloads = context.getSystemService(DownloadManager::class.java)
    private val prefs = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)

    suspend fun check(): UpdateState = withContext(Dispatchers.IO) {
        try {
            val releaseJson = getText(LATEST_RELEASE)
            val checksumUrl = findChecksumUrl(releaseJson)
            val release = ReleaseParser.parse(releaseJson, getText(checksumUrl))
            if (ReleaseParser.isUpdateAvailable(BuildConfig.VERSION_CODE, release.versionCode)) restoreFor(release) ?: UpdateState.Available(release)
            else { clearDownloadState(true); UpdateState.UpToDate }
        } catch (e: Exception) { UpdateState.Error(networkMessage(e)) }
    }

    suspend fun restore(): UpdateState = withContext(Dispatchers.IO) {
        val release = savedRelease() ?: return@withContext UpdateState.Idle
        restoreFor(release) ?: UpdateState.Available(release)
    }

    fun startDownload(release: UpdateRelease): UpdateState {
        clearDownloadState(true)
        val dir = context.getExternalFilesDir(Environment.DIRECTORY_DOWNLOADS)
            ?: return UpdateState.Error("Armazenamento para atualização indisponível.", release)
        val file = File(dir, "neves-estoque-update-vc${release.versionCode}.apk")
        val request = DownloadManager.Request(Uri.parse(release.apk.url))
            .setTitle("Neves Estoque ${release.versionName}")
            .setDescription("Baixando atualização")
            .setNotificationVisibility(DownloadManager.Request.VISIBILITY_VISIBLE_NOTIFY_COMPLETED)
            .setAllowedOverMetered(true).setAllowedOverRoaming(false).setDestinationUri(Uri.fromFile(file))
        return try {
            val id = downloads.enqueue(request)
            save(id, file, release)
            UpdateState.Downloading(release, null)
        } catch (_: Exception) { UpdateState.Error("Não foi possível iniciar o download.", release) }
    }

    suspend fun poll(): UpdateState = withContext(Dispatchers.IO) {
        val release = savedRelease() ?: return@withContext UpdateState.Idle
        restoreFor(release) ?: UpdateState.Available(release)
    }

    fun canInstallPackages() = context.packageManager.canRequestPackageInstalls()

    fun openInstallPermission() {
        context.startActivity(Intent(Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES, Uri.parse("package:${context.packageName}")).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
    }

    fun install(file: File) {
        require(file.exists()) { "APK não encontrado." }
        val uri = FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", file)
        context.startActivity(Intent(Intent.ACTION_VIEW).setDataAndType(uri, "application/vnd.android.package-archive")
            .addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_ACTIVITY_NEW_TASK))
    }

    private fun restoreFor(release: UpdateRelease): UpdateState? {
        val id = prefs.getLong(KEY_DOWNLOAD_ID, -1L)
        val file = prefs.getString(KEY_FILE, null)?.let(::File)
        if (id < 0 || file == null) return null
        downloads.query(DownloadManager.Query().setFilterById(id)).use { cursor ->
            if (!cursor.moveToFirst()) {
                clearDownloadState(true)
                return UpdateState.Error("O download anterior não está mais disponível.", release)
            }
            return when (cursor.getInt(cursor.getColumnIndexOrThrow(DownloadManager.COLUMN_STATUS))) {
                DownloadManager.STATUS_PENDING, DownloadManager.STATUS_PAUSED, DownloadManager.STATUS_RUNNING -> {
                    val total = cursor.getLong(cursor.getColumnIndexOrThrow(DownloadManager.COLUMN_TOTAL_SIZE_BYTES))
                    val done = cursor.getLong(cursor.getColumnIndexOrThrow(DownloadManager.COLUMN_BYTES_DOWNLOADED_SO_FAR))
                    UpdateState.Downloading(release, if (total > 0) ((done * 100L) / total).toInt().coerceIn(0, 100) else null)
                }
                DownloadManager.STATUS_SUCCESSFUL -> validateDownloaded(file, release)
                else -> { clearDownloadState(true); UpdateState.Error("O download falhou ou foi interrompido. Tente novamente.", release) }
            }
        }
    }

    private fun validateDownloaded(file: File, release: UpdateRelease): UpdateState {
        if (!file.exists()) { clearDownloadState(false); return UpdateState.Error("O APK baixado não foi encontrado.", release) }
        return if (ReleaseParser.checksumMatches(file, release.sha256)) UpdateState.Ready(release, file)
        else { clearDownloadState(true); UpdateState.Error("SHA-256 incorreto. Instalação bloqueada; baixe novamente.", release) }
    }

    private fun savedRelease(): UpdateRelease? {
        val vc = prefs.getInt(KEY_VERSION_CODE, -1)
        val vn = prefs.getString(KEY_VERSION_NAME, null)
        val apk = prefs.getString(KEY_APK_URL, null)
        val shaUrl = prefs.getString(KEY_SHA_URL, null)
        val sha = prefs.getString(KEY_SHA, null)
        if (vc < 0 || vn == null || apk == null || shaUrl == null || sha == null) return null
        return UpdateRelease(vc, vn, ReleaseAsset(ReleaseParser.OFFICIAL_APK, apk), ReleaseAsset(ReleaseParser.CHECKSUMS, shaUrl), sha)
    }

    private fun save(id: Long, file: File, release: UpdateRelease) {
        prefs.edit().putLong(KEY_DOWNLOAD_ID, id).putString(KEY_FILE, file.absolutePath).putInt(KEY_VERSION_CODE, release.versionCode)
            .putString(KEY_VERSION_NAME, release.versionName).putString(KEY_APK_URL, release.apk.url).putString(KEY_SHA_URL, release.checksum.url)
            .putString(KEY_SHA, release.sha256).apply()
    }

    private fun clearDownloadState(deleteFile: Boolean) {
        if (deleteFile) prefs.getString(KEY_FILE, null)?.let { runCatching { File(it).delete() } }
        prefs.edit().clear().apply()
    }

    private fun findChecksumUrl(json: String): String {
        val assets = Json.parseToJsonElement(json).jsonObject["assets"]?.jsonArray ?: error("Release sem assets.")
        val matches = assets.mapNotNull { e ->
            val o = e.jsonObject
            if (o["name"]?.jsonPrimitive?.content == ReleaseParser.CHECKSUMS) o["browser_download_url"]?.jsonPrimitive?.content else null
        }
        require(matches.size == 1) { "Checksum oficial ausente ou ambíguo." }
        return matches.single()
    }

    private fun getText(url: String): String {
        val connection = URL(url).openConnection() as HttpURLConnection
        return try {
            connection.connectTimeout = 15_000; connection.readTimeout = 20_000; connection.requestMethod = "GET"
            connection.setRequestProperty("Accept", "application/vnd.github+json")
            connection.setRequestProperty("User-Agent", "NevesEstoque-Android/${BuildConfig.VERSION_NAME}")
            val code = connection.responseCode
            if (code !in 200..299) error("HTTP $code")
            connection.inputStream.bufferedReader().use { it.readText() }
        } finally { connection.disconnect() }
    }

    private fun networkMessage(e: Exception): String = when {
        e.message?.contains("HTTP 404") == true -> "Nenhuma Release publicada foi encontrada."
        e.message?.contains("HTTP") == true -> "GitHub indisponível no momento. Tente novamente."
        e is java.net.UnknownHostException || e is java.net.SocketTimeoutException -> "Sem conexão com a internet ou GitHub indisponível."
        else -> e.message ?: "Não foi possível verificar atualizações."
    }
}
