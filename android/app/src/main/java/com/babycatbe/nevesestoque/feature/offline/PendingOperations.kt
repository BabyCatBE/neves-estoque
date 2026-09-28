package com.babycatbe.nevesestoque.feature.offline

import com.babycatbe.nevesestoque.data.offline.OfflineStore
import com.babycatbe.nevesestoque.feature.conferences.CategoryConferenceWriteInput
import com.babycatbe.nevesestoque.feature.conferences.CategoryConferenceWriteItem
import com.babycatbe.nevesestoque.feature.conferences.ProductConferenceWriteInput
import com.babycatbe.nevesestoque.feature.entries.EntryCreateInput
import com.babycatbe.nevesestoque.feature.entries.EntryCreateItem
import com.babycatbe.nevesestoque.feature.entries.EntryDraftProduct
import com.babycatbe.nevesestoque.feature.entries.EntryDraftSupplier
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.withContext
import kotlinx.serialization.Serializable
import java.io.File
import java.time.OffsetDateTime
import java.util.UUID

/*
 * Pendências locais do Offline Android (etapa 10b).
 * Decisões aprovadas por Elias em 28/09/2026: arquivos JSON internos; envio somente com
 * confirmação consciente; pendência pode ser editada, excluída ou mantida.
 *
 * Uma pendência NÃO é dado oficial: não altera o estoque. Ela guarda o payload já validado e a
 * idempotencyKey original, que será reutilizada no envio para impedir gravação duplicada.
 * Fica em pasta separada do cache e nunca é apagada junto com ele.
 */

@Serializable
enum class PendingKind { Entry, CategoryConference, ProductConference }

@Serializable
data class PendingEntryItem(
    val productId: String? = null,
    val newProductClientId: String? = null,
    val newProductCategoryId: String? = null,
    val productName: String,
    val unit: String,
    val quantity: Double,
    val unitPrice: Double? = null,
)

@Serializable
data class PendingEntryPayload(
    val supplierId: String? = null,
    val newSupplierName: String? = null,
    val supplierLabel: String,
    val effectiveAt: String,
    val observation: String? = null,
    val items: List<PendingEntryItem>,
)

@Serializable
data class PendingConferenceItem(
    val productId: String,
    val productName: String,
    val unit: String,
    val quantity: Double,
)

@Serializable
data class PendingCategoryConferencePayload(
    val categoryId: String,
    val categoryLabel: String,
    val effectiveAt: String,
    val physicalResponsible: String,
    val observation: String? = null,
    val items: List<PendingConferenceItem>,
)

@Serializable
data class PendingProductConferencePayload(
    val productId: String,
    val productLabel: String,
    val unit: String,
    val effectiveAt: String,
    val physicalResponsible: String,
    val quantity: Double,
    val observation: String? = null,
)

@Serializable
data class PendingOperation(
    val localId: String,
    val kind: PendingKind,
    val status: String = "pending_confirmation",
    val createdAt: String,
    val updatedAt: String,
    val authUserId: String? = null,
    val appUserId: String? = null,
    val deviceId: String,
    val actorLabel: String,
    val idempotencyKey: String,
    val entry: PendingEntryPayload? = null,
    val categoryConference: PendingCategoryConferencePayload? = null,
    val productConference: PendingProductConferencePayload? = null,
) {
    val effectiveAt: String
        get() = entry?.effectiveAt ?: categoryConference?.effectiveAt ?: productConference?.effectiveAt.orEmpty()

    val kindLabel: String
        get() = when (kind) {
            PendingKind.Entry -> "Entrada"
            PendingKind.CategoryConference -> "Conferência por Categoria"
            PendingKind.ProductConference -> "Conferência unitária"
        }

    val summaryTitle: String
        get() = when (kind) {
            PendingKind.Entry -> entry?.supplierLabel?.ifBlank { null } ?: "Fornecedor não identificado"
            PendingKind.CategoryConference -> categoryConference?.categoryLabel?.ifBlank { null } ?: "Categoria não identificada"
            PendingKind.ProductConference -> productConference?.productLabel?.ifBlank { null } ?: "Produto não identificado"
        }

    val summarySubtitle: String
        get() = when (kind) {
            PendingKind.Entry -> {
                val count = entry?.items?.size ?: 0
                "$count ${if (count == 1) "item" else "itens"}" + (entry?.observation?.let { " · $it" } ?: "")
            }
            PendingKind.CategoryConference -> "Responsável físico: ${categoryConference?.physicalResponsible.orEmpty()}"
            PendingKind.ProductConference -> "Responsável físico: ${productConference?.physicalResponsible.orEmpty()}"
        }
}

data class PendingMetadata(
    val authUserId: String?,
    val appUserId: String?,
    val deviceId: String,
    val actorLabel: String,
)

// ---------- Construção a partir dos formulários ----------

fun buildPendingEntry(
    input: EntryCreateInput,
    supplierLabel: String,
    productLabel: (EntryCreateItem) -> Pair<String, String>,
    metadata: PendingMetadata,
    localId: String = UUID.randomUUID().toString(),
    now: String = OffsetDateTime.now().toString(),
): PendingOperation = PendingOperation(
    localId = localId,
    kind = PendingKind.Entry,
    createdAt = now,
    updatedAt = now,
    authUserId = metadata.authUserId,
    appUserId = metadata.appUserId,
    deviceId = metadata.deviceId,
    actorLabel = metadata.actorLabel,
    idempotencyKey = input.idempotencyKey,
    entry = PendingEntryPayload(
        supplierId = input.supplierId,
        newSupplierName = input.newSupplier?.name,
        supplierLabel = supplierLabel,
        effectiveAt = input.effectiveAt,
        observation = input.observation,
        items = input.items.map { item ->
            val (name, unit) = productLabel(item)
            PendingEntryItem(
                productId = item.productId,
                newProductClientId = item.newProduct?.clientId,
                newProductCategoryId = item.newProduct?.categoryId,
                productName = item.newProduct?.name ?: name,
                unit = item.newProduct?.unit ?: unit,
                quantity = item.quantity,
                unitPrice = item.unitPrice,
            )
        },
    ),
)

fun buildPendingCategoryConference(
    input: CategoryConferenceWriteInput,
    categoryLabel: String,
    productLabel: (String) -> Pair<String, String>,
    metadata: PendingMetadata,
    localId: String = UUID.randomUUID().toString(),
    now: String = OffsetDateTime.now().toString(),
): PendingOperation = PendingOperation(
    localId = localId,
    kind = PendingKind.CategoryConference,
    createdAt = now,
    updatedAt = now,
    authUserId = metadata.authUserId,
    appUserId = metadata.appUserId,
    deviceId = input.deviceId,
    actorLabel = metadata.actorLabel,
    idempotencyKey = input.idempotencyKey,
    categoryConference = PendingCategoryConferencePayload(
        categoryId = input.categoryId,
        categoryLabel = categoryLabel,
        effectiveAt = input.effectiveAt,
        physicalResponsible = input.physicalResponsible,
        observation = input.observation,
        items = input.items.map { item ->
            val (name, unit) = productLabel(item.productId)
            PendingConferenceItem(item.productId, name, unit, item.quantity)
        },
    ),
)

fun buildPendingProductConference(
    input: ProductConferenceWriteInput,
    productLabel: String,
    unit: String,
    metadata: PendingMetadata,
    localId: String = UUID.randomUUID().toString(),
    now: String = OffsetDateTime.now().toString(),
): PendingOperation = PendingOperation(
    localId = localId,
    kind = PendingKind.ProductConference,
    createdAt = now,
    updatedAt = now,
    authUserId = metadata.authUserId,
    appUserId = metadata.appUserId,
    deviceId = input.deviceId,
    actorLabel = metadata.actorLabel,
    idempotencyKey = input.idempotencyKey,
    productConference = PendingProductConferencePayload(
        productId = input.productId,
        productLabel = productLabel,
        unit = unit,
        effectiveAt = input.effectiveAt,
        physicalResponsible = input.physicalResponsible,
        quantity = input.quantity,
        observation = input.observation,
    ),
)

// ---------- Conversão de volta para os contratos oficiais (mesma idempotencyKey) ----------

fun PendingOperation.toEntryCreateInput(effectiveAtOverride: String? = null): EntryCreateInput {
    val payload = requireNotNull(entry) { "Pendência não é uma Entrada." }
    return EntryCreateInput(
        supplierId = payload.supplierId,
        newSupplier = payload.newSupplierName?.let(::EntryDraftSupplier),
        effectiveAt = effectiveAtOverride ?: payload.effectiveAt,
        idempotencyKey = idempotencyKey,
        observation = payload.observation,
        items = payload.items.map { item ->
            EntryCreateItem(
                productId = item.productId,
                newProduct = item.newProductClientId?.let {
                    EntryDraftProduct(it, item.productName, item.unit, item.newProductCategoryId)
                },
                quantity = item.quantity,
                unitPrice = item.unitPrice,
            )
        },
    )
}

fun PendingOperation.toCategoryConferenceInput(deviceIdNow: String): CategoryConferenceWriteInput {
    val payload = requireNotNull(categoryConference) { "Pendência não é uma Conferência por Categoria." }
    return CategoryConferenceWriteInput(
        categoryId = payload.categoryId,
        effectiveAt = payload.effectiveAt,
        physicalResponsible = payload.physicalResponsible,
        deviceId = deviceIdNow,
        idempotencyKey = idempotencyKey,
        observation = payload.observation,
        items = payload.items.map { CategoryConferenceWriteItem(it.productId, it.quantity) },
    )
}

fun PendingOperation.toProductConferenceInput(deviceIdNow: String): ProductConferenceWriteInput {
    val payload = requireNotNull(productConference) { "Pendência não é uma Conferência unitária." }
    return ProductConferenceWriteInput(
        productId = payload.productId,
        effectiveAt = payload.effectiveAt,
        physicalResponsible = payload.physicalResponsible,
        deviceId = deviceIdNow,
        idempotencyKey = idempotencyKey,
        quantity = payload.quantity,
        observation = payload.observation,
    )
}

// ---------- Armazenamento ----------

data class PendingFileProblem(
    val fileName: String,
    val userMessage: String = "Existe uma pendência local com problema de leitura. O arquivo foi preservado neste aparelho.",
)

sealed interface PendingMutationResult {
    data object Success : PendingMutationResult
    data class Failure(val userMessage: String) : PendingMutationResult
}

sealed interface PendingLoadResult {
    data object Success : PendingLoadResult
    data class Failure(val userMessage: String) : PendingLoadResult
}

object PendingStore {
    private val _pending = MutableStateFlow<List<PendingOperation>>(emptyList())
    private val _problems = MutableStateFlow<List<PendingFileProblem>>(emptyList())
    private val _loaded = MutableStateFlow(false)

    /** Pendências atuais, da mais recente para a mais antiga. */
    val pending: StateFlow<List<PendingOperation>> = _pending.asStateFlow()
    val problems: StateFlow<List<PendingFileProblem>> = _problems.asStateFlow()
    val loaded: StateFlow<Boolean> = _loaded.asStateFlow()

    private fun file(localId: String): File =
        File(OfflineStore.pendingDir(), localId.replace(Regex("[^A-Za-z0-9-]"), "_") + ".json")

    suspend fun reload(): PendingLoadResult {
        return try {
            val (operations, problems) = withContext(Dispatchers.IO) {
                val valid = mutableListOf<PendingOperation>()
                val invalid = mutableListOf<PendingFileProblem>()
                OfflineStore.pendingDir().mkdirs()
                OfflineStore.pendingDir().listFiles { f -> f.isFile && f.name.endsWith(".json") }
                    .orEmpty()
                    .forEach { pendingFile ->
                        try {
                            valid += OfflineStore.json.decodeFromString(
                                PendingOperation.serializer(),
                                OfflineStore.readTextFile(pendingFile)
                                    ?: throw IllegalStateException("Arquivo local indisponível."),
                            )
                        } catch (error: Throwable) {
                            if (error is CancellationException) throw error
                            invalid += PendingFileProblem(pendingFile.name)
                        }
                    }
                valid.sortedByDescending { it.createdAt } to invalid.sortedBy { it.fileName }
            }
            _pending.value = operations
            _problems.value = problems
            _loaded.value = true
            PendingLoadResult.Success
        } catch (error: Throwable) {
            if (error is CancellationException) throw error
            _problems.value = listOf(
                PendingFileProblem(
                    fileName = "",
                    userMessage = "Não foi possível carregar as pendências locais agora. Tente novamente.",
                )
            )
            _loaded.value = true
            PendingLoadResult.Failure("Não foi possível carregar as pendências locais agora. Tente novamente.")
        }
    }

    suspend fun save(
        operation: PendingOperation,
        write: suspend (File, ByteArray) -> Unit = { target, bytes -> OfflineStore.writeAtomic(target, bytes) },
    ): PendingMutationResult {
        val target = file(operation.localId)
        return try {
            val bytes = withContext(Dispatchers.IO) {
                OfflineStore.json.encodeToString(PendingOperation.serializer(), operation).toByteArray(Charsets.UTF_8)
            }
            write(target, bytes)
            _pending.update { current ->
                (current.filterNot { it.localId == operation.localId } + operation)
                    .sortedByDescending { it.createdAt }
            }
            _problems.update { current -> current.filterNot { it.fileName == target.name } }
            _loaded.value = true
            PendingMutationResult.Success
        } catch (error: Throwable) {
            if (error is CancellationException) throw error
            PendingMutationResult.Failure("Não foi possível guardar a pendência neste aparelho. Tente novamente.")
        }
    }

    /** Consulta somente o estado já carregado; nunca acessa arquivo durante a composição da UI. */
    fun find(localId: String): PendingOperation? = _pending.value.firstOrNull { it.localId == localId }

    /** Remove somente o rascunho local. Nunca existe registro oficial correspondente a apagar. */
    suspend fun delete(
        localId: String,
        remove: suspend (File) -> Boolean = { target -> OfflineStore.deleteFile(target) },
    ): PendingMutationResult {
        val target = file(localId)
        return try {
            if (!remove(target)) {
                PendingMutationResult.Failure("Não foi possível excluir a pendência deste aparelho. Tente novamente.")
            } else {
                _pending.update { current -> current.filterNot { it.localId == localId } }
                _problems.update { current -> current.filterNot { it.fileName == target.name } }
                _loaded.value = true
                PendingMutationResult.Success
            }
        } catch (error: Throwable) {
            if (error is CancellationException) throw error
            PendingMutationResult.Failure("Não foi possível excluir a pendência deste aparelho. Tente novamente.")
        }
    }
}

/** Mesmo texto usado nos formulários ao guardar sem internet. */
const val PENDING_SAVED_MESSAGE =
    "Sem internet: guardado como pendência local neste aparelho. Ainda não altera o estoque. " +
        "Confirme o envio em Alertas → Pendências locais quando a conexão voltar."

/** Metadados do usuário/aparelho validados, para identificar quem preparou a pendência. */
suspend fun currentPendingMetadata(): PendingMetadata? {
    val deviceId = com.babycatbe.nevesestoque.data.device.DeviceIdentityStore.registeredDeviceId() ?: return null
    val access = com.babycatbe.nevesestoque.feature.auth.OfflineAccessStore.load()
    return PendingMetadata(
        authUserId = access?.authUserId,
        appUserId = access?.appUserId,
        deviceId = deviceId,
        actorLabel = access?.displayName?.ifBlank { null } ?: access?.username ?: "Usuário deste aparelho",
    )
}
