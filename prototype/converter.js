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

  // o'/g' в конце слова (tog', bog') — тоже буква, если слово не начинается с кавычки ('radio').
  const trailingOG = new RegExp(`([OoGg])[${AP}]$`, "u");
  function ogLetters(word, toLetter) {
    const w = word.replace(oldOG, (m, l) => toLetter(l));
    return AP.includes(word[0]) ? w : w.replace(trailingOG, (m, l) => toLetter(l));
  }

  function oldLatinWord(word, keepShCh) {
    let w = ogLetters(word, l => OG[l]);
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
      // ъ в русских заимствованиях (после согласной перед е, ё, ю, я) не пишется: объект → obyekt.
      else if (lo === "ъ" && prev !== null && !atStart && !afterVowel && "еёюя".includes((chars[i + 1] ?? "").toLowerCase())) latin = "";
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

  // --- Обратные направления: нынешняя латиница (1995) и кириллица ---
  // Слово сначала приводится к новой латинице, потом переписывается в нужную систему.
  // Правила и эталоны: spec/converter-rules.md, spec/cases-old-latin.txt, spec/cases-cyrillic.txt.

  const camelCase = /\p{Ll}.*\p{Lu}/u;   // iPhone, WhatsApp: имена, не трогаем

  function convertWords(text, convertWord) {
    const keepShCh = newShCh.test(text);
    return text.replace(chunkRe, s => {
      if (/^\s+$/u.test(s) || isAddress(s)) return s;
      return s.replace(wordRe, w => {
        if (camelCase.test(w)) return w;
        return convertWord(cyrLetter.test(w) ? cyrillicWord(w) : oldLatinWord(w, keepShCh), w);
      });
    });
  }

  function caseOf(word) {
    const cased = Array.from(word).filter(c => isUpper(c) || isLower(c));
    return {
      allCaps: cased.length >= 2 && !cased.some(isLower),
      capFirst: cased.length > 0 && isUpper(cased[0]),
    };
  }

  function applyCase(out, { allCaps, capFirst }) {
    if (allCaps) return out.toUpperCase();
    return capFirst ? out[0].toUpperCase() + out.slice(1) : out;
  }

  const OLD = { "ö": "oʻ", "ğ": "gʻ", "ş": "sh", "ç": "ch" };

  // Новая латиница → нынешняя: Ö → Oʻ, Ş → Sh; s+h, которые читаются раздельно, получают тутук.
  function oldLatinOf(word) {
    const c = caseOf(word);
    const lower = word.toLowerCase();
    let out = "";
    for (let i = 0; i < lower.length; i++) {
      const ch = lower[i];
      if (OLD[ch]) out += OLD[ch];
      else if ((ch === "s" || ch === "c") && lower[i + 1] === "h") out += ch + TUTUQ;
      else out += ch;
    }
    return c.allCaps ? out.toUpperCase() : applyCase(out, c);
  }

  function toOldLatin(text) {
    return convertWords(text, oldLatinOf);
  }

  // <cyrillic-words> — создано tools/gen-cyrillic-words.js из spec/cyrillic-words.txt, не править руками
  const CYR_WORDS = "yanvar=январь|fevral=февраль|aprel=апрель|iyun=июнь|iyul=июль|sentyabr=сентябрь|oktyabr=октябрь|noyabr=ноябрь|dekabr=декабрь|sirk=цирк|sentr=центр|sentner=центнер|sement=цемент|sitata=цитата|siferblat=циферблат|silindr=цилиндр|sikl=цикл|sivilizatsiya=цивилизация|sex=цех|konsert=концерт|prinsip=принцип|abzas=абзац|kansler=канцлер|fransuz=француз|litsey=лицей|litsenziya=лицензия|kompyuter=компьютер|pyesa=пьеса|premyer=премьер|portfel=портфель|mebel=мебель|model=модель|medal=медаль|festival=фестиваль|dizel=дизель|rubl=рубль|spektakl=спектакль|albom=альбом|olga=ольга|natalya=наталья|ilya=илья|pavilyon=павильон|batalyon=батальон|pedal=педаль|tunnel=туннель|gospital=госпиталь|avtomobil=автомобиль|kalendar=календарь|lager=лагерь|ansambl=ансамбль|obyekt=объект|subyekt=субъект|obyektiv=объектив|obyom=объём|syezd=съезд|podyezd=подъезд|adyutant=адъютант|inyeksiya=инъекция|pensiya=пенсия|ekspansiya=экспансия";
  // </cyrillic-words>
  const cyrWords = new Map(CYR_WORDS.split("|").filter(Boolean).map(p => p.split("=")));
  const LAT_VOWELS = "aeiouö";
  const LAT_CONSONANTS = "bdfgğhjklmnpqrsştvxyzç";
  const TO_CYR = { a:"а",b:"б",d:"д",f:"ф",g:"г","ğ":"ғ",h:"ҳ",i:"и",j:"ж",k:"к",l:"л",m:"м",n:"н",o:"о","ö":"ў",p:"п",q:"қ",r:"р",s:"с","ş":"ш",t:"т",u:"у",v:"в",x:"х",y:"й",z:"з","ç":"ч",c:"к",w:"в" };
  const IOTATED = { e: "е", o: "ё", u: "ю", a: "я" };

  // Основа из словаря подходит, если за ней конец слова, согласная или «i» (sirkda, но не sirka).
  function dictionaryStem(lower) {
    for (let n = lower.length; n >= 2; n--) {
      const cyr = cyrWords.get(lower.slice(0, n));
      if (cyr === undefined) continue;
      const next = lower[n];
      if (next === undefined) return [n, cyr];
      if (LAT_CONSONANTS.includes(next) || next === "i") return [n, cyr.endsWith("ь") ? cyr.slice(0, -1) : cyr];
    }
    return [0, ""];
  }

  // Новая латиница → кириллица.
  function cyrillicOf(word) {
    const c = caseOf(word);
    const lower = word.toLowerCase();
    let [i, out] = dictionaryStem(lower);
    for (; i < lower.length; i++) {
      const ch = lower[i], next = lower[i + 1];
      const prev = i > 0 ? lower[i - 1] : "";
      const afterVowel = prev !== "" && LAT_VOWELS.includes(prev);
      const afterConsonant = prev !== "" && LAT_CONSONANTS.includes(prev);
      if (ch === "y" && next in IOTATED) {
        // pyesa → пьеса; dunyo, kelyapti → дунё, келяпти
        out += (next === "e" && afterConsonant ? "ь" : "") + IOTATED[next];
        i++;
      } else if (ch === "e") {
        out += afterVowel || !afterConsonant ? "э" : "е";
      } else if (ch === "t" && next === "s" && lower[i + 2] === "i" && (LAT_VOWELS + "y").includes(lower[i + 3] || "-")) {
        out += "ц";   // militsiya, sotsial; но otsiz, aytsin — тс
        i++;
      } else if (ch === "s" && (prev === "k" || prev === "n") && lower.startsWith("siya", i)) {
        out += "ция";   // aksiya, stansiya
        i += 3;
      } else if (ch === TUTUQ) {
        out += "ъ";
      } else {
        out += TO_CYR[ch] ?? word[i];
      }
    }
    return applyCase(out, c);
  }

  function toCyrillic(text) {
    return convertWords(text, cyrillicOf);
  }

  // --- Исправление апострофов: алфавит не меняется ---
  // После o и g — ʻ (U+02BB), между буквами — тутук ʼ (U+02BC). Эталоны: spec/cases-apostrophes.txt.
  const OKINA = "ʻ";
  const MARK = "\u0001";
  const innerApAfterMark = new RegExp(`(?<=[\\p{L}${MARK}])[${AP}](?=\\p{L})`, "gu");

  function fixApostrophes(text) {
    return text.replace(chunkRe, s => {
      if (/^\s+$/u.test(s) || isAddress(s)) return s;
      return s.replace(wordRe, w => {
        if (cyrLetter.test(w)) return w;
        return ogLetters(w, l => l + MARK).replace(innerApAfterMark, TUTUQ).split(MARK).join(OKINA);
      });
    });
  }

  const api = { toNewLatin, toOldLatin, toCyrillic, fixApostrophes, TUTUQ };
  if (typeof module === "object" && module.exports) module.exports = api;
  else root.AlifboConverter = api;
})(this);
