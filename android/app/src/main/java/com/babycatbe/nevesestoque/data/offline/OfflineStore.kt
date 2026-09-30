package com.babycatbe.nevesestoque.data.offline

import android.content.Context
import android.net.ConnectivityManager
import android.net.Network
import android.net.NetworkCapabilities
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import java.io.File
import java.io.IOException
import java.nio.file.AtomicMoveNotSupportedException
import java.nio.file.Files
import java.nio.file.StandardCopyOption
import java.security.MessageDigest
import java.util.concurrent.ConcurrentHashMap

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
    /**
     * Geração do cache reconstruível. Quando muda, a primeira abertura da nova versão apaga
     * SOMENTE a pasta `cache/` (nunca `pending/` nem `access/`) antes de qualquer leitura local.
     *
     * 2 = troca dos dados de teste pelo catálogo real da Panificadora (Bloco 252, 30/09/2026):
     * impede que Produtos, Categorias, Fornecedores, estoque ou preços do período de testes
     * reapareçam a partir da cópia local. Só incrementar por decisão consciente.
     */
    const val CACHE_GENERATION = 2

    private lateinit var root: File
    private val fileMutexes = ConcurrentHashMap<String, Mutex>()
    private val _lastSnapshotAt = MutableStateFlow<Long?>(null)

    /** Liberado ao fim de [prepare]: nenhuma leitura/gravação de cache acontece antes da invalidação. */
    @Volatile
    private var ready = CompletableDeferred<Unit>()

    /** Momento da última cópia de leitura salva com sucesso (para a faixa "Sem internet"). */
    val lastSnapshotAt: StateFlow<Long?> = _lastSnapshotAt.asStateFlow()

    val json: Json = Json {
        ignoreUnknownKeys = true
        encodeDefaults = true
    }

    fun initialize(context: Context) {
        root = File(context.applicationContext.filesDir, "offline")
    }

    /** Testes: pasta isolada; o portão já nasce liberado, salvo se o teste for exercitar [prepare]. */
    internal fun initializeForTests(filesDir: File, awaitPrepare: Boolean = false) {
        root = File(filesDir, "offline")
        _lastSnapshotAt.value = null
        fileMutexes.clear()
        ready = CompletableDeferred<Unit>().also { if (!awaitPrepare) it.complete(Unit) }
    }

    /**
     * Cria as pastas, invalida o cache de geração antiga e carrega metadados locais fora da
     * thread da interface. Sempre libera o portão, mesmo em falha (cache é só otimização).
     */
    suspend fun prepare() = withContext(Dispatchers.IO) {
        try {
            cacheDir().mkdirs()
            pendingDir().mkdirs()
            accessDir().mkdirs()
            invalidateCacheIfGenerationChanged()
            _lastSnapshotAt.value = cacheDir().listFiles()?.maxOfOrNull { it.lastModified() }?.takeIf { it > 0 }
        } finally {
            ready.complete(Unit)
        }
    }

    private fun generationFile(): File = File(root, "cache-generation")

    /** Apaga apenas `cache/` quando a geração gravada difere de [CACHE_GENERATION]. */
    private suspend fun invalidateCacheIfGenerationChanged() {
        val stored = runCatching { readTextFile(generationFile())?.trim()?.toIntOrNull() }.getOrNull()
        if (stored == CACHE_GENERATION) return
        cacheDir().listFiles()?.forEach { file -> fileMutex(file).withLock { file.delete() } }
        writeAtomic(generationFile(), CACHE_GENERATION.toString().toByteArray(Charsets.UTF_8))
    }

    fun cacheDir(): File = File(root, "cache")
    fun pendingDir(): File = File(root, "pending")
    fun accessDir(): File = File(root, "access")

    private fun safeName(key: String): String = key.replace(Regex("[^A-Za-z0-9._-]"), "_")

    suspend fun writeCache(key: String, text: String) {
        ready.await()
        writeAtomic(File(cacheDir(), safeName(key) + ".json"), text.toByteArray(Charsets.UTF_8))
        _lastSnapshotAt.value = System.currentTimeMillis()
    }

    suspend fun readCache(key: String): String? {
        ready.await()
        return readTextFile(File(cacheDir(), safeName(key) + ".json"))
    }

    suspend fun writeCacheBytes(key: String, bytes: ByteArray) {
        ready.await()
        writeAtomic(File(cacheDir(), "bin-" + sha256(key)), bytes)
    }

    suspend fun readCacheBytes(key: String): ByteArray? {
        ready.await()
        return readBytesFile(File(cacheDir(), "bin-" + sha256(key)))
    }

    /** Apaga somente o cache reconstruível. Pendências locais são preservadas. */
    suspend fun clearCache() = withContext(Dispatchers.IO) {
        ready.await()
        cacheDir().listFiles()?.forEach { file -> fileMutex(file).withLock { file.delete() } }
        _lastSnapshotAt.value = null
    }

    /**
     * Sincroniza um temporário único antes de substituir o destino, sem apagar previamente a
     * versão válida. O substituidor configurável serve somente a testes determinísticos.
     */
    suspend fun writeAtomic(
        target: File,
        bytes: ByteArray,
        replace: (File, File) -> Unit = ::replaceTempFile,
    ) = withContext(Dispatchers.IO) {
        fileMutex(target).withLock {
            target.parentFile?.mkdirs()
            val parent = target.parentFile ?: throw IOException("Pasta local indisponível.")
            val temp = File.createTempFile("offline-${target.name}-", ".tmp", parent)
            try {
                temp.outputStream().use { stream ->
                    stream.write(bytes)
                    stream.fd.sync()
                }
                currentCoroutineContext().ensureActive()
                replace(temp, target)
            } finally {
                if (temp.exists()) temp.delete()
            }
        }
    }

    suspend fun deleteFile(
        target: File,
        delete: (File) -> Boolean = { it.delete() },
    ): Boolean = withContext(Dispatchers.IO) {
        fileMutex(target).withLock { !target.exists() || delete(target) }
    }

    suspend fun readTextFile(target: File): String? = withContext(Dispatchers.IO) {
        fileMutex(target).withLock { target.takeIf { it.isFile }?.readText(Charsets.UTF_8) }
    }

    suspend fun readBytesFile(target: File): ByteArray? = withContext(Dispatchers.IO) {
        fileMutex(target).withLock { target.takeIf { it.isFile }?.readBytes() }
    }

    private fun fileMutex(file: File): Mutex =
        fileMutexes.computeIfAbsent(file.absoluteFile.toPath().normalize().toString()) { Mutex() }

    private fun replaceTempFile(temp: File, target: File) {
        try {
            Files.move(
                temp.toPath(),
                target.toPath(),
                StandardCopyOption.ATOMIC_MOVE,
                StandardCopyOption.REPLACE_EXISTING,
            )
        } catch (_: AtomicMoveNotSupportedException) {
            Files.move(temp.toPath(), target.toPath(), StandardCopyOption.REPLACE_EXISTING)
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
    com.babycatbe.nevesestoque.data.supabase.awaitAuthSessionReady()
    return try {
        val rows = fetch()
        runCatching {
            val encoded = withContext(Dispatchers.IO) { OfflineStore.json.encodeToString(rows) }
            OfflineStore.writeCache(key, encoded)
        }
        rows
    } catch (error: Throwable) {
        if (error is kotlinx.coroutines.CancellationException) throw error
        if (!isNetworkFailure(error)) throw error
        val cached = OfflineStore.readCache(key) ?: throw OfflineUnavailableException()
        withContext(Dispatchers.IO) { OfflineStore.json.decodeFromString<List<T>>(cached) }
    }
}

/**
 * Lê somente a cópia local guardada por [offlineCachedList], sem rede. Usada para mostrar dados na hora
 * enquanto a leitura oficial acontece em segundo plano. Devolve null se não houver cópia ou se ela não
 * puder ser lida (cópia antiga/corrompida nunca vira erro de tela).
 */
suspend inline fun <reified T> readCachedList(key: String): List<T>? {
    return try {
        val cached = OfflineStore.readCache(key) ?: return null
        withContext(Dispatchers.IO) { OfflineStore.json.decodeFromString<List<T>>(cached) }
    } catch (error: Throwable) {
        if (error is kotlinx.coroutines.CancellationException) throw error
        null
    }
}
