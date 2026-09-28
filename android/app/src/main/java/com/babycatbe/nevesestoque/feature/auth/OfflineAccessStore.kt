package com.babycatbe.nevesestoque.feature.auth

import com.babycatbe.nevesestoque.data.offline.OfflineStore
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File

/**
 * Guarda somente o último acesso validado online neste aparelho. Serve apenas para reconhecer,
 * sem internet, o MESMO usuário no MESMO aparelho; nunca concede acesso novo. É apagado ao sair
 * da conta ou quando o servidor recusa o acesso/aparelho.
 */
object OfflineAccessStore {
    private fun file(): File = File(OfflineStore.accessDir(), "offline-access.json")

    suspend fun save(record: OfflineAccessRecord): Boolean {
        return try {
            val bytes = withContext(Dispatchers.IO) {
                OfflineStore.json.encodeToString(OfflineAccessRecord.serializer(), record).toByteArray(Charsets.UTF_8)
            }
            OfflineStore.writeAtomic(file(), bytes)
            true
        } catch (error: Throwable) {
            if (error is CancellationException) throw error
            false
        }
    }

    suspend fun load(): OfflineAccessRecord? = withContext(Dispatchers.IO) {
        runCatching {
            OfflineStore.readTextFile(file())
                ?.let { OfflineStore.json.decodeFromString(OfflineAccessRecord.serializer(), it) }
        }.getOrNull()
    }

    suspend fun clear(): Boolean = OfflineStore.deleteFile(file())
}
