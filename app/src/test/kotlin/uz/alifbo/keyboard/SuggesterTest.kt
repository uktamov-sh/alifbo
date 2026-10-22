package uz.alifbo.keyboard

import org.junit.Test
import java.util.Locale
import kotlin.test.assertEquals

class SuggesterTest {

    private val s = Suggester(listOf("şahar", "şanba", "şaxsiy", "şu", "Toşkent", "salom", "ish"))

    @Test
    fun `подсказывает слова по началу в порядке частоты`() {
        assertEquals(listOf("şahar", "şanba", "şaxsiy"), s.suggest("şa"))
    }

    @Test
    fun `не предлагает уже набранное слово целиком`() {
        assertEquals(emptyList(), s.suggest("şu"))
    }

    @Test
    fun `повторяет регистр набранного`() {
        assertEquals(listOf("Şahar", "Şanba", "Şaxsiy"), s.suggest("Şa"))
        assertEquals(listOf("ŞAHAR", "ŞANBA", "ŞAXSIY"), s.suggest("ŞA"))
        assertEquals(listOf("Toşkent"), s.suggest("toş"))
    }

    @Test
    fun `заглавные не зависят от турецкой локали`() {
        val saved = Locale.getDefault()
        try {
            Locale.setDefault(Locale.forLanguageTag("tr-TR"))
            assertEquals(listOf("ŞAXSIY"), s.suggest("ŞAX"))
            assertEquals(listOf("ish"), s.suggest("I").map { it.lowercase() })
        } finally {
            Locale.setDefault(saved)
        }
    }

    @Test
    fun `текущее слово берётся до курсора вместе с тутуком`() {
        assertEquals("maʼn", Suggester.currentWord("Bu maʼn"))
        assertEquals("", Suggester.currentWord("salom "))
        assertEquals("Şa", Suggester.currentWord("(Şa"))
        assertEquals("", Suggester.currentWord(""))
    }

    @Test
    fun `читает словарь из текста, пропуская комментарии и пустые строки`() {
        val d = Suggester.parse("# комментарий\nsalom\n\n  rahmat  \n")
        assertEquals(listOf("salom", "rahmat"), d)
    }
}
