package uz.alifbo.keyboard

import uz.alifbo.converter.AlifboConverter

enum class Action { SHIFT, BACKSPACE, SYMBOLS, SYMBOLS2, LETTERS, LANGUAGE, SPACE, ENTER }

enum class Page { UZ, RU, SYMBOLS, SYMBOLS2 }

/** Клавиша: либо символ [text], либо действие [action]. [width] — относительная ширина в ряду. */
data class Key(
    val text: String? = null,
    val action: Action? = null,
    val longPress: String? = null,
    val width: Float = 1f,
    val isNewLetter: Boolean = false,
)

/** Раскладки утверждены по прототипу: по 11 клавиш в ряду, Ş Ç Ö Ğ на основных рядах. */
object Layouts {
    val TUTUQ = AlifboConverter.TUTUQ.toString()

    private val newLetters = setOf("ğ", "ş", "ö", "ç")
    private val letterLongPress = mapOf("е" to "ё", "ь" to "ъ", TUTUQ to "'")
    private val digits = "1234567890".map { it.toString() }

    private fun row(keys: String, digitsOnLongPress: Boolean = false) =
        keys.split(" ").mapIndexed { i, s ->
            val digit = if (digitsOnLongPress && i < digits.size) digits[i] else null
            Key(text = s, longPress = letterLongPress[s] ?: digit, isNewLetter = s in newLetters)
        }

    private fun bottom(switchKey: Action) = listOf(
        Key(action = switchKey, width = 1.5f),
        Key(action = Action.LANGUAGE),
        Key(text = ","),
        Key(action = Action.SPACE, width = 5f),
        Key(text = "."),
        Key(action = Action.ENTER, width = 1.5f),
    )

    private fun withEdges(left: Action, keys: List<Key>) =
        listOf(Key(action = left, width = 1.5f)) + keys + Key(action = Action.BACKSPACE, width = 1.5f)

    private val uz = listOf(
        row("q w e r t y u i o p ğ", digitsOnLongPress = true),
        row("a s d f g h j k l ş $TUTUQ"),
        withEdges(Action.SHIFT, row("z x c v b n m ö ç")),
        bottom(Action.SYMBOLS),
    )

    private val ru = listOf(
        row("й ц у к е н г ш щ з х", digitsOnLongPress = true),
        row("ф ы в а п р о л д ж э"),
        withEdges(Action.SHIFT, row("я ч с м и т ь б ю")),
        bottom(Action.SYMBOLS),
    )

    private val symbols = listOf(
        row("1 2 3 4 5 6 7 8 9 0"),
        row("@ # $ % & - + ( ) /"),
        withEdges(Action.SYMBOLS2, row("* \" ' : ; ! ?")),
        bottom(Action.LETTERS),
    )

    private val symbols2 = listOf(
        row("~ ` | • √ π ÷ × ¶ ∆"),
        row("£ € ¥ ^ ° = { } [ ]"),
        withEdges(Action.SYMBOLS, row("\\ < > _ © ® ™")),
        bottom(Action.LETTERS),
    )

    fun forPage(page: Page): List<List<Key>> = when (page) {
        Page.UZ -> uz
        Page.RU -> ru
        Page.SYMBOLS -> symbols
        Page.SYMBOLS2 -> symbols2
    }
}
