// Частотный словарь русского языка из FrequencyWords (stdin: ru_50k.txt, строки «слово число»).
const readline = require("readline");

const LIMIT = Number(process.env.LIMIT || 50000);
const SOURCE = process.env.SOURCE || "hermitdave/FrequencyWords ru_50k.txt";
const words = [];
const seen = new Set();

readline.createInterface({ input: process.stdin, crlfDelay: Infinity })
  .on("line", line => {
    const w = line.trim().split(/\s+/)[0];
    if (!w || w.length < 2 || !/^[а-яё]+(-[а-яё]+)?$/u.test(w) || seen.has(w)) return;
    seen.add(w);
    words.push(w);
  })
  .on("close", () => {
    const out = [
      `# Russian word frequency list from ${SOURCE} (OpenSubtitles 2018).`,
      "# FrequencyWords by Hermit Dave, CC BY-SA 4.0, https://github.com/hermitdave/FrequencyWords",
      ...words.slice(0, LIMIT),
    ];
    process.stdout.write(out.join("\n") + "\n");
    console.error(`words=${Math.min(words.length, LIMIT)}`);
  });
