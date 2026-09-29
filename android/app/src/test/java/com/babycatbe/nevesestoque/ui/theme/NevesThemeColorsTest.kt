package com.babycatbe.nevesestoque.ui.theme

import androidx.compose.ui.graphics.Color
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertSame
import org.junit.Test

class NevesThemeColorsTest {
    @Test
    fun `extremos da transicao devolvem os esquemas exatos`() {
        assertSame(NevesLightColors, lerpColorScheme(NevesLightColors, NevesDarkColors, 0f))
        assertSame(NevesDarkColors, lerpColorScheme(NevesLightColors, NevesDarkColors, 1f))
        assertSame(LightNevesPalette, lerpNevesPalette(LightNevesPalette, DarkNevesPalette, 0f))
        assertSame(DarkNevesPalette, lerpNevesPalette(LightNevesPalette, DarkNevesPalette, 1f))
    }

    @Test
    fun `tema claro preserva os valores aprovados`() {
        assertEquals(Color(0xFFB91C1C), NevesLightColors.primary)
        assertEquals(Color(0xFFF7F7F8), NevesLightColors.background)
        assertEquals(Color.White, NevesLightColors.surface)
        assertEquals(Color(0xFF09090B), LightNevesPalette.header)
        assertEquals(Color(0xFFF8DADA), LightNevesPalette.cardBorder)
        assertEquals(Color(0xFF78350F), LightNevesPalette.offlineBanner)
        assertEquals(Color.White, LightNevesPalette.onOfflineBanner)
    }

    @Test
    fun `tema escuro usa a paleta grafite quente aprovada`() {
        assertEquals(Color(0xFF181614), NevesDarkColors.background)
        assertEquals(Color(0xFF211E1B), NevesDarkColors.surface)
        assertEquals(Color(0xFF2A2622), NevesDarkColors.surfaceVariant)
        assertEquals(Color(0xFF302B27), NevesDarkColors.surfaceContainerHigh)
        assertEquals(Color(0xFFF4F1ED), NevesDarkColors.onSurface)
        assertEquals(Color(0xFFB8B0A7), NevesDarkColors.onSurfaceVariant)
    }

    @Test
    fun `meio da transicao fica entre claro e escuro`() {
        val middle = lerpColorScheme(NevesLightColors, NevesDarkColors, 0.5f)
        assertNotEquals(NevesLightColors.background, middle.background)
        assertNotEquals(NevesDarkColors.background, middle.background)
        val palette = lerpNevesPalette(LightNevesPalette, DarkNevesPalette, 0.5f)
        assertNotEquals(LightNevesPalette.cardBorder, palette.cardBorder)
        assertNotEquals(DarkNevesPalette.cardBorder, palette.cardBorder)
    }
}
