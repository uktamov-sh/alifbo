package uz.alifbo.converter

import org.junit.jupiter.api.DynamicTest
import org.junit.jupiter.api.TestFactory
import java.io.File
import java.util.Locale
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class AlifboConverterTest {

    private data class Case(val line: Int, val input: String, val expected: String)

    private fun loadCases(): List<Case> {
        val path = System.getProperty("alifbo.cases") ?: error("Не задан путь к spec/cases.txt")
        return File(path).readLines(Charsets.UTF_8).mapIndexedNotNull { i, raw ->
            val line = raw.trim()
            if (line.isEmpty() || line.startsWith("#")) return@mapIndexedNotNull null
            val parts = line.split(" → ")
            require(parts.size == 2) { "cases.txt:${i + 1}: ожидается «вход → результат»" }
            Case(i + 1, parts[0].replace("\\n", "\n"), parts[1].replace("\\n", "\n"))
        }
    }

    @TestFactory
    fun `эталонные примеры из spec`(): List<DynamicTest> {
        val cases = loadCases()
        assertTrue(cases.size > 50, "Слишком мало примеров: ${cases.size}")
        return cases.map { c ->
            DynamicTest.dynamicTest("стр. ${c.line}: ${c.input}") {
                assertEquals(c.expected, AlifboConverter.toNewLatin(c.input))
            }
        }
    }

    // Слово вроде «Ishoq» без Ş/Ç не отличить от старой латиницы, такие результаты пропускаем.
    @Test
    fun `повторная конвертация ничего не меняет`() {
        val ambiguous = Regex("[sScC][hH]")
        val newMarker = Regex("[ŞşÇç]")
        loadCases().filter { !ambiguous.containsMatchIn(it.expected) || newMarker.containsMatchIn(it.expected) }.forEach { c ->
            assertEquals(c.expected, AlifboConverter.toNewLatin(c.expected), "стр. ${c.line}")
        }
    }

    @Test
    fun `турецкая локаль системы не даёт İ`() {
        val saved = Locale.getDefault()
        try {
            Locale.setDefault(Locale.forLanguageTag("tr-TR"))
            assertEquals("ISHOQ", AlifboConverter.toNewLatin("IS'HOQ"))
            assertEquals("Ishoq", AlifboConverter.toNewLatin("Исҳоқ"))
            assertEquals("ÖZBEKISTON", AlifboConverter.toNewLatin("ЎЗБЕКИСТОН"))
        } finally {
            Locale.setDefault(saved)
        }
    }

    @Test
    fun `по кнопке eski→yangi текст всегда считается старым`() {
        assertEquals("Toşkent va Işoq", AlifboConverter.toNewLatin("Toşkent va Ishoq", assumeOldLatin = true))
        assertEquals("Şahar, çoy va şakar", AlifboConverter.toNewLatin("Şahar, choy va shakar", assumeOldLatin = true))
        // Без режима текст с Ş/Ç считается новым, и sh/ch в нём не трогаются.
        assertEquals("Şahar, choy va shakar", AlifboConverter.toNewLatin("Şahar, choy va shakar"))
        // Остальные правила не меняются.
        assertEquals("Ishoq maʼno", AlifboConverter.toNewLatin("Is'hoq ma'no", assumeOldLatin = true))
        assertEquals("https://shop.uz", AlifboConverter.toNewLatin("https://shop.uz", assumeOldLatin = true))
    }

    @Test
    fun `пустая строка и пробелы сохраняются`() {
        assertEquals("", AlifboConverter.toNewLatin(""))
        assertEquals("  çoy\t\n ", AlifboConverter.toNewLatin("  choy\t\n "))
    }
}
