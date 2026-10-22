// Частотный словарь узбекского языка из дампа Википедии (stdin: распакованный XML).
// Слова переводятся на новый алфавит тем же конвертером, что в приложении.
// Запуск: bzcat uzwiki-…-pages-articles.xml.bz2 | node tools/dictionary/build_uz.js > words_uz.txt
const readline = require("readline");
const path = require("path");
const { toNewLatin } = require(path.join(__dirname, "..", "..", "prototype", "converter.js"));

const LIMIT = Number(process.env.LIMIT || 60000);
const MIN_COUNT = 3;
const SOURCE = process.env.SOURCE || "uzwiki pages-articles";

const counts = new Map();
let ns = null, inText = false, buf = [], pages = 0;

const ENTITIES = { "&amp;": "&", "&lt;": "<", "&gt;": ">", "&quot;": '"', "&#39;": "'", "&nbsp;": " " };

function clean(t) {
  // В дампе бывает двойное кодирование (&amp;nbsp;), поэтому раскодируем дважды.
  for (let i = 0; i < 2; i++) t = t.replace(/&(amp|lt|gt|quot|#39|nbsp);/g, m => ENTITIES[m]);
  t = t.replace(/^=+.*?=+\s*$/gm, " ");  // заголовки разделов: «Manbalar», «Havolalar»
  t = t.replace(/<!--[\s\S]*?-->/g, " ");
  t = t.replace(/<ref[^>]*\/>/gi, " ").replace(/<ref[\s\S]*?<\/ref>/gi, " ");
  t = t.replace(/<[^>]+>/g, " ");
  // Шаблоны {{…}} могут быть вложенными: убираем изнутри наружу.
  for (let i = 0; i < 10 && t.includes("{{"); i++) t = t.replace(/\{\{[^{}]*\}\}/g, " ");
  t = t.replace(/\{\|[\s\S]*?\|\}/g, " ");
  t = t.replace(/\[\[(?:Fayl|File|Tasvir|Rasm|Image|Turkum|Category|Kategoriya)[^\]]*\]\]/gi, " ");
  t = t.replace(/\[\[(?:[^|\]]*\|)?([^\]]*)\]\]/g, "$1");
  t = t.replace(/\[https?:\/\/\S+\s*([^\]]*)\]/g, "$1");
  t = t.replace(/https?:\/\/\S+/g, " ");
  t = t.replace(/'{2,}/g, " ");
  return t;
}

const TOKEN = /[\p{L}\p{M}'‘’ʻʼ`]+/gu;
function countTokens(t) {
  for (let w of t.match(TOKEN) || []) {
    w = w.replace(/^['‘’ʻʼ`]+/, "");
    // Апостроф в конце оставляем только после o/g: это часть буквы (oʻ, gʻ).
    w = w.replace(/(?<![OoGg])['‘’ʻʼ`]+$/, "");
    if (w.length < 2) continue;
    counts.set(w, (counts.get(w) || 0) + 1);
  }
}

const rl = readline.createInterface({ input: process.stdin, crlfDelay: Infinity });
rl.on("line", line => {
  if (!inText) {
    const m = line.match(/<ns>(\d+)<\/ns>/);
    if (m) ns = m[1];
    const start = line.indexOf("<text");
    if (start < 0) return;
    if (ns !== "0") return;
    inText = true;
    buf = [line.slice(line.indexOf(">", start) + 1)];
  } else {
    buf.push(line);
  }
  if (inText && buf[buf.length - 1].includes("</text>")) {
    inText = false;
    const text = buf.join("\n").replace(/<\/text>[\s\S]*$/, "");
    pages++;
    if (!/^#(REDIRECT|YO[ʻ'‘]NALTIRISH)/i.test(text)) countTokens(clean(text));
  }
});

rl.on("close", () => {
  // Сводим написания к новому алфавиту. Регистр: строчное, если оно встречается хотя бы в 10% случаев
  // (заглавные в начале предложения не в счёт), иначе самое частое (Toşkent, BMT).
  const ALPHABET = /^[a-zçğöşʼ]+$/iu;
  const words = new Map();
  for (const [raw, n] of counts) {
    const w = toNewLatin(raw);
    // Слово без гласной — сокращение (km, mln): в подсказках не нужно.
    if (!ALPHABET.test(w) || /[cw]/i.test(w) || !/[aeiouö]/i.test(w) || w.startsWith("ʼ") || w.endsWith("ʼ")) continue;
    const key = w.toLowerCase();
    let e = words.get(key);
    if (!e) words.set(key, e = { total: 0, forms: new Map() });
    e.total += n;
    e.forms.set(w, (e.forms.get(w) || 0) + n);
  }
  const list = [...words.values()]
    .filter(e => e.total >= MIN_COUNT)
    .sort((a, b) => b.total - a.total)
    .slice(0, LIMIT)
    .map(e => {
      const lower = [...e.forms.keys()].find(f => f === f.toLowerCase());
      if (lower && e.forms.get(lower) >= e.total * 0.1) return lower;
      return [...e.forms].sort((a, b) => b[1] - a[1])[0][0];
    });
  const out = [
    `# Uzbek word frequency list in the new alphabet, built from ${SOURCE} (${pages} pages).`,
    "# Source text: Uzbek Wikipedia contributors, CC BY-SA 4.0, https://uz.wikipedia.org",
    "# This list is shared under CC BY-SA 4.0. Built by tools/dictionary/build_uz.js.",
    ...list,
  ];
  process.stdout.write(out.join("\n") + "\n");
  console.error(`pages=${pages} raw=${counts.size} words=${list.length}`);
});
