package uz.alifbo.keyboard

import org.junit.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class SpellCheckerTest {

    private val sc = SpellChecker(
        listOf("va", "bilan", "maktab", "maktub", "kitob", "özbekiston", "şahar", "sahar", "işlab", "telefon",
            "bilak", "qilmoq", "uka", "buvi", "rahmat", "prezident", "bilan")
    )

    @Test
    fun `словарные и короткие слова верны`() {
        assertTrue(sc.isKnown("maktab"))
        assertTrue(sc.isKnown("Maktab"))
        assertTrue(sc.isKnown("va"))
        assertTrue(sc.isKnown("xy"))
        assertTrue(sc.isKnown("2026"))
    }

    @Test
    fun `слова с суффиксами верны`() {
        assertTrue(sc.isKnown("kitoblarimizdagi"))
        assertTrue(sc.isKnown("maktabga"))
        assertTrue(sc.isKnown("buvimning"))
        assertTrue(sc.isKnown("ukam"))
        assertTrue(sc.isKnown("bilagi"))        // k → g
        assertTrue(sc.isKnown("qilamiz"))       // основа qil через qilmoq
    }

    @Test
    fun `опечатки не верны и получают исправления`() {
        assertFalse(sc.isKnown("maktb"))
        assertEquals(listOf("maktab", "maktub"), sc.corrections("maktb"))
        assertFalse(sc.isKnown("kitb"))
        assertEquals("kitob", sc.corrections("kitb").first())
        assertEquals(listOf("prezident"), sc.corrections("prezdent"))
        assertEquals(listOf("telefon"), sc.corrections("tilfon"))
    }

    @Test
    fun `старая латиница исправляется первой`() {
        assertEquals(listOf("şahar", "sahar"), sc.corrections("shahar"))
        assertEquals("işlab", sc.corrections("ishlab").first())
        assertEquals("özbekiston", sc.corrections("o'zbekiston").first())
    }

    @Test
    fun `регистр исправления как у набранного слова`() {
        assertEquals("Maktab", sc.corrections("Maktb").first())
        assertEquals("MAKTAB", sc.corrections("MAKTB").first())
        assertEquals("Şahar", sc.corrections("Shahar").first())
    }

    @Test
    fun `личный словарь делает слово верным`() {
        assertFalse(sc.isKnown("Alifbochi"))
        assertTrue(sc.isKnown("Alifbochi", setOf("alifbochi")))
        assertTrue(sc.isKnown("alifbochilar", setOf("alifbochi")))
    }

    @Test
    fun `законченное слово перед пробелом`() {
        assertEquals(Suggester.Companion.Completed("kitb", " ", 4), Suggester.lastCompletedWord("Men kitb "))
        assertEquals(Suggester.Companion.Completed("kitb", ", ", 4), Suggester.lastCompletedWord("Men kitb, "))
        assertEquals(null, Suggester.lastCompletedWord("Men kitb"))
        assertEquals(null, Suggester.lastCompletedWord("Men kitb  "))
    }

    @Test
    fun `начало предложения`() {
        assertTrue(Suggester.startsSentence("Ali ", 0))
        assertTrue(Suggester.startsSentence("Keldi. Ali ", 7))
        assertFalse(Suggester.startsSentence("Men Ali ", 4))
    }
}
