package uz.alifbo.keyboard

/** Подсказки по началу слова. Словарь — список слов по убыванию частоты, работает только на устройстве. */
class Suggester(private val words: List<String>) {

    private val lowered = words.map { it.lowercase() }

    fun suggest(prefix: String, limit: Int = 3): List<String> {
        if (prefix.isEmpty()) return emptyList()
        val p = prefix.lowercase()
        val allCaps = prefix.length >= 2 && prefix.none { it.isLowerCase() }
        val capitalized = prefix[0].isUpperCase()
        val result = ArrayList<String>(limit)
        for (i in words.indices) {
            if (result.size == limit) break
            val w = lowered[i]
            if (w.length > p.length && w.startsWith(p)) {
                // lowercase()/uppercase() в Kotlin не зависят от локали: i не станет İ.
                result += when {
                    allCaps -> words[i].uppercase()
                    capitalized -> words[i].replaceFirstChar { it.uppercaseChar() }
                    else -> words[i]
                }
            }
        }
        return result
    }

    companion object {
        private val tutuq = Layouts.TUTUQ[0]

        fun currentWord(beforeCursor: CharSequence): String {
            var start = beforeCursor.length
            while (start > 0 && (beforeCursor[start - 1].isLetter() || beforeCursor[start - 1] == tutuq)) start--
            return beforeCursor.substring(start)
        }

        fun parse(text: String): List<String> =
            text.lineSequence().map { it.trim() }.filter { it.isNotEmpty() && !it.startsWith("#") }.toList()
    }
}
