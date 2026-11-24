// Проверяет C#-конвертер на общих эталонах spec/cases.txt и режиме горячей клавиши.
using System;
using System.IO;
using Alifbo;

class Check
{
    static int Main(string[] args)
    {
        string path = args.Length > 0 ? args[0] : Path.Combine("spec", "cases.txt");
        string dir = Path.GetDirectoryName(Path.GetFullPath(path));
        int total = 0, failed = 0;
        var files = new (string name, Func<string, string> convert)[]
        {
            (Path.GetFileName(path), s => AlifboConverter.ToNewLatin(s)),
            ("cases-old-latin.txt", s => AlifboConverter.ToOldLatin(s)),
            ("cases-cyrillic.txt", s => AlifboConverter.ToCyrillic(s)),
            ("cases-apostrophes.txt", AlifboConverter.FixApostrophes),
        };
        foreach (var (name, convert) in files)
        {
            string[] lines = File.ReadAllLines(Path.Combine(dir, name), System.Text.Encoding.UTF8);
            for (int i = 0; i < lines.Length; i++)
            {
                string line = lines[i].Trim();
                if (line.Length == 0 || line.StartsWith("#")) continue;
                string[] parts = line.Split(new[] { " → " }, StringSplitOptions.None);
                string input = parts[0].Replace("\\n", "\n"), expected = parts[1].Replace("\\n", "\n");
                total++;
                string actual = convert(input);
                if (actual != expected) { failed++; Report(name + ":" + (i + 1), input, expected, actual); }
            }
        }
        // Режим горячей клавиши: те же ожидания, что в AlifboConverterTest.kt и converter.test.js.
        string[,] hotkey =
        {
            { "Toşkent va Ishoq", "Toşkent va Işoq" },
            { "Şahar, choy va shakar", "Şahar, çoy va şakar" },
            { "Is'hoq ma'no", "Ishoq maʼno" },
            { "https://shop.uz", "https://shop.uz" },
        };
        for (int i = 0; i < hotkey.GetLength(0); i++)
        {
            total++;
            string actual = AlifboConverter.ToNewLatin(hotkey[i, 0], assumeOldLatin: true);
            if (actual != hotkey[i, 1]) { failed++; Report("горячая клавиша", hotkey[i, 0], hotkey[i, 1], actual); }
        }
        Console.OutputEncoding = System.Text.Encoding.UTF8;
        Console.WriteLine($"{total - failed}/{total} примеров прошли");
        return failed == 0 ? 0 : 1;
    }

    static void Report(string where, string input, string expected, string actual) =>
        Console.WriteLine($"{where}: \"{input}\"\n  ожидалось \"{expected}\"\n  получено  \"{actual}\"");
}
