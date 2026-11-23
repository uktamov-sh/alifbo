// Конвертер на новый узбекский алфавит: та же логика, что в converter/src/main/kotlin/.../AlifboConverter.kt.
// Проверяется теми же эталонами spec/cases.txt (windows/converter/check).
// Работает только с переданной строкой: ничего не хранит, не пишет в журнал и не обращается к сети.
using System.Text;
using System.Text.RegularExpressions;

namespace Alifbo
{
    public static class AlifboConverter
    {
        /// <summary>Тутук белгиси.</summary>
        public const char Tutuq = 'ʼ';

        private const string Ap = "'‘’ʻʼ`´";
        private const char Separate = '\u0000';

        private static readonly Regex Chunk = new Regex(@"\s+|\S+");
        private static readonly Regex Word = new Regex("[\\p{L}\\p{M}" + Ap + "]+");
        private static readonly Regex Email = new Regex(@"^[^@\s]+@[^@\s]+\.[^@\s]+$");
        private static readonly Regex OldOG = new Regex("([OoGg])([ʻ‘]|[" + Ap + "](?=[\\p{L}" + Ap + "]))");
        private static readonly Regex SeparateSH = new Regex("([SsCc])[" + Ap + "](?=[Hh])");
        private static readonly Regex Digraph = new Regex("[SsCc][Hh]");
        private static readonly Regex InnerApostrophe = new Regex("(?<=\\p{L})[" + Ap + "](?=\\p{L})");
        private static readonly Regex NewShCh = new Regex("[ŞşÇç]");
        private static readonly Regex CyrillicLetter = new Regex("[а-яёўқғҳА-ЯЁЎҚҒҲ]");

        private const string CyrVowels = "аеёиоуўэюяы";

        private static string CyrMap(char c)
        {
            switch (c)
            {
                case 'а': return "a"; case 'б': return "b"; case 'в': return "v"; case 'г': return "g";
                case 'ғ': return "ğ"; case 'д': return "d"; case 'ё': return "yo"; case 'ж': return "j";
                case 'з': return "z"; case 'и': return "i"; case 'й': return "y"; case 'к': return "k";
                case 'қ': return "q"; case 'л': return "l"; case 'м': return "m"; case 'н': return "n";
                case 'о': return "o"; case 'ў': return "ö"; case 'п': return "p"; case 'р': return "r";
                case 'с': return "s"; case 'т': return "t"; case 'у': return "u"; case 'ф': return "f";
                case 'х': return "x"; case 'ҳ': return "h"; case 'ч': return "ç"; case 'ш': return "ş";
                case 'щ': return "ş"; case 'ъ': return Tutuq.ToString(); case 'ь': return "";
                case 'ы': return "i"; case 'э': return "e"; case 'ю': return "yu"; case 'я': return "ya";
                default: return null;
            }
        }

        /// <param name="assumeOldLatin">Считать латинский текст старым, даже если в нём уже есть Ş или Ç
        /// (так работает горячая клавиша: её нажимают именно для старого текста).</param>
        public static string ToNewLatin(string text, bool assumeOldLatin = false)
        {
            // Если в тексте уже есть Ş или Ç, он написан новым алфавитом и sh/ch в нём — две буквы.
            bool keepShCh = !assumeOldLatin && NewShCh.IsMatch(text);
            return Chunk.Replace(text, chunk =>
            {
                string s = chunk.Value;
                if (string.IsNullOrWhiteSpace(s) || IsAddress(s)) return s;
                return Word.Replace(s, w => CyrillicLetter.IsMatch(w.Value)
                    ? CyrillicWord(w.Value)
                    : OldLatinWord(w.Value, keepShCh));
            });
        }

        private static bool IsAddress(string s) =>
            s.IndexOf("://", System.StringComparison.Ordinal) >= 0 ||
            s.StartsWith("www.", System.StringComparison.OrdinalIgnoreCase) ||
            Email.IsMatch(s);

        private static string OldLatinWord(string word, bool keepShCh)
        {
            string w = OldOG.Replace(word, m =>
            {
                switch (m.Groups[1].Value)
                {
                    case "O": return "Ö";
                    case "o": return "ö";
                    case "G": return "Ğ";
                    default: return "ğ";
                }
            });
            if (!keepShCh)
            {
                w = SeparateSH.Replace(w, m => m.Groups[1].Value + Separate);
                w = Digraph.Replace(w, m =>
                {
                    switch (m.Value[0])
                    {
                        case 'S': return "Ş";
                        case 's': return "ş";
                        case 'C': return "Ç";
                        default: return "ç";
                    }
                });
                w = w.Replace(Separate.ToString(), "");
            }
            return InnerApostrophe.Replace(w, Tutuq.ToString());
        }

        private static string CyrillicWord(string word)
        {
            int cased = 0, lower = 0;
            foreach (char c in word)
            {
                if (char.IsLetter(c) && char.ToLowerInvariant(c) != char.ToUpperInvariant(c))
                {
                    cased++;
                    if (char.IsLower(c)) lower++;
                }
            }
            bool allCaps = cased >= 2 && lower == 0;
            var output = new StringBuilder();
            for (int i = 0; i < word.Length; i++)
            {
                char ch = word[i];
                char lo = char.ToLowerInvariant(ch);
                char? prev = i > 0 ? char.ToLowerInvariant(word[i - 1]) : (char?)null;
                bool atStart = prev == null || !CyrillicLetter.IsMatch(prev.Value.ToString());
                bool afterVowel = prev != null && CyrVowels.IndexOf(prev.Value) >= 0;
                string latin;
                if (lo == 'е') latin = atStart || afterVowel || prev == 'ъ' || prev == 'ь' ? "ye" : "e";
                else if (lo == 'ц') latin = afterVowel ? "ts" : "s";
                // ъ в русских заимствованиях (после согласной перед е, ё, ю, я) не пишется: объект → obyekt.
                else if (lo == 'ъ' && !atStart && !afterVowel && i + 1 < word.Length && "еёюя".IndexOf(char.ToLowerInvariant(word[i + 1])) >= 0) latin = "";
                else latin = CyrMap(lo) ?? ch.ToString();
                // ToUpperInvariant не зависит от языка системы: i не станет İ.
                if (!char.IsUpper(ch) || latin.Length == 0) output.Append(latin);
                else if (allCaps) output.Append(latin.ToUpperInvariant());
                else output.Append(char.ToUpperInvariant(latin[0])).Append(latin.Substring(1));
            }
            return output.ToString();
        }

        // --- Обратные направления: нынешняя латиница (1995) и кириллица ---
        // Слово сначала приводится к новой латинице, потом переписывается в нужную систему.
        // Правила и эталоны: spec/converter-rules.md, spec/cases-old-latin.txt, spec/cases-cyrillic.txt.

        /// <summary>iPhone, WhatsApp: имена со смешанным регистром не трогаем.</summary>
        private static readonly Regex CamelCase = new Regex(@"\p{Ll}.*\p{Lu}");

        private static string ConvertWords(string text, System.Func<string, string> convertWord)
        {
            bool keepShCh = NewShCh.IsMatch(text);
            return Chunk.Replace(text, chunk =>
            {
                string s = chunk.Value;
                if (string.IsNullOrWhiteSpace(s) || IsAddress(s)) return s;
                return Word.Replace(s, m =>
                {
                    string w = m.Value;
                    if (CamelCase.IsMatch(w)) return w;
                    return convertWord(CyrillicLetter.IsMatch(w) ? CyrillicWord(w) : OldLatinWord(w, keepShCh));
                });
            });
        }

        private static (bool allCaps, bool capFirst) CaseOf(string word)
        {
            int cased = 0, lower = 0;
            bool capFirst = false;
            foreach (char c in word)
            {
                if (!char.IsLetter(c) || char.ToLowerInvariant(c) == char.ToUpperInvariant(c)) continue;
                if (cased == 0) capFirst = char.IsUpper(c);
                cased++;
                if (char.IsLower(c)) lower++;
            }
            return (cased >= 2 && lower == 0, capFirst);
        }

        private static string ApplyCase(string output, (bool allCaps, bool capFirst) c)
        {
            if (c.allCaps) return output.ToUpperInvariant();
            if (c.capFirst && output.Length > 0) return char.ToUpperInvariant(output[0]) + output.Substring(1);
            return output;
        }

        /// <summary>Новая латиница → нынешняя: Ö → Oʻ, Ş → Sh; s+h, которые читаются раздельно, получают тутук.</summary>
        private static string OldLatinOf(string word)
        {
            string lower = word.ToLowerInvariant();
            var output = new StringBuilder();
            for (int i = 0; i < lower.Length; i++)
            {
                char ch = lower[i];
                switch (ch)
                {
                    case 'ö': output.Append("oʻ"); break;
                    case 'ğ': output.Append("gʻ"); break;
                    case 'ş': output.Append("sh"); break;
                    case 'ç': output.Append("ch"); break;
                    default:
                        output.Append(ch);
                        if ((ch == 's' || ch == 'c') && i + 1 < lower.Length && lower[i + 1] == 'h') output.Append(Tutuq);
                        break;
                }
            }
            return ApplyCase(output.ToString(), CaseOf(word));
        }

        /// <summary>Переписывает текст на нынешнюю латиницу (1995): Oʻ, Gʻ, Sh, Ch.</summary>
        public static string ToOldLatin(string text) => ConvertWords(text, OldLatinOf);

        // <cyrillic-words> — создано tools/gen-cyrillic-words.js из spec/cyrillic-words.txt, не править руками
        private const string CyrWords = "yanvar=январь|fevral=февраль|aprel=апрель|iyun=июнь|iyul=июль|sentyabr=сентябрь|oktyabr=октябрь|noyabr=ноябрь|dekabr=декабрь|sirk=цирк|sentr=центр|sentner=центнер|sement=цемент|sitata=цитата|siferblat=циферблат|silindr=цилиндр|sikl=цикл|sivilizatsiya=цивилизация|sex=цех|konsert=концерт|prinsip=принцип|abzas=абзац|kansler=канцлер|fransuz=француз|litsey=лицей|litsenziya=лицензия|kompyuter=компьютер|pyesa=пьеса|premyer=премьер|portfel=портфель|mebel=мебель|model=модель|medal=медаль|festival=фестиваль|dizel=дизель|rubl=рубль|spektakl=спектакль|albom=альбом|olga=ольга|natalya=наталья|ilya=илья|pavilyon=павильон|batalyon=батальон|pedal=педаль|tunnel=туннель|gospital=госпиталь|avtomobil=автомобиль|kalendar=календарь|lager=лагерь|ansambl=ансамбль|obyekt=объект|subyekt=субъект|obyektiv=объектив|obyom=объём|syezd=съезд|podyezd=подъезд|adyutant=адъютант|inyeksiya=инъекция|pensiya=пенсия|ekspansiya=экспансия";
        // </cyrillic-words>
        private static readonly System.Collections.Generic.Dictionary<string, string> CyrWordMap = BuildCyrWords();
        private const string LatVowels = "aeiouö";
        private const string LatConsonants = "bdfgğhjklmnpqrsştvxyzç";

        private static System.Collections.Generic.Dictionary<string, string> BuildCyrWords()
        {
            var map = new System.Collections.Generic.Dictionary<string, string>();
            foreach (string pair in CyrWords.Split(new[] { '|' }, System.StringSplitOptions.RemoveEmptyEntries))
            {
                int eq = pair.IndexOf('=');
                map[pair.Substring(0, eq)] = pair.Substring(eq + 1);
            }
            return map;
        }

        private static string ToCyr(char c)
        {
            switch (c)
            {
                case 'a': return "а"; case 'b': return "б"; case 'd': return "д"; case 'f': return "ф";
                case 'g': return "г"; case 'ğ': return "ғ"; case 'h': return "ҳ"; case 'i': return "и";
                case 'j': return "ж"; case 'k': return "к"; case 'l': return "л"; case 'm': return "м";
                case 'n': return "н"; case 'o': return "о"; case 'ö': return "ў"; case 'p': return "п";
                case 'q': return "қ"; case 'r': return "р"; case 's': return "с"; case 'ş': return "ш";
                case 't': return "т"; case 'u': return "у"; case 'v': return "в"; case 'x': return "х";
                case 'y': return "й"; case 'z': return "з"; case 'ç': return "ч"; case 'c': return "к";
                case 'w': return "в";
                default: return null;
            }
        }

        private static string Iotated(char c)
        {
            switch (c)
            {
                case 'e': return "е"; case 'o': return "ё"; case 'u': return "ю"; case 'a': return "я";
                default: return null;
            }
        }

        /// <summary>Основа из словаря подходит, если за ней конец слова, согласная или «i» (sirkda, но не sirka).</summary>
        private static (int length, string cyr) DictionaryStem(string lower)
        {
            for (int n = lower.Length; n >= 2; n--)
            {
                if (!CyrWordMap.TryGetValue(lower.Substring(0, n), out string cyr)) continue;
                if (n == lower.Length) return (n, cyr);
                char next = lower[n];
                if (LatConsonants.IndexOf(next) >= 0 || next == 'i')
                    return (n, cyr.EndsWith("ь") ? cyr.Substring(0, cyr.Length - 1) : cyr);
            }
            return (0, "");
        }

        /// <summary>Новая латиница → кириллица.</summary>
        private static string CyrillicOf(string word)
        {
            string lower = word.ToLowerInvariant();
            var (start, stem) = DictionaryStem(lower);
            var output = new StringBuilder(stem);
            for (int i = start; i < lower.Length; i++)
            {
                char ch = lower[i];
                char next = i + 1 < lower.Length ? lower[i + 1] : '\0';
                char prev = i > 0 ? lower[i - 1] : '\0';
                bool afterVowel = prev != '\0' && LatVowels.IndexOf(prev) >= 0;
                bool afterConsonant = prev != '\0' && LatConsonants.IndexOf(prev) >= 0;
                char third = i + 3 < lower.Length ? lower[i + 3] : '\0';
                if (ch == 'y' && Iotated(next) != null)
                {
                    // pyesa → пьеса; dunyo, kelyapti → дунё, келяпти
                    if (next == 'e' && afterConsonant) output.Append('ь');
                    output.Append(Iotated(next));
                    i++;
                }
                else if (ch == 'e') output.Append(afterVowel || !afterConsonant ? "э" : "е");
                else if (ch == 't' && next == 's' && i + 2 < lower.Length && lower[i + 2] == 'i' &&
                         third != '\0' && (LatVowels.IndexOf(third) >= 0 || third == 'y'))
                {
                    output.Append('ц');   // militsiya, sotsial; но otsiz, aytsin — тс
                    i++;
                }
                else if (ch == 's' && (prev == 'k' || prev == 'n') && string.CompareOrdinal(lower, i, "siya", 0, 4) == 0)
                {
                    output.Append("ция");   // aksiya, stansiya
                    i += 3;
                }
                else if (ch == Tutuq) output.Append('ъ');
                else output.Append(ToCyr(ch) ?? (i < word.Length ? word[i].ToString() : ch.ToString()));
            }
            return ApplyCase(output.ToString(), CaseOf(word));
        }

        /// <summary>Переписывает латинский текст (новый или нынешний алфавит) на кириллицу. Кириллица остаётся как есть.</summary>
        public static string ToCyrillic(string text) => ConvertWords(text, CyrillicOf);
    }
}
