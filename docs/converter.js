// Конвертер для прототипа: та же логика, что в converter/src/main/kotlin/.../AlifboConverter.kt.
// Проверяется теми же эталонами spec/cases.txt: node prototype/converter.test.js
(function (root) {
  const TUTUQ = "ʼ";
  const AP = "'‘’ʻʼ`´";
  const SEPARATE = "\u0000";

  const chunkRe = /\s+|\S+/gu;
  const wordRe = new RegExp(`[\\p{L}\\p{M}${AP}]+`, "gu");
  const emailRe = /^[^@\s]+@[^@\s]+\.[^@\s]+$/u;
  const oldOG = new RegExp(`([OoGg])([ʻ‘]|[${AP}](?=[\\p{L}${AP}]))`, "gu");
  const separateSH = new RegExp(`([SsCc])[${AP}](?=[Hh])`, "gu");
  const digraph = /[SsCc][Hh]/gu;
  const innerAp = new RegExp(`(?<=\\p{L})[${AP}](?=\\p{L})`, "gu");
  const newShCh = /[ŞşÇç]/u;
  const cyrLetter = /[а-яёўқғҳА-ЯЁЎҚҒҲ]/u;
  const CYR_VOWELS = "аеёиоуўэюяы";
  const CYR = { "а":"a","б":"b","в":"v","г":"g","ғ":"ğ","д":"d","ё":"yo","ж":"j","з":"z","и":"i","й":"y","к":"k","қ":"q","л":"l","м":"m","н":"n","о":"o","ў":"ö","п":"p","р":"r","с":"s","т":"t","у":"u","ф":"f","х":"x","ҳ":"h","ч":"ç","ш":"ş","щ":"ş","ъ":TUTUQ,"ь":"","ы":"i","э":"e","ю":"yu","я":"ya" };
  const OG = { O: "Ö", o: "ö", G: "Ğ", g: "ğ" };
  const SC = { S: "Ş", s: "ş", C: "Ç", c: "ç" };

  const isAddress = s => s.includes("://") || /^www\./i.test(s) || emailRe.test(s);
  const isUpper = c => c !== c.toLowerCase();
  const isLower = c => c !== c.toUpperCase();

  function oldLatinWord(word, keepShCh) {
    let w = word.replace(oldOG, (m, l) => OG[l]);
    if (!keepShCh) {
      w = w.replace(separateSH, (m, l) => l + SEPARATE);
      w = w.replace(digraph, m => SC[m[0]]);
      w = w.split(SEPARATE).join("");
    }
    return w.replace(innerAp, TUTUQ);
  }

  function cyrillicWord(word) {
    const chars = Array.from(word);
    const cased = chars.filter(c => isUpper(c) || isLower(c));
    const allCaps = cased.length >= 2 && !cased.some(isLower);
    return chars.map((ch, i) => {
      const lo = ch.toLowerCase();
      const prev = i > 0 ? chars[i - 1].toLowerCase() : null;
      const atStart = prev === null || !cyrLetter.test(prev);
      const afterVowel = prev !== null && CYR_VOWELS.includes(prev);
      let latin;
      if (lo === "е") latin = (atStart || afterVowel || prev === "ъ" || prev === "ь") ? "ye" : "e";
      else if (lo === "ц") latin = afterVowel ? "ts" : "s";
      else latin = CYR[lo] ?? ch;
      // toUpperCase() без локали: i не станет İ.
      if (!isUpper(ch) || latin === "") return latin;
      return allCaps ? latin.toUpperCase() : latin[0].toUpperCase() + latin.slice(1);
    }).join("");
  }

  // assumeOldLatin: считать текст старым даже при наличии Ş/Ç (кнопка «eski→yangi» на клавиатуре).
  function toNewLatin(text, { assumeOldLatin = false } = {}) {
    const keepShCh = !assumeOldLatin && newShCh.test(text);
    return text.replace(chunkRe, s => {
      if (/^\s+$/u.test(s) || isAddress(s)) return s;
      return s.replace(wordRe, w => cyrLetter.test(w) ? cyrillicWord(w) : oldLatinWord(w, keepShCh));
    });
  }

  const api = { toNewLatin, TUTUQ };
  if (typeof module === "object" && module.exports) module.exports = api;
  else root.AlifboConverter = api;
})(this);
