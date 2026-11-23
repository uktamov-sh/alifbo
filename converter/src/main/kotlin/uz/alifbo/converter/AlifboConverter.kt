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

    /** o'/g' в конце слова (tog', bog') — тоже буква, если слово не начинается с кавычки ('radio'). */
    private val trailingOG = Regex("""([OoGg])[$APOSTROPHES]$""")

    private inline fun ogLetters(word: String, crossinline toLetter: (String) -> String): String {
        val w = oldOG.replace(word) { m -> toLetter(m.groupValues[1]) }
        return if (word.isNotEmpty() && word[0] in APOSTROPHES) w
        else trailingOG.replace(w) { m -> toLetter(m.groupValues[1]) }
    }

    private fun oldLatinWord(word: String, keepShCh: Boolean): String {
        var w = ogLetters(word) { l ->
            when (l) {
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
                // ъ в русских заимствованиях (после согласной перед е, ё, ю, я) не пишется: объект → obyekt.
                'ъ' -> if (!atStart && !afterVowel && word.getOrNull(i + 1)?.lowercaseChar()?.let { it in "еёюя" } == true) ""
                       else TUTUQ.toString()
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

    // --- Обратные направления: нынешняя латиница (1995) и кириллица ---
    // Слово сначала приводится к новой латинице, потом переписывается в нужную систему.
    // Правила и эталоны: spec/converter-rules.md, spec/cases-old-latin.txt, spec/cases-cyrillic.txt.

    /** iPhone, WhatsApp: имена со смешанным регистром не трогаем. */
    private val camelCase = Regex("""\p{Ll}.*\p{Lu}""")

    private fun convertWords(text: String, convertWord: (String) -> String): String {
        val keepShCh = newShCh.containsMatchIn(text)
        return chunkRegex.replace(text) { chunk ->
            val s = chunk.value
            if (s.isBlank() || isAddress(s)) s
            else wordRegex.replace(s) { m ->
                val w = m.value
                if (camelCase.containsMatchIn(w)) w
                else convertWord(if (cyrillicLetter.containsMatchIn(w)) cyrillicWord(w) else oldLatinWord(w, keepShCh))
            }
        }
    }

    private class WordCase(val allCaps: Boolean, val capFirst: Boolean)

    private fun caseOf(word: String): WordCase {
        val cased = word.filter { it.isLetter() && it.lowercaseChar() != it.uppercaseChar() }
        return WordCase(
            allCaps = cased.length >= 2 && cased.none { it.isLowerCase() },
            capFirst = cased.isNotEmpty() && cased[0].isUpperCase(),
        )
    }

    private fun applyCase(out: String, c: WordCase): String = when {
        c.allCaps -> out.uppercase()
        c.capFirst && out.isNotEmpty() -> out.replaceFirstChar { it.uppercaseChar() }
        else -> out
    }

    private val oldLetters = mapOf('ö' to "oʻ", 'ğ' to "gʻ", 'ş' to "sh", 'ç' to "ch")

    /** Новая латиница → нынешняя: Ö → Oʻ, Ş → Sh; s+h, которые читаются раздельно, получают тутук. */
    private fun oldLatinOf(word: String): String {
        val lower = word.lowercase()
        val out = StringBuilder()
        lower.forEachIndexed { i, ch ->
            val old = oldLetters[ch]
            when {
                old != null -> out.append(old)
                (ch == 's' || ch == 'c') && lower.getOrNull(i + 1) == 'h' -> out.append(ch).append(TUTUQ)
                else -> out.append(ch)
            }
        }
        return applyCase(out.toString(), caseOf(word))
    }

    /** Переписывает текст на нынешнюю латиницу (1995): Oʻ, Gʻ, Sh, Ch. */
    fun toOldLatin(text: String): String = convertWords(text, ::oldLatinOf)

    // <cyrillic-words> — создано tools/gen-cyrillic-words.js из spec/cyrillic-words.txt, не править руками
    private const val CYR_WORDS = "yanvar=январь|fevral=февраль|aprel=апрель|iyun=июнь|iyul=июль|sentyabr=сентябрь|oktyabr=октябрь|noyabr=ноябрь|dekabr=декабрь|sirk=цирк|sentr=центр|sentner=центнер|sement=цемент|sitata=цитата|siferblat=циферблат|silindr=цилиндр|sikl=цикл|sivilizatsiya=цивилизация|sex=цех|konsert=концерт|prinsip=принцип|abzas=абзац|kansler=канцлер|fransuz=француз|litsey=лицей|litsenziya=лицензия|kompyuter=компьютер|pyesa=пьеса|premyer=премьер|portfel=портфель|mebel=мебель|model=модель|medal=медаль|festival=фестиваль|dizel=дизель|rubl=рубль|spektakl=спектакль|albom=альбом|olga=ольга|natalya=наталья|ilya=илья|pavilyon=павильон|batalyon=батальон|pedal=педаль|tunnel=туннель|gospital=госпиталь|avtomobil=автомобиль|kalendar=календарь|lager=лагерь|ansambl=ансамбль|obyekt=объект|subyekt=субъект|obyektiv=объектив|obyom=объём|syezd=съезд|podyezd=подъезд|adyutant=адъютант|inyeksiya=инъекция|pensiya=пенсия|ekspansiya=экспансия"
    // </cyrillic-words>
    private val cyrWords: Map<String, String> = CYR_WORDS.split('|').filter { it.isNotEmpty() }
        .associate { it.substringBefore('=') to it.substringAfter('=') }
    private const val LAT_VOWELS = "aeiouö"
    private const val LAT_CONSONANTS = "bdfgğhjklmnpqrsştvxyzç"
    private val toCyr = mapOf(
        'a' to "а", 'b' to "б", 'd' to "д", 'f' to "ф", 'g' to "г", 'ğ' to "ғ", 'h' to "ҳ", 'i' to "и",
        'j' to "ж", 'k' to "к", 'l' to "л", 'm' to "м", 'n' to "н", 'o' to "о", 'ö' to "ў", 'p' to "п",
        'q' to "қ", 'r' to "р", 's' to "с", 'ş' to "ш", 't' to "т", 'u' to "у", 'v' to "в", 'x' to "х",
        'y' to "й", 'z' to "з", 'ç' to "ч", 'c' to "к", 'w' to "в",
    )
    private val iotated = mapOf('e' to "е", 'o' to "ё", 'u' to "ю", 'a' to "я")

    /** Основа из словаря подходит, если за ней конец слова, согласная или «i» (sirkda, но не sirka). */
    private fun dictionaryStem(lower: String): Pair<Int, String> {
        for (n in lower.length downTo 2) {
            val cyr = cyrWords[lower.substring(0, n)] ?: continue
            val next = lower.getOrNull(n) ?: return n to cyr
            if (next in LAT_CONSONANTS || next == 'i') return n to cyr.removeSuffix("ь")
        }
        return 0 to ""
    }

    /** Новая латиница → кириллица. */
    private fun cyrillicOf(word: String): String {
        val lower = word.lowercase()
        val (start, stem) = dictionaryStem(lower)
        val out = StringBuilder(stem)
        var i = start
        while (i < lower.length) {
            val ch = lower[i]
            val next = lower.getOrNull(i + 1)
            val prev = lower.getOrNull(i - 1)
            val afterVowel = prev != null && prev in LAT_VOWELS
            val afterConsonant = prev != null && prev in LAT_CONSONANTS
            when {
                ch == 'y' && next != null && next in iotated -> {
                    // pyesa → пьеса; dunyo, kelyapti → дунё, келяпти
                    if (next == 'e' && afterConsonant) out.append('ь')
                    out.append(iotated.getValue(next))
                    i++
                }
                ch == 'e' -> out.append(if (afterVowel || !afterConsonant) "э" else "е")
                ch == 't' && next == 's' && lower.getOrNull(i + 2) == 'i' &&
                    (lower.getOrNull(i + 3)?.let { it in LAT_VOWELS || it == 'y' } == true) -> {
                    out.append('ц')   // militsiya, sotsial; но otsiz, aytsin — тс
                    i++
                }
                ch == 's' && (prev == 'k' || prev == 'n') && lower.startsWith("siya", i) -> {
                    out.append("ция")   // aksiya, stansiya
                    i += 3
                }
                ch == TUTUQ -> out.append('ъ')
                else -> out.append(toCyr[ch] ?: word.getOrNull(i)?.toString() ?: ch.toString())
            }
            i++
        }
        return applyCase(out.toString(), caseOf(word))
    }

    /** Переписывает латинский текст (новый или нынешний алфавит) на кириллицу. Кириллица остаётся как есть. */
    fun toCyrillic(text: String): String = convertWords(text, ::cyrillicOf)

    // --- Исправление апострофов: алфавит не меняется ---
    // После o и g — ʻ (U+02BB), между буквами — тутук ʼ (U+02BC). Эталоны: spec/cases-apostrophes.txt.
    private const val OKINA = "ʻ"
    private const val MARK = "\u0001"
    private val innerApAfterMark = Regex("""(?<=[\p{L}$MARK])[$APOSTROPHES](?=\p{L})""")

    /** Ставит правильные знаки: oʻ, gʻ и тутук ʼ. Буквы и алфавит не меняются, кириллица и кавычки не трогаются. */
    fun fixApostrophes(text: String): String = chunkRegex.replace(text) { chunk ->
        val s = chunk.value
        if (s.isBlank() || isAddress(s)) s
        else wordRegex.replace(s) { m ->
            val w = m.value
            if (cyrillicLetter.containsMatchIn(w)) w
            else innerApAfterMark.replace(ogLetters(w) { it + MARK }, TUTUQ.toString()).replace(MARK, OKINA)
        }
    }
}
