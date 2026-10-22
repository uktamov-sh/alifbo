package uz.alifbo.converter

/**
 * Переписывает текст старой латиницей или кириллицей на новый узбекский алфавит
 * (закон O‘RQ-1184 от 21.10.2026). Правила и эталонные примеры: spec/converter-rules.md, spec/cases.txt.
 *
 * Работает только с переданной строкой: ничего не хранит, не пишет в лог и не обращается к сети.
 */
object AlifboConverter {

    /** Тутук белгиси. */
    const val TUTUQ = 'ʼ'

    private const val APOSTROPHES = "'‘’ʻʼ`´"
    private const val SEPARATE = '\u0000'

    private val chunkRegex = Regex("""\s+|\S+""")
    private val wordRegex = Regex("""[\p{L}\p{M}$APOSTROPHES]+""")
    private val emailRegex = Regex("""[^@\s]+@[^@\s]+\.[^@\s]+""")

    private val oldOG = Regex("""([OoGg])([ʻ‘]|[$APOSTROPHES](?=[\p{L}$APOSTROPHES]))""")
    private val separateSH = Regex("""([SsCc])[$APOSTROPHES](?=[Hh])""")
    private val digraph = Regex("""[SsCc][Hh]""")
    private val innerApostrophe = Regex("""(?<=\p{L})[$APOSTROPHES](?=\p{L})""")
    private val newShCh = Regex("""[ŞşÇç]""")

    private val cyrillicLetter = Regex("""[а-яёўқғҳА-ЯЁЎҚҒҲ]""")
    private const val CYR_VOWELS = "аеёиоуўэюяы"
    private val cyrMap = mapOf(
        'а' to "a", 'б' to "b", 'в' to "v", 'г' to "g", 'ғ' to "ğ", 'д' to "d", 'ё' to "yo",
        'ж' to "j", 'з' to "z", 'и' to "i", 'й' to "y", 'к' to "k", 'қ' to "q", 'л' to "l",
        'м' to "m", 'н' to "n", 'о' to "o", 'ў' to "ö", 'п' to "p", 'р' to "r", 'с' to "s",
        'т' to "t", 'у' to "u", 'ф' to "f", 'х' to "x", 'ҳ' to "h", 'ч' to "ç", 'ш' to "ş",
        'щ' to "ş", 'ъ' to TUTUQ.toString(), 'ь' to "", 'ы' to "i", 'э' to "e", 'ю' to "yu",
        'я' to "ya",
    )

    /**
     * @param assumeOldLatin считать весь латинский текст старым, даже если в нём уже есть Ş или Ç.
     *   Так работает кнопка «eski→yangi» на клавиатуре: её нажимают именно для старого текста.
     */
    fun toNewLatin(text: String, assumeOldLatin: Boolean = false): String {
        // Если в тексте уже есть Ş или Ç, он написан новым алфавитом и sh/ch в нём — две буквы.
        val keepShCh = !assumeOldLatin && newShCh.containsMatchIn(text)
        return chunkRegex.replace(text) { chunk ->
            val s = chunk.value
            if (s.isBlank() || isAddress(s)) s
            else wordRegex.replace(s) { w ->
                if (cyrillicLetter.containsMatchIn(w.value)) cyrillicWord(w.value)
                else oldLatinWord(w.value, keepShCh)
            }
        }
    }

    private fun isAddress(s: String) =
        "://" in s || s.startsWith("www.", ignoreCase = true) || emailRegex.matches(s)

    private fun oldLatinWord(word: String, keepShCh: Boolean): String {
        var w = oldOG.replace(word) { m ->
            when (m.groupValues[1]) {
                "O" -> "Ö"; "o" -> "ö"; "G" -> "Ğ"; else -> "ğ"
            }
        }
        if (!keepShCh) {
            w = separateSH.replace(w) { m -> m.groupValues[1] + SEPARATE }
            w = digraph.replace(w) { m ->
                when (m.value[0]) {
                    'S' -> "Ş"; 's' -> "ş"; 'C' -> "Ç"; else -> "ç"
                }
            }
            w = w.replace(SEPARATE.toString(), "")
        }
        return innerApostrophe.replace(w, TUTUQ.toString())
    }

    private fun cyrillicWord(word: String): String {
        val cased = word.filter { it.isLetter() && it.lowercaseChar() != it.uppercaseChar() }
        val allCaps = cased.length >= 2 && cased.none { it.isLowerCase() }
        val out = StringBuilder()
        word.forEachIndexed { i, ch ->
            val lo = ch.lowercaseChar()
            val prev = if (i > 0) word[i - 1].lowercaseChar() else null
            val atStart = prev == null || !cyrillicLetter.matches(prev.toString())
            val afterVowel = prev != null && prev in CYR_VOWELS
            val latin = when (lo) {
                'е' -> if (atStart || afterVowel || prev == 'ъ' || prev == 'ь') "ye" else "e"
                'ц' -> if (afterVowel) "ts" else "s"
                else -> cyrMap[lo] ?: ch.toString()
            }
            // uppercase() в Kotlin не зависит от языка системы: i не станет İ даже на турецкой локали.
            out.append(
                when {
                    !ch.isUpperCase() || latin.isEmpty() -> latin
                    allCaps -> latin.uppercase()
                    else -> latin.replaceFirstChar { it.uppercaseChar() }
                }
            )
        }
        return out.toString()
    }
}
