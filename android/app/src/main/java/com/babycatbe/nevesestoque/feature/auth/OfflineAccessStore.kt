package com.babycatbe.nevesestoque.feature.auth

import com.babycatbe.nevesestoque.data.offline.OfflineStore
import java.io.File

/**
 * Guarda somente o último acesso validado online neste aparelho. Serve apenas para reconhecer,
 * sem internet, o MESMO usuário no MESMO aparelho; nunca concede acesso novo. É apagado ao sair
 * da conta ou quando o servidor recusa o acesso/aparelho.
 */
object OfflineAccessStore {
    private fun file(): File = File(OfflineStore.accessDir(), "offline-access.json")

    fun save(record: OfflineAccessRecord) {
        runCatching {
            OfflineStore.writeAtomic(
                file(),
                OfflineStore.json.encodeToString(OfflineAccessRecord.serializer(), record).toByteArray(Charsets.UTF_8),
            )
        }
    }

    fun load(): OfflineAccessRecord? = runCatching {
        file().takeIf { it.isFile }?.readText(Charsets.UTF_8)
            ?.let { OfflineStore.json.decodeFromString(OfflineAccessRecord.serializer(), it) }
    }.getOrNull()

    fun clear() {
        runCatching { file().delete() }
    }
}
