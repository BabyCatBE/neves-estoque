package com.babycatbe.nevesestoque.feature.conferences

import com.babycatbe.nevesestoque.data.offline.OfflineStore
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.Serializable
import java.io.File
import java.security.MessageDigest
import java.time.OffsetDateTime

/**
 * Uma conferência incompleta NUNCA é uma PendingOperation enviável.
 * O formato conserva strings (inclusive "" e "0") e NÃO aciona RPC ou sincronização.
 * Gravação atômica em armazenamento privado, separada do cache apagável.
 */
@Serializable
data class LocalConferenceDraft(
    val ownerId: String,
    val categoryId: String,
    val date: String,
    val responsible: String,
    val observation: String,
    val quantities: Map<String, String>,
    val updatedAt: String = OffsetDateTime.now().toString(),
) {
    fun filledCount(productIds: List<String>): Int =
        productIds.count { quantities[it]?.isNotBlank() == true }

    fun forProducts(productIds: List<String>): LocalConferenceDraft =
        copy(quantities = productIds.associateWith { quantities[it].orEmpty() })
}

sealed interface ConferenceDraftResult<out T> {
    data class Success<T>(val value: T) : ConferenceDraftResult<T>
    data class Failure(val message: String) : ConferenceDraftResult<Nothing>
}

object ConferenceDraftStore {
    private fun file(ownerId: String, categoryId: String): File {
        val key = (ownerId + "|" + categoryId).toByteArray(Charsets.UTF_8)
        val hash = MessageDigest.getInstance("SHA-256").digest(key)
            .joinToString("") { "%02x".format(it) }
        return File(OfflineStore.conferenceDraftsDir(), "$hash.json")
    }

    suspend fun load(ownerId: String, categoryId: String): ConferenceDraftResult<LocalConferenceDraft?> {
        if (ownerId.isBlank() || categoryId.isBlank()) return ConferenceDraftResult.Failure("Usuário ou Categoria indisponível.")
        return try {
            val raw = OfflineStore.readTextFile(file(ownerId, categoryId))
                ?: return ConferenceDraftResult.Success(null)
            val draft = withContext(Dispatchers.IO) {
                OfflineStore.json.decodeFromString(LocalConferenceDraft.serializer(), raw)
            }
            if (draft.ownerId != ownerId || draft.categoryId != categoryId)
                ConferenceDraftResult.Failure("O rascunho pertence a outra sessão ou Categoria.")
            else ConferenceDraftResult.Success(draft)
        } catch (error: Throwable) {
            if (error is CancellationException) throw error
            ConferenceDraftResult.Failure("Não foi possível ler o rascunho. O arquivo foi preservado no aparelho.")
        }
    }

    suspend fun list(ownerId: String): ConferenceDraftResult<List<LocalConferenceDraft>> {
        if (ownerId.isBlank()) return ConferenceDraftResult.Failure("Usuário indisponível.")
        return try {
            val found = mutableListOf<LocalConferenceDraft>()
            val files = withContext(Dispatchers.IO) {
                OfflineStore.conferenceDraftsDir().listFiles { f -> f.isFile && f.name.endsWith(".json") }.orEmpty().toList()
            }
            for (target in files) {
                val raw = OfflineStore.readTextFile(target) ?: continue
                val draft = withContext(Dispatchers.IO) {
                    OfflineStore.json.decodeFromString(LocalConferenceDraft.serializer(), raw)
                }
                if (draft.ownerId == ownerId) found += draft
            }
            ConferenceDraftResult.Success(found.sortedByDescending { it.updatedAt })
        } catch (error: Throwable) {
            if (error is CancellationException) throw error
            ConferenceDraftResult.Failure("Alguns rascunhos não puderam ser lidos. Os arquivos foram preservados.")
        }
    }

    suspend fun save(draft: LocalConferenceDraft): ConferenceDraftResult<Unit> {
        if (draft.ownerId.isBlank() || draft.categoryId.isBlank())
            return ConferenceDraftResult.Failure("Usuário ou Categoria indisponível.")
        return try {
            val encoded = withContext(Dispatchers.IO) {
                OfflineStore.json.encodeToString(LocalConferenceDraft.serializer(), draft)
            }
            OfflineStore.writeAtomic(file(draft.ownerId, draft.categoryId), encoded.toByteArray(Charsets.UTF_8))
            ConferenceDraftResult.Success(Unit)
        } catch (error: Throwable) {
            if (error is CancellationException) throw error
            ConferenceDraftResult.Failure("Não foi possível guardar o rascunho no aparelho. Não saia desta tela.")
        }
    }

    suspend fun delete(ownerId: String, categoryId: String): ConferenceDraftResult<Unit> {
        if (ownerId.isBlank() || categoryId.isBlank())
            return ConferenceDraftResult.Failure("Usuário ou Categoria indisponível.")
        return try {
            if (OfflineStore.deleteFile(file(ownerId, categoryId))) ConferenceDraftResult.Success(Unit)
            else ConferenceDraftResult.Failure("Não foi possível remover o rascunho do aparelho.")
        } catch (error: Throwable) {
            if (error is CancellationException) throw error
            ConferenceDraftResult.Failure("Não foi possível remover o rascunho do aparelho.")
        }
    }
}
