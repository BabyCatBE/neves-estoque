package com.babycatbe.nevesestoque.feature.products

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class CategoryIllustrationTest {
    @Test
    fun fileValidationMatchesOfficialMimeTypesAndLimit() {
        assertNull(validateCategoryIllustrationFile("image/jpeg", 100))
        assertNull(validateCategoryIllustrationFile("image/png", 100))
        assertNull(validateCategoryIllustrationFile("image/webp", MAX_CATEGORY_ILLUSTRATION_SIZE))

        assertEquals(
            "Use uma imagem JPG, PNG ou WEBP.",
            validateCategoryIllustrationFile("image/gif", 100),
        )
        assertEquals(
            "A imagem pode ter no máximo 5 MB.",
            validateCategoryIllustrationFile("image/jpeg", MAX_CATEGORY_ILLUSTRATION_SIZE + 1),
        )
    }

    @Test
    fun librarySelectionUsesOfficialKeyAndCentersPosition() {
        val draft = CategoryIllustrationDraft(
            source = "library",
            key = "panificacao",
            positionX = 4,
            positionY = 92,
        )
        assertNull(validateCategoryIllustrationDraft(draft))

        val metadata = resolveCategoryIllustrationMetadata(draft)

        assertEquals("library", metadata.source)
        assertEquals("panificacao", metadata.key)
        assertEquals(50, metadata.positionX)
        assertEquals(50, metadata.positionY)
    }

    @Test
    fun existingUploadKeepsKeyAndPositionWithoutReupload() {
        val draft = CategoryIllustrationDraft(
            source = "upload",
            key = "11111111-1111-4111-8111-111111111111/22222222-2222-4222-8222-222222222222.jpg",
            positionX = 0,
            positionY = 52,
        )
        assertNull(validateCategoryIllustrationDraft(draft))

        val metadata = resolveCategoryIllustrationMetadata(draft)

        assertEquals("upload", metadata.source)
        assertEquals(draft.key, metadata.key)
        assertEquals(0, metadata.positionX)
        assertEquals(52, metadata.positionY)
    }

    @Test
    fun newUploadRequiresBytesAndSupportedMimeType() {
        val missing = CategoryIllustrationDraft(source = "upload")
        assertEquals(
            "Selecione novamente a imagem da Categoria.",
            validateCategoryIllustrationDraft(missing),
        )

        val selected = CategoryIllustrationDraft(
            source = "upload",
            imageBytes = byteArrayOf(1, 2, 3),
            mimeType = "image/png",
        )
        assertNull(validateCategoryIllustrationDraft(selected))
        assertEquals("png", categoryIllustrationExtension("image/png"))
    }

    @Test
    fun oldUploadIsRemovedOnlyAfterKeyChanges() {
        val old = "category/old.jpg"
        assertFalse(shouldRemovePreviousCategoryUpload("library", old, "new"))
        assertFalse(shouldRemovePreviousCategoryUpload("upload", old, old))
        assertTrue(shouldRemovePreviousCategoryUpload("upload", old, "new"))
        assertTrue(shouldRemovePreviousCategoryUpload("upload", old, null))
    }
}
