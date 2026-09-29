package com.babycatbe.nevesestoque.feature.products

import com.babycatbe.nevesestoque.ui.components.NevesIcon
import com.babycatbe.nevesestoque.ui.components.NevesIcons
import android.graphics.BitmapFactory
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Slider
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlin.math.roundToInt

const val MAX_CATEGORY_ILLUSTRATION_SIZE = 5 * 1024 * 1024

data class CategoryIllustrationOption(
    val key: String,
    val label: String,
    val glyph: String,
)

val CATEGORY_ILLUSTRATION_OPTIONS = listOf(
    CategoryIllustrationOption("panificacao", "Panificação", "🥖"),
    CategoryIllustrationOption("boleria", "Boleria", "🎂"),
    CategoryIllustrationOption("confeitaria", "Confeitaria", "🧁"),
    CategoryIllustrationOption("frios", "Frios e embutidos", "🧀"),
    CategoryIllustrationOption("manteigas", "Manteigas e requeijões", "🧈"),
    CategoryIllustrationOption("embalagens", "Embalagens", "📦"),
    CategoryIllustrationOption("etiquetas", "Bobinas e etiquetas", "🏷️"),
    CategoryIllustrationOption("descartaveis", "Descartáveis", "🥤"),
    CategoryIllustrationOption("higiene", "Higiene e proteção", "🧤"),
    CategoryIllustrationOption("flexiveis", "Embalagens flexíveis", "🛍️"),
    CategoryIllustrationOption("conveniencia", "Conveniência", "🛒"),
    CategoryIllustrationOption("limpeza", "Limpeza e descarte", "🧹"),
)

data class CategoryIllustrationDraft(
    val source: String? = null,
    val key: String? = null,
    val imageBytes: ByteArray? = null,
    val mimeType: String? = null,
    val positionX: Int = 50,
    val positionY: Int = 50,
)

data class CategoryIllustrationMetadata(
    val source: String?,
    val key: String?,
    val positionX: Int,
    val positionY: Int,
)

fun emptyCategoryIllustrationDraft() = CategoryIllustrationDraft()

fun CategoryListItem.toIllustrationDraft() = CategoryIllustrationDraft(
    source = illustrationSource,
    key = illustrationKey,
    imageBytes = illustrationBytes,
    positionX = illustrationPositionX,
    positionY = illustrationPositionY,
)

fun validateCategoryIllustrationFile(mimeType: String?, sizeBytes: Int): String? =
    when {
        mimeType !in setOf("image/jpeg", "image/png", "image/webp") ->
            "Use uma imagem JPG, PNG ou WEBP."
        sizeBytes > MAX_CATEGORY_ILLUSTRATION_SIZE ->
            "A imagem pode ter no máximo 5 MB."
        else -> null
    }

fun validateCategoryIllustrationDraft(draft: CategoryIllustrationDraft): String? {
    if (draft.positionX !in 0..100 || draft.positionY !in 0..100) {
        return "A posição da imagem deve ficar entre 0 e 100."
    }

    return when (draft.source) {
        null -> null
        "library" -> if (CATEGORY_ILLUSTRATION_OPTIONS.none { it.key == draft.key }) {
            "Escolha uma ilustração da biblioteca."
        } else {
            null
        }
        "upload" -> {
            if (draft.key != null) {
                null
            } else if (draft.imageBytes == null || draft.mimeType == null) {
                "Selecione novamente a imagem da Categoria."
            } else {
                validateCategoryIllustrationFile(draft.mimeType, draft.imageBytes.size)
            }
        }
        else -> "Ilustração inválida."
    }
}

fun categoryIllustrationExtension(mimeType: String): String? =
    when (mimeType) {
        "image/jpeg" -> "jpg"
        "image/png" -> "png"
        "image/webp" -> "webp"
        else -> null
    }

fun resolveCategoryIllustrationMetadata(
    draft: CategoryIllustrationDraft,
    uploadedKey: String? = null,
): CategoryIllustrationMetadata =
    when (draft.source) {
        "library" -> CategoryIllustrationMetadata(
            source = "library",
            key = draft.key,
            positionX = 50,
            positionY = 50,
        )
        "upload" -> CategoryIllustrationMetadata(
            source = "upload",
            key = uploadedKey ?: draft.key,
            positionX = draft.positionX,
            positionY = draft.positionY,
        )
        else -> CategoryIllustrationMetadata(
            source = null,
            key = null,
            positionX = 50,
            positionY = 50,
        )
    }

fun shouldRemovePreviousCategoryUpload(
    oldSource: String?,
    oldKey: String?,
    newKey: String?,
): Boolean = oldSource == "upload" && oldKey != null && oldKey != newKey

@Composable
fun CategoryIllustrationEditor(
    value: CategoryIllustrationDraft,
    categoryName: String,
    fileError: String?,
    enabled: Boolean,
    onChange: (CategoryIllustrationDraft) -> Unit,
    onPickUpload: () -> Unit,
) {
    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
        Text("Ilustração", fontWeight = FontWeight.Bold)
        Text(
            "Opcional. Escolha uma ilustração da biblioteca ou envie uma imagem própria.",
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            style = MaterialTheme.typography.bodySmall,
        )

        CATEGORY_ILLUSTRATION_OPTIONS.chunked(3).forEach { options ->
            Row(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                modifier = Modifier.fillMaxWidth(),
            ) {
                options.forEach { option ->
                    val selected = value.source == "library" && value.key == option.key
                    val modifier = Modifier.weight(1f)
                    if (selected) {
                        Button(
                            onClick = {
                                onChange(
                                    CategoryIllustrationDraft(
                                        source = "library",
                                        key = option.key,
                                    )
                                )
                            },
                            enabled = enabled,
                            modifier = modifier,
                        ) {
                            LibraryOptionContent(option)
                        }
                    } else {
                        OutlinedButton(
                            onClick = {
                                onChange(
                                    CategoryIllustrationDraft(
                                        source = "library",
                                        key = option.key,
                                    )
                                )
                            },
                            enabled = enabled,
                            modifier = modifier,
                        ) {
                            LibraryOptionContent(option)
                        }
                    }
                }
                repeat(3 - options.size) { Spacer(Modifier.weight(1f)) }
            }
        }

        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            OutlinedButton(onClick = onPickUpload, enabled = enabled) {
                Text("Enviar imagem")
            }
            OutlinedButton(
                onClick = { onChange(emptyCategoryIllustrationDraft()) },
                enabled = enabled,
            ) {
                Text("Sem ilustração")
            }
        }

        if (value.source == "upload") {
            Text(
                if (value.key != null) "Imagem própria atual" else "Nova imagem própria selecionada",
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                style = MaterialTheme.typography.bodySmall,
            )
            Text("Ajuste horizontal: ${value.positionX}")
            Slider(
                value = value.positionX.toFloat(),
                onValueChange = { onChange(value.copy(positionX = it.roundToInt())) },
                valueRange = 0f..100f,
                enabled = enabled,
            )
            Text("Ajuste vertical: ${value.positionY}")
            Slider(
                value = value.positionY.toFloat(),
                onValueChange = { onChange(value.copy(positionY = it.roundToInt())) },
                valueRange = 0f..100f,
                enabled = enabled,
            )
        }

        fileError?.let {
            Text(it, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall)
        }

        Text(
            "Prévia no cartão",
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            modifier = Modifier.fillMaxWidth(),
        ) {
            CategoryIllustrationVisual(
                source = value.source,
                key = value.key,
                imageBytes = value.imageBytes,
                positionX = value.positionX,
                positionY = value.positionY,
                modifier = Modifier.size(64.dp),
            )
            Column {
                Text(categoryName.trim().ifBlank { "NOME DA CATEGORIA" }, fontWeight = FontWeight.Bold)
                Text("0 Produtos", color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
    }
}

@Composable
private fun LibraryOptionContent(option: CategoryIllustrationOption) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        NevesIcon(categoryIcon(option.key))
        Text(
            option.label,
            style = MaterialTheme.typography.labelSmall,
            maxLines = 2,
        )
    }
}

@Composable
fun CategoryIllustrationVisual(
    source: String?,
    key: String?,
    imageBytes: ByteArray?,
    positionX: Int,
    positionY: Int,
    modifier: Modifier = Modifier,
) {
    val shape = RoundedCornerShape(12.dp)
    val bitmap = remember(imageBytes) {
        imageBytes?.let(::decodeCategoryPreview)?.asImageBitmap()
    }

    Box(
        contentAlignment = Alignment.Center,
        modifier = modifier
            .clip(shape)
            .background(
                if (source == "upload") {
                    MaterialTheme.colorScheme.surfaceVariant
                } else {
                    MaterialTheme.colorScheme.primaryContainer
                }
            ),
    ) {
        when {
            source == "upload" && bitmap != null -> {
                Image(
                    bitmap = bitmap,
                    contentDescription = null,
                    contentScale = ContentScale.Crop,
                    alignment = PercentAlignment(positionX, positionY),
                    modifier = Modifier.fillMaxSize(),
                )
            }
            source == "library" && key != null -> {
                NevesIcon(categoryIcon(key), tint = MaterialTheme.colorScheme.primary)
            }
            source == "upload" -> {
                NevesIcon(NevesIcons.Error, "Imagem indisponível", tint = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            else -> NevesIcon(NevesIcons.Inventory, tint = MaterialTheme.colorScheme.primary)
        }
    }
}

private fun decodeCategoryPreview(bytes: ByteArray): android.graphics.Bitmap? {
    val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
    BitmapFactory.decodeByteArray(bytes, 0, bytes.size, bounds)
    var sample = 1
    while (bounds.outWidth / sample > 1024 || bounds.outHeight / sample > 1024) {
        sample *= 2
    }
    return BitmapFactory.decodeByteArray(
        bytes,
        0,
        bytes.size,
        BitmapFactory.Options().apply { inSampleSize = sample },
    )
}

private class PercentAlignment(
    x: Int,
    y: Int,
) : Alignment {
    private val xFraction = x.coerceIn(0, 100) / 100f
    private val yFraction = y.coerceIn(0, 100) / 100f

    override fun align(
        size: IntSize,
        space: IntSize,
        layoutDirection: LayoutDirection,
    ): IntOffset = IntOffset(
        x = ((space.width - size.width) * xFraction).roundToInt(),
        y = ((space.height - size.height) * yFraction).roundToInt(),
    )
}


private fun categoryIcon(key: String): Int = when (key) {
    "panificacao" -> NevesIcons.Bread
    "boleria", "confeitaria" -> NevesIcons.Cake
    "frios", "manteigas" -> NevesIcons.Cold
    "etiquetas" -> NevesIcons.Label
    "descartaveis" -> NevesIcons.Cup
    "higiene", "limpeza" -> NevesIcons.Hygiene
    "flexiveis" -> NevesIcons.Bag
    "conveniencia" -> NevesIcons.Cart
    else -> NevesIcons.Inventory
}
