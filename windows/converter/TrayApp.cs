// Alifbo: значок в трее. Выделите текст в любой программе и нажмите Ctrl+Shift+Y —
// он перепишется со старой латиницы или кириллицы на новый алфавит.
//
// Приватность: программа не подключается к сети и ничего не записывает. Буфер обмена она читает
// только в момент нажатия горячей клавиши и через секунду возвращает в нём прежний текст.
using System;
using System.Diagnostics;
using System.Drawing;
using System.Globalization;
using System.Runtime.InteropServices;
using System.Threading;
using System.Windows.Forms;

namespace Alifbo
{
    internal static class Program
    {
        [STAThread]
        private static void Main()
        {
            using (var mutex = new Mutex(true, "Alifbo.Converter.SingleInstance", out bool created))
            {
                if (!created) return;
                Application.EnableVisualStyles();
                Application.SetCompatibleTextRenderingDefault(false);
                Application.Run(new TrayContext());
            }
        }
    }

    internal static class Texts
    {
        private static readonly bool Ru = CultureInfo.CurrentUICulture.TwoLetterISOLanguageName == "ru";

        public static string Tooltip => Ru ? "Alifbo: Ctrl+Shift+Y — на новый алфавит" : "Alifbo: Ctrl+Shift+Y — yangi alifboga";
        public static string Started => Ru
            ? "Выделите текст и нажмите Ctrl+Shift+Y: он перепишется на новый алфавит."
            : "Matnni belgilab, Ctrl+Shift+Y ni bosing: u yangi alifboga öğiriladi.";
        public static string SelectFirst => Ru ? "Сначала выделите текст." : "Avval matnni belgilang.";
        public static string HotkeyBusy => Ru
            ? "Сочетание Ctrl+Shift+Y занято другой программой."
            : "Ctrl+Shift+Y boşqa dastur tomonidan band.";
        public static string MenuHint => Ru
            ? "Ctrl+Shift+Y: переписать выделенный текст"
            : "Ctrl+Shift+Y: belgilangan matnni öğiriş";
        public static string MenuAbout => Ru ? "О программе" : "Dastur haqida";
        public static string MenuExit => Ru ? "Выход" : "Çiqiş";
    }

    internal sealed class TrayContext : ApplicationContext
    {
        private readonly NotifyIcon icon;
        private readonly HotkeyWindow hotkey;
        private readonly System.Windows.Forms.Timer restoreTimer = new System.Windows.Forms.Timer { Interval = 1000 };
        private string savedClipboard;
        private bool busy;

        public TrayContext()
        {
            hotkey = new HotkeyWindow(ConvertSelection);
            var menu = new ContextMenuStrip();
            menu.Items.Add(new ToolStripMenuItem(Texts.MenuHint) { Enabled = false });
            menu.Items.Add(new ToolStripSeparator());
            menu.Items.Add(Texts.MenuAbout, null, (s, e) => Process.Start("https://github.com/uktamov-sh/alifbo"));
            menu.Items.Add(Texts.MenuExit, null, (s, e) => ExitThread());
            icon = new NotifyIcon { Icon = MakeIcon(), Text = Texts.Tooltip, ContextMenuStrip = menu, Visible = true };
            restoreTimer.Tick += (s, e) => RestoreClipboard();
            if (hotkey.Registered) icon.ShowBalloonTip(4000, "Alifbo", Texts.Started, ToolTipIcon.Info);
            else icon.ShowBalloonTip(6000, "Alifbo", Texts.HotkeyBusy, ToolTipIcon.Warning);
        }

        private void ConvertSelection()
        {
            if (busy) return;
            busy = true;
            try
            {
                // Пока пользователь держит Ctrl+Shift, наш Ctrl+C превратился бы в Ctrl+Shift+C.
                Native.WaitForModifiersReleased(1500);
                RestoreClipboard();
                savedClipboard = Retry(() => Clipboard.ContainsText() ? Clipboard.GetText() : null);
                uint before = Native.GetClipboardSequenceNumber();
                Native.SendCtrl(Native.VK_C);
                string selected = WaitForNewClipboardText(before, 800);
                if (string.IsNullOrEmpty(selected))
                {
                    icon.ShowBalloonTip(2500, "Alifbo", Texts.SelectFirst, ToolTipIcon.None);
                    savedClipboard = null;
                    return;
                }
                string converted = AlifboConverter.ToNewLatin(selected, assumeOldLatin: true);
                if (converted == selected)
                {
                    RestoreClipboard();
                    return;
                }
                Retry(() => { Clipboard.SetText(converted); return true; });
                Native.SendCtrl(Native.VK_V);
                // Программа читает буфер не сразу: прежний текст возвращаем через секунду.
                restoreTimer.Start();
            }
            finally
            {
                busy = false;
            }
        }

        private string WaitForNewClipboardText(uint before, int timeoutMs)
        {
            var watch = Stopwatch.StartNew();
            while (watch.ElapsedMilliseconds < timeoutMs)
            {
                if (Native.GetClipboardSequenceNumber() != before)
                {
                    Thread.Sleep(30);
                    return Retry(() => Clipboard.ContainsText() ? Clipboard.GetText() : null);
                }
                Thread.Sleep(20);
            }
            return null;
        }

        private void RestoreClipboard()
        {
            restoreTimer.Stop();
            if (savedClipboard == null) return;
            string text = savedClipboard;
            savedClipboard = null;
            Retry(() => { Clipboard.SetText(text); return true; });
        }

        // Буфер обмена бывает занят другой программой на доли секунды.
        private static T Retry<T>(Func<T> action)
        {
            for (int i = 0; ; i++)
            {
                try { return action(); }
                catch (ExternalException) when (i < 5) { Thread.Sleep(40); }
            }
        }

        private static Icon MakeIcon()
        {
            using (var bmp = new Bitmap(32, 32))
            using (var g = Graphics.FromImage(bmp))
            using (var bg = new SolidBrush(Color.FromArgb(0x1E, 0x3C, 0x9E)))
            using (var font = new Font("Segoe UI", 17, FontStyle.Bold, GraphicsUnit.Pixel))
            {
                g.SmoothingMode = System.Drawing.Drawing2D.SmoothingMode.AntiAlias;
                g.TextRenderingHint = System.Drawing.Text.TextRenderingHint.AntiAliasGridFit;
                g.FillEllipse(bg, 0, 0, 31, 31);
                var format = new StringFormat { Alignment = StringAlignment.Center, LineAlignment = StringAlignment.Center };
                g.DrawString("Ö", font, Brushes.White, new RectangleF(0, 1, 32, 32), format);
                return Icon.FromHandle(bmp.GetHicon());
            }
        }

        protected override void ExitThreadCore()
        {
            RestoreClipboard();
            icon.Visible = false;
            icon.Dispose();
            hotkey.Dispose();
            base.ExitThreadCore();
        }
    }

    internal sealed class HotkeyWindow : NativeWindow, IDisposable
    {
        private const int WM_HOTKEY = 0x0312;
        private const uint MOD_CONTROL = 0x2, MOD_SHIFT = 0x4, MOD_NOREPEAT = 0x4000;
        private readonly Action onHotkey;

        public bool Registered { get; }

        public HotkeyWindow(Action onHotkey)
        {
            this.onHotkey = onHotkey;
            CreateHandle(new CreateParams());
            Registered = Native.RegisterHotKey(Handle, 1, MOD_CONTROL | MOD_SHIFT | MOD_NOREPEAT, (uint)Keys.Y);
        }

        protected override void WndProc(ref Message m)
        {
            if (m.Msg == WM_HOTKEY) onHotkey();
            base.WndProc(ref m);
        }

        public void Dispose()
        {
            if (Registered) Native.UnregisterHotKey(Handle, 1);
            DestroyHandle();
        }
    }

    internal static class Native
    {
        public const ushort VK_C = 0x43, VK_V = 0x56;
        private const ushort VK_SHIFT = 0x10, VK_CONTROL = 0x11, VK_MENU = 0x12, VK_LWIN = 0x5B, VK_RWIN = 0x5C;
        private const uint INPUT_KEYBOARD = 1, KEYEVENTF_KEYUP = 2;

        [DllImport("user32.dll", SetLastError = true)]
        public static extern bool RegisterHotKey(IntPtr hWnd, int id, uint modifiers, uint vk);

        [DllImport("user32.dll")]
        public static extern bool UnregisterHotKey(IntPtr hWnd, int id);

        [DllImport("user32.dll")]
        public static extern uint GetClipboardSequenceNumber();

        [DllImport("user32.dll")]
        private static extern short GetAsyncKeyState(int vk);

        [DllImport("user32.dll", SetLastError = true)]
        private static extern uint SendInput(uint count, INPUT[] inputs, int size);

        public static void WaitForModifiersReleased(int timeoutMs)
        {
            var watch = Stopwatch.StartNew();
            ushort[] keys = { VK_SHIFT, VK_CONTROL, VK_MENU, VK_LWIN, VK_RWIN };
            while (watch.ElapsedMilliseconds < timeoutMs && Array.Exists(keys, k => (GetAsyncKeyState(k) & 0x8000) != 0))
                Thread.Sleep(20);
        }

        public static void SendCtrl(ushort vk)
        {
            INPUT[] inputs = { Key(VK_CONTROL, false), Key(vk, false), Key(vk, true), Key(VK_CONTROL, true) };
            SendInput((uint)inputs.Length, inputs, Marshal.SizeOf(typeof(INPUT)));
        }

        private static INPUT Key(ushort vk, bool up) => new INPUT
        {
            type = INPUT_KEYBOARD,
            u = new InputUnion { ki = new KEYBDINPUT { wVk = vk, dwFlags = up ? KEYEVENTF_KEYUP : 0 } },
        };

        [StructLayout(LayoutKind.Sequential)]
        private struct INPUT
        {
            public uint type;
            public InputUnion u;
        }

        [StructLayout(LayoutKind.Explicit)]
        private struct InputUnion
        {
            [FieldOffset(0)] public MOUSEINPUT mi;
            [FieldOffset(0)] public KEYBDINPUT ki;
        }

        [StructLayout(LayoutKind.Sequential)]
        private struct MOUSEINPUT
        {
            public int dx, dy;
            public uint mouseData, dwFlags, time;
            public IntPtr dwExtraInfo;
        }

        [StructLayout(LayoutKind.Sequential)]
        private struct KEYBDINPUT
        {
            public ushort wVk, wScan;
            public uint dwFlags, time;
            public IntPtr dwExtraInfo;
        }
    }
}
