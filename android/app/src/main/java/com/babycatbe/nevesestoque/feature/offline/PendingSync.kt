package com.babycatbe.nevesestoque.feature.offline

import com.babycatbe.nevesestoque.data.device.DeviceIdentityStore
import com.babycatbe.nevesestoque.data.supabase.SupabaseProvider
import com.babycatbe.nevesestoque.data.supabase.attachRegisteredDevice
import com.babycatbe.nevesestoque.data.supabase.fetchByIdChunks
import com.babycatbe.nevesestoque.feature.conferences.ConferenceConsumptionWarning
import com.babycatbe.nevesestoque.feature.conferences.ConferenceHistoryItem
import com.babycatbe.nevesestoque.feature.conferences.ConferenceModuleRepository
import com.babycatbe.nevesestoque.feature.conferences.ProductConferenceRepository
import com.babycatbe.nevesestoque.feature.conferences.conferenceModuleErrorMessage
import com.babycatbe.nevesestoque.feature.entries.EntriesRepository
import com.babycatbe.nevesestoque.feature.entries.entryErrorMessage
import com.babycatbe.nevesestoque.feature.entries.shouldReconcileEntryFailure
import io.github.jan.supabase.postgrest.from
import io.github.jan.supabase.postgrest.query.Columns
import kotlinx.coroutines.CancellationException
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import java.time.Instant
import java.time.OffsetDateTime
import java.time.ZoneId
import java.time.format.DateTimeFormatter

/*
 * Confirmação consciente das pendências (etapa 10c), portada de src/features/offline/lib/pendingSync.ts
 * e da tela OfflinePendingPage da Web:
 * - nada é enviado automaticamente; cada envio começa consultando o estado atual do servidor;
 * - Entrada: Conferências do mesmo dia com Produto em comum exigem escolher a posição cronológica;
 * - Conferência: revisão de consumo e Conferência da mesma Categoria na mesma data são refeitas online;
 * - o envio usa a idempotencyKey original; a pendência só sai do aparelho após confirmação oficial.
 */

data class EntryConferenceConflictItem(
    val productId: String,
    val productName: String,
    val entryQuantity: Double,
    val conferenceQuantity: Double,
)

data class EntryConferenceConflict(
    val conferenceId: String,
    val effectiveAt: String,
    val physicalResponsible: String,
    val items: List<EntryConferenceConflictItem>,
)

data class EntryConflictPosition(
    val id: String,
    val label: String,
    val description: String,
    val effectiveAt: String,
)

private fun midpointIso(leftMs: Long, rightMs: Long): String? {
    val gap = rightMs - leftMs
    if (gap <= 1) return null
    return Instant.ofEpochMilli(leftMs + gap / 2).toString()
}

private fun epoch(value: String): Long = OffsetDateTime.parse(value).toInstant().toEpochMilli()

private fun formatTime(value: String, zone: ZoneId): String =
    OffsetDateTime.parse(value).atZoneSameInstant(zone).format(DateTimeFormatter.ofPattern("HH:mm"))

/** Porte de buildEntryConflictPositions (Web): antes da primeira, entre cada par e depois da última. */
fun buildEntryConflictPositions(
    conflicts: List<EntryConferenceConflict>,
    originalEffectiveAt: String,
    zone: ZoneId = ZoneId.systemDefault(),
): List<EntryConflictPosition> {
    if (conflicts.isEmpty()) return emptyList()
    val sorted = conflicts.sortedBy { epoch(it.effectiveAt) }
    val day = OffsetDateTime.parse(originalEffectiveAt).atZoneSameInstant(zone).toLocalDate()
    val dayStart = day.atStartOfDay(zone).toInstant().toEpochMilli()
    val dayEnd = day.plusDays(1).atStartOfDay(zone).toInstant().toEpochMilli()
    val first = sorted.first()
    val last = sorted.last()
    val single = sorted.size == 1

    return buildList {
        midpointIso(dayStart, epoch(first.effectiveAt))?.let {
            add(
                EntryConflictPosition(
                    "before-first",
                    if (single) "Antes da Conferência" else "Antes de todas",
                    "Antes da Conferência das ${formatTime(first.effectiveAt, zone)}",
                    it,
                )
            )
        }
        for (index in 0 until sorted.size - 1) {
            val left = sorted[index]
            val right = sorted[index + 1]
            midpointIso(epoch(left.effectiveAt), epoch(right.effectiveAt))?.let {
                add(
                    EntryConflictPosition(
                        "between-$index",
                        "Entre ${formatTime(left.effectiveAt, zone)} e ${formatTime(right.effectiveAt, zone)}",
                        "A Entrada aconteceu depois da primeira e antes da segunda Conferência.",
                        it,
                    )
                )
            }
        }
        midpointIso(epoch(last.effectiveAt), dayEnd)?.let {
            add(
                EntryConflictPosition(
                    "after-last",
                    if (single) "Depois da Conferência" else "Depois de todas",
                    "Depois da Conferência das ${formatTime(last.effectiveAt, zone)}",
                    it,
                )
            )
        }
    }
}

@Serializable
private data class ConflictConferenceRow(
    val id: String,
    @SerialName("effective_at") val effectiveAt: String,
    @SerialName("physical_responsible") val physicalResponsible: String? = null,
    @SerialName("deleted_at") val deletedAt: String? = null,
)

@Serializable
private data class ConflictConferenceItemRow(
    @SerialName("conference_id") val conferenceId: String,
    @SerialName("product_id") val productId: String,
    val quantity: Double,
)

/** Resultado de uma tentativa de envio. */
sealed interface PendingSendResult {
    data class Sent(val message: String) : PendingSendResult
    data class Failed(val message: String) : PendingSendResult
}

class PendingSyncRepository {
    private val entries = EntriesRepository()
    private val conferences = ConferenceModuleRepository()
    private val productConferences = ProductConferenceRepository()

    private fun client() = SupabaseProvider.client ?: error("Supabase não está configurado nesta build.")

    /** Conferências oficiais do mesmo dia com pelo menos um Produto em comum com a Entrada. */
    suspend fun entryConflicts(operation: PendingOperation, zone: ZoneId = ZoneId.systemDefault()): List<EntryConferenceConflict> {
        val payload = operation.entry ?: return emptyList()
        val entryQuantity = payload.items
            .filter { it.productId != null }
            .groupBy { it.productId!! }
            .mapValues { (_, items) -> items.sumOf { it.quantity } }
        if (entryQuantity.isEmpty()) return emptyList()
        val names = payload.items.filter { it.productId != null }.associate { it.productId!! to it.productName }

        val day = OffsetDateTime.parse(payload.effectiveAt).atZoneSameInstant(zone).toLocalDate()
        val start = day.atStartOfDay(zone).toInstant().toEpochMilli()
        val end = day.plusDays(1).atStartOfDay(zone).toInstant().toEpochMilli()

        // Somente o dia da Entrada, filtrado no servidor: ler todas as Conferências e todos os
        // itens e filtrar no aparelho perderia conflitos em silêncio acima do teto de linhas.
        val sameDay = client().from("conferences")
            .select(Columns.list("id", "effective_at", "physical_responsible", "deleted_at")) {
                attachRegisteredDevice()
                filter {
                    exact("deleted_at", null)
                    gte("effective_at", Instant.ofEpochMilli(start).toString())
                    lt("effective_at", Instant.ofEpochMilli(end).toString())
                }
            }
            .decodeList<ConflictConferenceRow>()
            .filter { it.deletedAt == null && epoch(it.effectiveAt) in start until end }
            .associateBy { it.id }
        if (sameDay.isEmpty()) return emptyList()

        // Até 20 Conferências por chamada: cada uma tem no máximo os Produtos da sua Categoria.
        val items = fetchByIdChunks(sameDay.keys, chunkSize = 20) { ids ->
            client().from("conference_items")
                .select(Columns.list("conference_id", "product_id", "quantity")) {
                    attachRegisteredDevice()
                    filter { isIn("conference_id", ids) }
                }
                .decodeList<ConflictConferenceItemRow>()
        }.filter { it.conferenceId in sameDay && it.productId in entryQuantity }

        return items.groupBy { it.conferenceId }
            .mapNotNull { (conferenceId, rows) ->
                val conference = sameDay[conferenceId] ?: return@mapNotNull null
                EntryConferenceConflict(
                    conferenceId = conferenceId,
                    effectiveAt = conference.effectiveAt,
                    physicalResponsible = conference.physicalResponsible.orEmpty(),
                    items = rows.map {
                        EntryConferenceConflictItem(
                            productId = it.productId,
                            productName = names[it.productId] ?: "Produto",
                            entryQuantity = entryQuantity[it.productId] ?: 0.0,
                            conferenceQuantity = it.quantity,
                        )
                    },
                )
            }
            .sortedBy { epoch(it.effectiveAt) }
    }

    suspend fun conferenceReview(operation: PendingOperation): Pair<List<ConferenceConsumptionWarning>, List<ConferenceHistoryItem>> {
        val deviceId = DeviceIdentityStore.registeredDeviceId() ?: error("Dispositivo não autorizado.")
        return when (operation.kind) {
            PendingKind.CategoryConference -> {
                val input = operation.toCategoryConferenceInput(deviceId)
                val warnings = conferences.reviewConsumption(input.effectiveAt, input.items)
                val sameDay = conferences.listSameDayCategoryConferences(input.categoryId, localDateOf(input.effectiveAt))
                warnings to sameDay
            }
            PendingKind.ProductConference -> {
                val input = operation.toProductConferenceInput(deviceId)
                listOfNotNull(
                    productConferences.reviewConsumption(
                        productId = input.productId,
                        candidateQuantity = input.quantity,
                        effectiveAt = input.effectiveAt,
                    )
                ) to emptyList()
            }
            PendingKind.Entry -> emptyList<ConferenceConsumptionWarning>() to emptyList()
        }
    }

    /** Envia com a idempotencyKey original. Em falha ambígua, confere se o registro já existe. */
    suspend fun send(operation: PendingOperation, entryEffectiveAt: String? = null): PendingSendResult {
        val deviceId = DeviceIdentityStore.registeredDeviceId()
            ?: return PendingSendResult.Failed("Este dispositivo não está autorizado para esta ação.")
        return try {
            when (operation.kind) {
                PendingKind.Entry -> entries.createEntry(operation.toEntryCreateInput(entryEffectiveAt))
                PendingKind.CategoryConference -> conferences.createCategoryConference(operation.toCategoryConferenceInput(deviceId))
                PendingKind.ProductConference -> productConferences.createProductConference(operation.toProductConferenceInput(deviceId))
            }
            sentResultAfterLocalDelete(operation)
        } catch (error: Throwable) {
            if (error is CancellationException) throw error
            val alreadySaved = runCatching {
                when (operation.kind) {
                    PendingKind.Entry ->
                        shouldReconcileEntryFailure(error) && entries.findEntryIdByIdempotencyKey(operation.idempotencyKey) != null
                    else -> conferences.findConferenceIdByIdempotencyKey(operation.idempotencyKey) != null
                }
            }.getOrDefault(false)
            if (alreadySaved) {
                sentResultAfterLocalDelete(operation)
            } else {
                val reason = if (operation.kind == PendingKind.Entry) entryErrorMessage(error) else conferenceModuleErrorMessage(error)
                PendingSendResult.Failed("$reason A pendência continua guardada neste aparelho.")
            }
        }
    }

    private fun sentMessage(operation: PendingOperation): String =
        "${operation.kindLabel} “${operation.summaryTitle}” enviada ao estoque oficial. A pendência local foi removida."

    private suspend fun sentResultAfterLocalDelete(operation: PendingOperation): PendingSendResult =
        when (PendingStore.delete(operation.localId)) {
            PendingMutationResult.Success -> PendingSendResult.Sent(sentMessage(operation))
            is PendingMutationResult.Failure -> PendingSendResult.Sent(
                "${operation.kindLabel} “${operation.summaryTitle}” foi enviada ao estoque oficial, " +
                    "mas a cópia local não pôde ser removida. Tente excluí-la novamente em Pendências locais."
            )
        }
}
