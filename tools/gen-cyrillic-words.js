// Переносит словарь spec/cyrillic-words.txt в код всех трёх конвертеров (JS, Kotlin, C#).
// node tools/gen-cyrillic-words.js          — записать
// node tools/gen-cyrillic-words.js --check  — только проверить, что код совпадает со словарём (для CI)
const fs = require("fs");
const path = require("path");

const root = path.join(__dirname, "..");
const pairs = fs.readFileSync(path.join(root, "spec", "cyrillic-words.txt"), "utf8")
  .split(/\r?\n/).map(l => l.trim()).filter(l => l && !l.startsWith("#"))
  .map((l, i) => {
    const parts = l.split(/\s+/);
    if (parts.length !== 2) throw new Error(`cyrillic-words.txt: ожидается «основа кириллица»: ${l}`);
    return parts.join("=");
  });
const literal = JSON.stringify(pairs.join("|"));

const targets = [
  ["prototype/converter.js", `  const CYR_WORDS = ${literal};`],
  ["docs/converter.js", `  const CYR_WORDS = ${literal};`],
  ["converter/src/main/kotlin/uz/alifbo/converter/AlifboConverter.kt", `    private const val CYR_WORDS = ${literal}`],
  ["windows/converter/AlifboConverter.cs", `        private const string CyrWords = ${literal};`],
];
const check = process.argv.includes("--check");
let stale = 0;
for (const [file, line] of targets) {
  const full = path.join(root, file);
  const src = fs.readFileSync(full, "utf8");
  const re = /(<cyrillic-words>[^\n]*\n)[^\n]*\n([^\n]*<\/cyrillic-words>)/;
  if (!re.test(src)) throw new Error(`${file}: нет меток <cyrillic-words>`);
  const out = src.replace(re, (m, a, b) => a + line + "\n" + b);
  if (out !== src) {
    stale++;
    if (check) console.log(`${file}: словарь устарел, запустите node tools/gen-cyrillic-words.js`);
    else fs.writeFileSync(full, out);
  }
}
if (check && stale) process.exit(1);
console.log(check ? "словарь в коде совпадает" : `словарь: ${pairs.length} основ`);
