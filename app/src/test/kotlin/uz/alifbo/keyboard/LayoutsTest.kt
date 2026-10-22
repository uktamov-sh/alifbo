package uz.alifbo.keyboard

import org.junit.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class LayoutsTest {

    private fun texts(page: Page) = Layouts.forPage(page).flatten().mapNotNull { it.text }

    @Test
    fun `узбекская раскладка содержит весь алфавит закона и тутук`() {
        val alphabet = "a b d e f g ğ h i j k l m n o ö p q r s ş ç t u v x y z".split(" ")
        val keys = texts(Page.UZ)
        alphabet.forEach { assertTrue(it in keys, "Нет буквы $it") }
        assertTrue(Layouts.TUTUQ in keys, "Нет тутук белгиси")
    }

    @Test
    fun `новые буквы стоят на основных рядах, а не на долгом нажатии`() {
        val rows = Layouts.forPage(Page.UZ).take(3)
        listOf("ğ", "ş", "ö", "ç").forEach { l ->
            val key = rows.flatten().single { it.text == l }
            assertTrue(key.isNewLetter, "$l должна быть выделена как новая буква")
        }
    }

    @Test
    fun `русская раскладка содержит все 33 буквы с учётом долгих нажатий`() {
        val keys = Layouts.forPage(Page.RU).flatten()
        val all = keys.mapNotNull { it.text } + keys.mapNotNull { it.longPress }
        "абвгдеёжзийклмнопрстуфхцчшщъыьэюя".forEach { assertTrue(it.toString() in all, "Нет буквы $it") }
        assertEquals("ё", keys.single { it.text == "е" }.longPress)
        assertEquals("ъ", keys.single { it.text == "ь" }.longPress)
    }

    @Test
    fun `на страницах букв нет повторяющихся клавиш`() {
        listOf(Page.UZ, Page.RU, Page.SYMBOLS, Page.SYMBOLS2).forEach { page ->
            val t = texts(page)
            assertEquals(t.size, t.toSet().size, "Повтор на странице $page: ${t.groupBy { it }.filter { it.value.size > 1 }.keys}")
        }
    }

    @Test
    fun `на каждой странице есть пробел, удаление, ввод и выход на другие страницы`() {
        listOf(Page.UZ, Page.RU, Page.SYMBOLS, Page.SYMBOLS2).forEach { page ->
            val actions = Layouts.forPage(page).flatten().mapNotNull { it.action }
            listOf(Action.SPACE, Action.BACKSPACE, Action.ENTER, Action.LANGUAGE).forEach {
                assertTrue(it in actions, "На странице $page нет $it")
            }
            assertTrue(Action.SYMBOLS in actions || Action.LETTERS in actions)
        }
    }

    @Test
    fun `верхний ряд узбекской раскладки даёт цифры долгим нажатием`() {
        val top = Layouts.forPage(Page.UZ)[0]
        assertEquals("1234567890".map { it.toString() }, top.take(10).map { it.longPress })
    }
}
