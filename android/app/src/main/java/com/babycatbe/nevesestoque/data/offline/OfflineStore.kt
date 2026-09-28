package com.babycatbe.nevesestoque.data.offline

import android.content.Context
import android.net.ConnectivityManager
import android.net.Network
import android.net.NetworkCapabilities
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import java.io.File
import java.io.IOException
import java.security.MessageDigest

/**
 * Armazenamento local do Offline Android (decisão aprovada por Elias em 28/09/2026: arquivos JSON internos).
 *
 * - "cache": cópias reconstruíveis de dados de leitura; pode ser apagado a qualquer momento.
 * - "pending": pendências locais (etapa 10b); ficam em pasta separada e NUNCA são apagadas junto com o cache.
 * - "access": registro mínimo do último acesso validado online (usuário + aparelho), também separado do cache.
 *
 * Toda gravação é atômica: escreve um arquivo temporário e só então troca pelo definitivo, para que
 * uma interrupção (bateria, app encerrado) não deixe arquivo pela metade.
 * Os arquivos ficam na área privada do app. Dados locais nunca concedem permissão: o banco continua
 * decidindo tudo quando a conexão volta.
 */
object OfflineStore {
    private lateinit var root: File
    private val _lastSnapshotAt = MutableStateFlow<Long?>(null)

    /** Momento da última cópia de leitura salva com sucesso (para a faixa "Sem internet"). */
    val lastSnapshotAt: StateFlow<Long?> = _lastSnapshotAt.asStateFlow()

    val json: Json = Json {
        ignoreUnknownKeys = true
        encodeDefaults = true
    }

    fun initialize(context: Context) {
        root = File(context.applicationContext.filesDir, "offline")
        cacheDir().mkdirs()
        pendingDir().mkdirs()
        accessDir().mkdirs()
        _lastSnapshotAt.value = cacheDir().listFiles()?.maxOfOrNull { it.lastModified() }?.takeIf { it > 0 }
    }

    fun cacheDir(): File = File(root, "cache")
    fun pendingDir(): File = File(root, "pending")
    fun accessDir(): File = File(root, "access")

    private fun safeName(key: String): String = key.replace(Regex("[^A-Za-z0-9._-]"), "_")

    fun writeCache(key: String, text: String) {
        writeAtomic(File(cacheDir(), safeName(key) + ".json"), text.toByteArray(Charsets.UTF_8))
        _lastSnapshotAt.value = System.currentTimeMillis()
    }

    fun readCache(key: String): String? =
        File(cacheDir(), safeName(key) + ".json").takeIf { it.isFile }?.readText(Charsets.UTF_8)

    fun writeCacheBytes(key: String, bytes: ByteArray) {
        writeAtomic(File(cacheDir(), "bin-" + sha256(key)), bytes)
    }

    fun readCacheBytes(key: String): ByteArray? =
        File(cacheDir(), "bin-" + sha256(key)).takeIf { it.isFile }?.readBytes()

    /** Apaga somente o cache reconstruível. Pendências locais são preservadas. */
    fun clearCache() {
        cacheDir().listFiles()?.forEach { it.delete() }
        _lastSnapshotAt.value = null
    }

    fun writeAtomic(target: File, bytes: ByteArray) {
        target.parentFile?.mkdirs()
        val temp = File(target.parentFile, target.name + ".tmp")
        temp.outputStream().use { stream ->
            stream.write(bytes)
            stream.fd.sync()
        }
        if (!temp.renameTo(target)) {
            target.delete()
            if (!temp.renameTo(target)) throw IOException("Não foi possível gravar ${target.name}.")
        }
    }

    private fun sha256(value: String): String =
        MessageDigest.getInstance("SHA-256").digest(value.toByteArray()).joinToString("") { "%02x".format(it) }
}

/** Estado de conexão do aparelho, observado pelo sistema Android. */
object ConnectivityMonitor {
    private val _online = MutableStateFlow(true)
    val online: StateFlow<Boolean> = _online.asStateFlow()

    fun initialize(context: Context) {
        val manager = context.getSystemService(Context.CONNECTIVITY_SERVICE) as? ConnectivityManager ?: return
        _online.value = manager.activeNetwork?.let { network ->
            manager.getNetworkCapabilities(network)?.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET)
        } ?: false
        runCatching {
            manager.registerDefaultNetworkCallback(object : ConnectivityManager.NetworkCallback() {
                override fun onAvailable(network: Network) {
                    _online.value = true
                }

                override fun onLost(network: Network) {
                    _online.value = false
                }

                override fun onCapabilitiesChanged(network: Network, capabilities: NetworkCapabilities) {
                    _online.value = capabilities.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET)
                }
            })
        }
    }

    val isOnline: Boolean get() = _online.value
}

/** Lançada quando não há internet e também não existe cópia local para a leitura pedida. */
class OfflineUnavailableException :
    IllegalStateException("Sem internet e sem dados guardados neste aparelho para esta tela.")

private val NETWORK_EXCEPTION_NAMES = setOf(
    "HttpRequestException",
    "HttpRequestTimeoutException",
    "ConnectTimeoutException",
    "SocketTimeoutException",
    "UnresolvedAddressException",
)

/**
 * Distingue falha de rede (sem internet, servidor inalcançável, tempo esgotado) de recusa real do
 * servidor (autorização, validação). Somente falha de rede pode usar dados locais.
 */
fun isNetworkFailure(error: Throwable): Boolean {
    if (!ConnectivityMonitor.isOnline) return true
    return generateSequence(error) { it.cause }.take(8).any { cause ->
        cause is IOException || cause.javaClass.simpleName in NETWORK_EXCEPTION_NAMES
    }
}

/**
 * Leitura com cópia local: se a leitura online funcionar, guarda a cópia; se falhar por rede,
 * devolve a última cópia guardada. Erros que não são de rede continuam aparecendo normalmente.
 */
suspend inline fun <reified T> offlineCachedList(key: String, crossinline fetch: suspend () -> List<T>): List<T> {
    return try {
        val rows = fetch()
        runCatching { OfflineStore.writeCache(key, OfflineStore.json.encodeToString(rows)) }
        rows
    } catch (error: Throwable) {
        if (error is kotlinx.coroutines.CancellationException) throw error
        if (!isNetworkFailure(error)) throw error
        val cached = OfflineStore.readCache(key) ?: throw OfflineUnavailableException()
        OfflineStore.json.decodeFromString<List<T>>(cached)
    }
}
