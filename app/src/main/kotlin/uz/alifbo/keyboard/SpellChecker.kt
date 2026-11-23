package uz.alifbo.keyboard

/**
 * Проверка узбекского слова по словарю частот и варианты исправления. Работает только на устройстве.
 *
 * Узбекский агглютинативный: «kitoblarimizdagi» в словаре может не быть, поэтому слово считается
 * верным, если после отсечения суффиксов остаётся основа из словаря (kitob + lar + imiz + dagi).
 * Глагольная основа ищется и через формы словаря: qayğur → qayğurmoq, qayğurib.
 * Ошибки только подсказываются, слово само не заменяется.
 */
class SpellChecker(words: List<String>) {

    private val rank = HashMap<String, Int>(words.size * 2)

    init {
        words.forEachIndexed { i, w -> rank.putIfAbsent(w.lowercase(), i) }
    }

    /** Верно ли слово. Короткие слова (до 3 букв) и слова с цифрами не проверяются. */
    fun isKnown(word: String, userWords: Set<String> = emptySet()): Boolean {
        val w = word.lowercase()
        if (w.length < MIN_LENGTH || w.any { !it.isLetter() && it != TUTUQ }) return true
        return w in rank || w in userWords || stemKnown(w, userWords, depth = 0)
    }

    private fun stemKnown(w: String, userWords: Set<String>, depth: Int): Boolean {
        if (depth > MAX_SUFFIXES) return false
        for (s in SUFFIXES) {
            if (w.length - s.length < MIN_STEM || !w.endsWith(s)) continue
            val stem = w.substring(0, w.length - s.length)
            if (stem in rank || stem in userWords) return true
            if (VERB_FORMS.any { stem + it in rank }) return true
            // Чередование на стыке: bilak → bilagi, qoşiq → qoşiğim.
            if (stem.endsWith('g') && stem.dropLast(1) + 'k' in rank) return true
            if (stem.endsWith('ğ') && stem.dropLast(1) + 'q' in rank) return true
            if (stemKnown(stem, userWords, depth + 1)) return true
        }
        return false
    }

    /** До [limit] вариантов исправления, самые частые первыми; регистр как в набранном слове. */
    fun corrections(word: String, limit: Int = 3): List<String> {
        val w = word.lowercase()
        val result = LinkedHashSet<String>()
        // Привычка старой латиницы: ishlab → işlab, shahar → şahar, o'zbek → özbek.
        oldLatinFix(w)?.let { if (it in rank) result += it }
        result += edits1(w).filter { it in rank }.sortedBy { rank.getValue(it) }
        if (result.isEmpty() && w.length <= MAX_LENGTH_FOR_TWO_EDITS) {
            val two = HashSet<String>()
            for (e in edits1(w)) for (x in edits1(e)) if (x in rank) two += x
            result += two.sortedBy { rank.getValue(it) }
        }
        return result.take(limit).map { matchCase(word, it) }
    }

    private fun oldLatinFix(w: String): String? {
        val fixed = w.replace("sh", "ş").replace("ch", "ç")
            .replace(Regex("o['‘’ʻ`]"), "ö").replace(Regex("g['‘’ʻ`]"), "ğ")
        return fixed.takeIf { it != w }
    }

    private fun edits1(w: String): Set<String> {
        val out = HashSet<String>(w.length * ALPHABET.length * 2 + w.length * 2)
        for (i in 0..w.length) {
            val a = w.substring(0, i)
            val b = w.substring(i)
            if (b.isNotEmpty()) out += a + b.substring(1)
            if (b.length > 1) out += a + b[1] + b[0] + b.substring(2)
            for (c in ALPHABET) {
                if (b.isNotEmpty()) out += a + c + b.substring(1)
                out += a + c + b
            }
        }
        out -= w
        return out
    }

    private fun matchCase(typed: String, word: String): String = when {
        typed.length >= 2 && typed.none { it.isLowerCase() } -> word.uppercase()
        typed[0].isUpperCase() -> word.replaceFirstChar { it.uppercaseChar() }
        else -> word
    }

    companion object {
        private val TUTUQ = Layouts.TUTUQ[0]
        private const val MIN_LENGTH = 3
        private const val MIN_STEM = 3
        private const val MAX_SUFFIXES = 4
        private const val MAX_LENGTH_FOR_TWO_EDITS = 12
        private const val ALPHABET = "abdefghijklmnopqrstuvxyzğöşçʼ"
        private val VERB_FORMS = listOf("moq", "ib", "di", "iş")

        /** Частые суффиксы в новом алфавите, длинные проверяются первыми. */
        private val SUFFIXES = listOf(
            "lar", "lari", "larni", "larga", "larda", "lardan", "larning", "lardagi", "laridagi",
            "ning", "ni", "ga", "ka", "qa", "ğa", "da", "dan", "dagi", "gi", "ki", "qi",
            "im", "ing", "i", "si", "imiz", "ingiz", "miz", "m", "ng", "sini", "siga", "sida", "sidan",
            "iga", "ini", "ida", "idan", "idagi", "indan",
            "mi", "çi", "çilar", "çilik", "lik", "ligi", "li", "siz", "dek", "day", "gina", "ça", "roq", "dir",
            "man", "san", "di", "dim", "ding", "dik", "dingiz", "dilar", "adi", "aman", "asan", "amiz", "asiz",
            "ayapti", "yapti", "yotgan", "yotir", "gan", "gani", "ganda", "gandan", "ganlar",
            "moqda", "moq", "iş", "işi", "işga", "işni", "işda", "işdi", "işyapti", "işadi",
            "ib", "sa", "sak", "sangiz", "may", "mas", "masdan", "madi", "maydi", "ar", "ayotgan",
            "ilgan", "lgan", "ildi", "ladi", "inçi", "ta", "tadan", "guncha", "gunça", "sin", "sinlar",
        ).distinct().sortedByDescending { it.length }
    }
}
