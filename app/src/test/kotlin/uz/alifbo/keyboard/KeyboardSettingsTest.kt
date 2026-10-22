package uz.alifbo.keyboard

import org.junit.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class KeyboardSettingsTest {

    @Test
    fun `по умолчанию вибрация, подсказки и показ буквы включены, звук выключен`() {
        val s = KeyboardSettings.from(emptyMap<String, Any>())
        assertTrue(s.vibration)
        assertTrue(s.preview)
        assertTrue(s.suggestions)
        assertFalse(s.sound)
        assertEquals(KeyboardSettings.Height.MEDIUM, s.height)
    }

    @Test
    fun `читает сохранённые значения`() {
        val s = KeyboardSettings.from(
            mapOf("vibration" to false, "sound" to true, "preview" to false, "suggestions" to false, "height" to "LARGE")
        )
        assertFalse(s.vibration)
        assertTrue(s.sound)
        assertFalse(s.preview)
        assertFalse(s.suggestions)
        assertEquals(KeyboardSettings.Height.LARGE, s.height)
    }

    @Test
    fun `неизвестная высота не ломает клавиатуру`() {
        assertEquals(KeyboardSettings.Height.MEDIUM, KeyboardSettings.from(mapOf("height" to "HUGE")).height)
    }

    @Test
    fun `высоты клавиш идут по возрастанию`() {
        val dp = KeyboardSettings.Height.entries.map { it.keyDp }
        assertEquals(dp.sorted(), dp)
    }
}
