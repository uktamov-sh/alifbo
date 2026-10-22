package uz.alifbo.keyboard

import android.text.InputType.TYPE_CLASS_NUMBER
import android.text.InputType.TYPE_CLASS_PHONE
import android.text.InputType.TYPE_CLASS_TEXT
import android.text.InputType.TYPE_NUMBER_VARIATION_PASSWORD
import android.text.InputType.TYPE_TEXT_FLAG_CAP_SENTENCES
import android.text.InputType.TYPE_TEXT_FLAG_NO_SUGGESTIONS
import android.text.InputType.TYPE_TEXT_VARIATION_EMAIL_ADDRESS
import android.text.InputType.TYPE_TEXT_VARIATION_PASSWORD
import android.text.InputType.TYPE_TEXT_VARIATION_URI
import android.text.InputType.TYPE_TEXT_VARIATION_VISIBLE_PASSWORD
import android.text.InputType.TYPE_TEXT_VARIATION_WEB_PASSWORD
import org.junit.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class FieldPolicyTest {

    @Test
    fun `все виды паролей считаются секретными полями`() {
        listOf(
            TYPE_CLASS_TEXT or TYPE_TEXT_VARIATION_PASSWORD,
            TYPE_CLASS_TEXT or TYPE_TEXT_VARIATION_VISIBLE_PASSWORD,
            TYPE_CLASS_TEXT or TYPE_TEXT_VARIATION_WEB_PASSWORD,
            TYPE_CLASS_NUMBER or TYPE_NUMBER_VARIATION_PASSWORD,
        ).forEach { type ->
            val p = FieldPolicy.of(type)
            assertTrue(p.sensitive, "Тип $type")
            assertFalse(p.suggestions, "Тип $type: подсказки должны быть выключены")
            assertFalse(p.converter, "Тип $type: конвертер должен быть скрыт")
        }
    }

    @Test
    fun `обычный текст получает подсказки и конвертер`() {
        val p = FieldPolicy.of(TYPE_CLASS_TEXT or TYPE_TEXT_FLAG_CAP_SENTENCES)
        assertFalse(p.sensitive)
        assertTrue(p.suggestions)
        assertTrue(p.converter)
        assertFalse(p.startOnSymbols)
    }

    @Test
    fun `поле, где приложение запретило подсказки, их не получает`() {
        val p = FieldPolicy.of(TYPE_CLASS_TEXT or TYPE_TEXT_FLAG_NO_SUGGESTIONS)
        assertFalse(p.suggestions)
        assertTrue(p.converter)
    }

    @Test
    fun `адреса почты и сайтов без подсказок и конвертера`() {
        listOf(TYPE_TEXT_VARIATION_EMAIL_ADDRESS, TYPE_TEXT_VARIATION_URI).forEach { v ->
            val p = FieldPolicy.of(TYPE_CLASS_TEXT or v)
            assertFalse(p.suggestions)
            assertFalse(p.converter)
        }
    }

    @Test
    fun `числа и телефон открываются на цифрах без подсказок`() {
        listOf(TYPE_CLASS_NUMBER, TYPE_CLASS_PHONE).forEach { type ->
            val p = FieldPolicy.of(type)
            assertTrue(p.startOnSymbols)
            assertFalse(p.suggestions)
            assertFalse(p.converter)
        }
    }
}
