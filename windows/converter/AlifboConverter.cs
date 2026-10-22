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
                else latin = CyrMap(lo) ?? ch.ToString();
                // ToUpperInvariant не зависит от языка системы: i не станет İ.
                if (!char.IsUpper(ch) || latin.Length == 0) output.Append(latin);
                else if (allCaps) output.Append(latin.ToUpperInvariant());
                else output.Append(char.ToUpperInvariant(latin[0])).Append(latin.Substring(1));
            }
            return output.ToString();
        }
    }
}
