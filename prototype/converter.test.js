// Проверяет конвертер прототипа на тех же эталонах, что и Kotlin-версию.
const fs = require("fs");
const path = require("path");
const { toNewLatin } = require("./converter.js");

const lines = fs.readFileSync(path.join(__dirname, "..", "spec", "cases.txt"), "utf8").split(/\r?\n/);
let total = 0, failed = 0;
lines.forEach((raw, i) => {
  const line = raw.trim();
  if (!line || line.startsWith("#")) return;
  const [input, expected] = line.split(" → ").map(s => s.replace(/\\n/g, "\n"));
  total++;
  const actual = toNewLatin(input);
  if (actual !== expected) {
    failed++;
    console.log(`стр. ${i + 1}: ${JSON.stringify(input)}\n  ожидалось ${JSON.stringify(expected)}\n  получено  ${JSON.stringify(actual)}`);
  }
});
// Режим кнопки «eski→yangi»: те же ожидания, что в AlifboConverterTest.kt.
[
  ["Toşkent va Ishoq", "Toşkent va Işoq"],
  ["Şahar, choy va shakar", "Şahar, çoy va şakar"],
  ["Is'hoq ma'no", "Ishoq maʼno"],
  ["https://shop.uz", "https://shop.uz"],
].forEach(([input, expected]) => {
  total++;
  const actual = toNewLatin(input, { assumeOldLatin: true });
  if (actual !== expected) {
    failed++;
    console.log(`режим кнопки: ${JSON.stringify(input)}\n  ожидалось ${JSON.stringify(expected)}\n  получено  ${JSON.stringify(actual)}`);
  }
});
console.log(`${total - failed}/${total} примеров прошли`);
process.exit(failed ? 1 : 0);
