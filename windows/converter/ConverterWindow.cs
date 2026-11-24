// Окно конвертера: слева текст, справа результат, сверху — куда переводить. Как страница
// konvertor.html на сайте, но без интернета: всё считается здесь же, текст никуда не уходит.
using System;
using System.Drawing;
using System.IO;
using System.Text;
using System.Text.RegularExpressions;
using System.Windows.Forms;

namespace Alifbo
{
    internal sealed class ConverterWindow : Form
    {
        private static readonly Color Accent = Color.FromArgb(0x1E, 0x3C, 0x9E);
        private static readonly Color Turq = Color.FromArgb(0x0E, 0x8F, 0x87);
        private static readonly Color ResultBack = Color.FromArgb(0xDF, 0xF3, 0xF1);

        private const string Sample =
            "Oʻzbekiston Respublikasi — Toshkent shahri. Ўзбекистон Республикаси пойтахти Тошкент шаҳри. Choy, togʻ, maʼno, sentyabr.";

        private readonly TextBox source = MakeBox(readOnly: false);
        private readonly TextBox result = MakeBox(readOnly: true);
        private readonly Label detected = new Label { AutoSize = true, ForeColor = Turq, Anchor = AnchorStyles.Right, Margin = new Padding(0, 6, 0, 0) };
        private readonly Label count = new Label { AutoSize = true, ForeColor = SystemColors.GrayText, Anchor = AnchorStyles.Right, Margin = new Padding(0, 8, 0, 0) };
        private readonly RadioButton[] targets;
        private readonly Button copy;
        private Func<string, string> convert;

        public ConverterWindow(Icon icon)
        {
            Text = Texts.WindowTitle;
            Icon = icon;
            Font = new Font("Segoe UI", 10f);
            StartPosition = FormStartPosition.CenterScreen;
            MinimumSize = new Size(640, 420);
            Size = new Size(980, 600);
            KeyPreview = true;

            Func<string, string>[] converters =
            {
                s => AlifboConverter.ToNewLatin(s),
                AlifboConverter.ToOldLatin,
                AlifboConverter.ToCyrillic,
                AlifboConverter.FixApostrophes,
            };
            string[] names = { Texts.TabNew, Texts.TabOld, Texts.TabCyr, Texts.TabApostrophes };
            var bar = new FlowLayoutPanel { AutoSize = true, WrapContents = true, Dock = DockStyle.Fill, Margin = new Padding(0, 0, 0, 8) };
            targets = new RadioButton[names.Length];
            for (int i = 0; i < names.Length; i++)
            {
                int index = i;
                var b = new RadioButton
                {
                    Text = names[i],
                    Appearance = Appearance.Button,
                    AutoSize = true,
                    FlatStyle = FlatStyle.Flat,
                    Padding = new Padding(10, 4, 10, 4),
                    Margin = new Padding(0, 0, 6, 0),
                    TextAlign = ContentAlignment.MiddleCenter,
                };
                b.FlatAppearance.BorderColor = SystemColors.ControlDark;
                b.FlatAppearance.CheckedBackColor = Accent;
                b.CheckedChanged += (s, e) =>
                {
                    b.ForeColor = b.Checked ? Color.White : SystemColors.ControlText;
                    if (b.Checked) { convert = converters[index]; Run(); }
                };
                targets[i] = b;
                bar.Controls.Add(b);
            }

            var grid = new TableLayoutPanel { Dock = DockStyle.Fill, ColumnCount = 2, RowCount = 4, Padding = new Padding(16) };
            grid.ColumnStyles.Add(new ColumnStyle(SizeType.Percent, 50));
            grid.ColumnStyles.Add(new ColumnStyle(SizeType.Percent, 50));
            grid.RowStyles.Add(new RowStyle(SizeType.AutoSize));
            grid.RowStyles.Add(new RowStyle(SizeType.AutoSize));
            grid.RowStyles.Add(new RowStyle(SizeType.Percent, 100));
            grid.RowStyles.Add(new RowStyle(SizeType.AutoSize));

            grid.Controls.Add(bar, 0, 0);
            grid.SetColumnSpan(bar, 2);
            grid.Controls.Add(Header(Texts.SourceLabel, detected), 0, 1);
            grid.Controls.Add(Header(Texts.ResultLabel, null), 1, 1);
            result.BackColor = ResultBack;
            grid.Controls.Add(source, 0, 2);
            grid.Controls.Add(result, 1, 2);

            var left = Buttons(
                Button(Texts.Paste, (s, e) => { if (Clipboard.ContainsText()) source.Text = Clipboard.GetText(); source.Focus(); }),
                Button(Texts.SampleText, (s, e) => { source.Text = Sample; source.Focus(); }),
                Button(Texts.Clear, (s, e) => { source.Clear(); source.Focus(); }));
            left.Controls.Add(count);
            copy = Button(Texts.Copy + "  (Ctrl+Enter)", null);
            copy.Click += (s, e) => CopyResult();
            copy.FlatStyle = FlatStyle.Flat;
            copy.FlatAppearance.BorderColor = Accent;
            copy.BackColor = Accent;
            copy.ForeColor = Color.White;
            var right = Buttons(
                copy,
                Button(Texts.SaveTxt, (s, e) => SaveResult()),
                Button(Texts.Swap, (s, e) => { source.Text = result.Text; source.Focus(); }));
            grid.Controls.Add(left, 0, 3);
            grid.Controls.Add(right, 1, 3);
            Controls.Add(grid);

            source.TextChanged += (s, e) => Run();
            targets[0].Checked = true;
            ActiveControl = source;
        }

        /// <summary>Показывает окно поверх остальных, даже если оно было свёрнуто или спрятано.</summary>
        public void ShowOnTop()
        {
            Show();
            if (WindowState == FormWindowState.Minimized) WindowState = FormWindowState.Normal;
            Activate();
            BringToFront();
            source.Focus();
        }

        // Крестик прячет окно: программа остаётся в трее и Ctrl+Shift+Y работает.
        protected override void OnFormClosing(FormClosingEventArgs e)
        {
            if (e.CloseReason == CloseReason.UserClosing)
            {
                e.Cancel = true;
                Hide();
                return;
            }
            base.OnFormClosing(e);
        }

        protected override void OnKeyDown(KeyEventArgs e)
        {
            if (e.KeyCode == Keys.Escape) { Hide(); e.Handled = true; }
            if (e.KeyCode == Keys.Enter && e.Control) { CopyResult(); e.Handled = true; e.SuppressKeyPress = true; }
            base.OnKeyDown(e);
        }

        private void Run()
        {
            if (convert == null) return;
            result.Text = convert(source.Text);
            detected.Text = Detect(source.Text);
            count.Text = source.TextLength.ToString("N0");
        }

        // Какие алфавиты есть в тексте: кириллица, новая латиница (Ö Ğ Ş Ç), нынешняя (oʻ gʻ sh ch).
        private static string Detect(string text)
        {
            var kinds = new System.Collections.Generic.List<string>();
            if (Regex.IsMatch(text, "[а-яёўқғҳА-ЯЁЎҚҒҲ]")) kinds.Add(Texts.Cyrillic);
            bool newLetters = Regex.IsMatch(text, "[ÖöĞğŞşÇç]");
            if (newLetters) kinds.Add(Texts.NewLatin);
            if (!newLetters && Regex.IsMatch(text, "[OoGg]['‘’ʻʼ`]|sh|ch|Sh|Ch|SH|CH")) kinds.Add(Texts.OldLatin);
            else if (kinds.Count == 0 && Regex.IsMatch(text, "[A-Za-z]")) kinds.Add(Texts.Latin);
            return kinds.Count == 0 ? "" : Texts.Detected + string.Join(" + ", kinds);
        }

        private void CopyResult()
        {
            if (result.TextLength == 0) return;
            try { Clipboard.SetText(result.Text); }
            catch (System.Runtime.InteropServices.ExternalException) { return; }
            string keep = copy.Text;
            copy.Text = Texts.Copied;
            var t = new Timer { Interval = 1200 };
            t.Tick += (s, e) => { copy.Text = keep; t.Dispose(); };
            t.Start();
        }

        private void SaveResult()
        {
            using (var dialog = new SaveFileDialog { Filter = "Text (*.txt)|*.txt", FileName = "alifbo.txt" })
            {
                if (dialog.ShowDialog(this) == DialogResult.OK)
                    File.WriteAllText(dialog.FileName, result.Text, new UTF8Encoding(true));
            }
        }

        private static TextBox MakeBox(bool readOnly) => new TextBox
        {
            Multiline = true,
            ReadOnly = readOnly,
            ScrollBars = ScrollBars.Vertical,
            AcceptsReturn = true,
            AcceptsTab = false,
            Dock = DockStyle.Fill,
            Font = new Font("Segoe UI", 11f),
            BackColor = SystemColors.Window,
            MaxLength = 0,
            Margin = new Padding(0, 0, 8, 8),
        };

        private static Control Header(string title, Label right)
        {
            var row = new TableLayoutPanel { ColumnCount = 2, AutoSize = true, Dock = DockStyle.Fill, Margin = new Padding(0, 0, 8, 4) };
            row.ColumnStyles.Add(new ColumnStyle(SizeType.AutoSize));
            row.ColumnStyles.Add(new ColumnStyle(SizeType.Percent, 100));
            row.Controls.Add(new Label { Text = title, AutoSize = true, Font = new Font("Segoe UI", 10f, FontStyle.Bold), Margin = new Padding(0, 6, 0, 0) }, 0, 0);
            if (right != null) row.Controls.Add(right, 1, 0);
            return row;
        }

        private static FlowLayoutPanel Buttons(params Control[] controls)
        {
            var panel = new FlowLayoutPanel { AutoSize = true, Dock = DockStyle.Fill, WrapContents = true, Margin = new Padding(0) };
            panel.Controls.AddRange(controls);
            return panel;
        }

        private static Button Button(string text, EventHandler onClick)
        {
            var b = new Button { Text = text, AutoSize = true, FlatStyle = FlatStyle.System, Padding = new Padding(6, 2, 6, 2), Margin = new Padding(0, 0, 6, 0) };
            if (onClick != null) b.Click += onClick;
            return b;
        }
    }
}
