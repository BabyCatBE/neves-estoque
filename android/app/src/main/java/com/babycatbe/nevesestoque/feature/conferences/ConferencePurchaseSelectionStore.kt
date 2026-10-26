package com.babycatbe.nevesestoque.feature.conferences

import com.babycatbe.nevesestoque.data.offline.OfflineStore
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import kotlinx.serialization.Serializable
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.encodeToString
import java.io.File
import java.security.MessageDigest

/**
 * Lista local de lembretes de compra por usuário. Nunca é Conferência, Entrada,
 * pendência enviável, nem pedido persistido no backend.
 */
@Serializable
data class ConferencePurchaseProduct(
    val productId: String,
    val name: String,
    val unit: String,
    val currentQuantity: Double? = null,
)

@Serializable
private data class ConferencePurchaseSelection(
    val ownerId: String,
    val products: List<ConferencePurchaseProduct>,
)

object ConferencePurchaseSelectionStore {
    private val mutex = Mutex()

    private fun file(ownerId: String): File {
        val hash = MessageDigest.getInstance("SHA-256")
            .digest(ownerId.toByteArray(Charsets.UTF_8))
            .joinToString("") { "%02x".format(it) }
        return File(OfflineStore.conferencePurchasesDir(), "$hash.json")
    }

    private suspend fun read(ownerId: String): List<ConferencePurchaseProduct> {
        require(ownerId.isNotBlank()) { "Usuário indisponível." }
        val raw = OfflineStore.readTextFile(file(ownerId)) ?: return emptyList()
        val saved = withContext(Dispatchers.IO) {
            OfflineStore.json.decodeFromString<ConferencePurchaseSelection>(raw)
        }
        require(saved.ownerId == ownerId) { "Seleção pertence a outro usuário." }
        return saved.products.distinctBy { it.productId }
    }

    suspend fun load(ownerId: String): ConferenceDraftResult<List<ConferencePurchaseProduct>> =
        try {
            ConferenceDraftResult.Success(mutex.withLock { read(ownerId) })
        } catch (error: Throwable) {
            if (error is CancellationException) throw error
            ConferenceDraftResult.Failure("Não foi possível ler os produtos marcados. A seleção local foi preservada.")
        }

    suspend fun toggle(
        ownerId: String,
        product: ConferencePurchaseProduct,
    ): ConferenceDraftResult<List<ConferencePurchaseProduct>> = try {
        mutex.withLock {
            val current = read(ownerId)
            val next = if (current.any { it.productId == product.productId })
                current.filterNot { it.productId == product.productId }
            else current + product
            persist(ownerId, next)
            ConferenceDraftResult.Success(next)
        }
    } catch (error: Throwable) {
        if (error is CancellationException) throw error
        ConferenceDraftResult.Failure("Não foi possível atualizar os produtos marcados. Tente novamente.")
    }

    suspend fun clear(ownerId: String): ConferenceDraftResult<List<ConferencePurchaseProduct>> = try {
        mutex.withLock {
            read(ownerId)
            persist(ownerId, emptyList())
            ConferenceDraftResult.Success(emptyList())
        }
    } catch (error: Throwable) {
        if (error is CancellationException) throw error
        ConferenceDraftResult.Failure("Não foi possível limpar a seleção. Ela foi preservada.")
    }

    private suspend fun persist(ownerId: String, products: List<ConferencePurchaseProduct>) {
        val encoded = withContext(Dispatchers.IO) {
            OfflineStore.json.encodeToString(ConferencePurchaseSelection(ownerId, products))
        }
        OfflineStore.writeAtomic(file(ownerId), encoded.toByteArray(Charsets.UTF_8))
    }
}
