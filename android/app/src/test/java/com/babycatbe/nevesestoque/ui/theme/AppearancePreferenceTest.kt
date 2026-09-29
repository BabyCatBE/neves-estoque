package com.babycatbe.nevesestoque.ui.theme

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class AppearancePreferenceTest {
    private class MemoryStorage(var value: String? = null) : AppearanceStorage {
        var writes = 0
        override fun read(): String? = value
        override fun write(value: String) {
            this.value = value
            writes++
        }
    }

    @Test
    fun `sem preferencia salva abre no tema claro`() {
        val storage = MemoryStorage()
        assertEquals(AppearanceMode.Light, AppearancePreference(storage).mode.value)
        assertNull(storage.value)
    }

    @Test
    fun `valor desconhecido cai no tema claro`() {
        assertEquals(AppearanceMode.Light, AppearanceMode.fromStorage("system"))
        assertEquals(AppearanceMode.Light, AppearanceMode.fromStorage(""))
    }

    @Test
    fun `escolher escuro muda imediatamente e persiste`() {
        val storage = MemoryStorage()
        val preference = AppearancePreference(storage)
        preference.select(AppearanceMode.Dark)
        assertEquals(AppearanceMode.Dark, preference.mode.value)
        assertEquals("dark", storage.value)
    }

    @Test
    fun `nova sessao restaura a escolha salva`() {
        val storage = MemoryStorage()
        AppearancePreference(storage).select(AppearanceMode.Dark)
        assertEquals(AppearanceMode.Dark, AppearancePreference(storage).mode.value)
        AppearancePreference(storage).select(AppearanceMode.Light)
        assertEquals(AppearanceMode.Light, AppearancePreference(storage).mode.value)
        assertEquals("light", storage.value)
    }

    @Test
    fun `repetir a mesma escolha nao regrava`() {
        val storage = MemoryStorage("dark")
        val preference = AppearancePreference(storage)
        preference.select(AppearanceMode.Dark)
        assertEquals(0, storage.writes)
    }
}
