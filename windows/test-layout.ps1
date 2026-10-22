# Проверяет установленную раскладку через Windows: ToUnicodeEx переводит нажатия в символы,
# как это происходит в любой программе. Раскладка должна быть уже установлена (install.ps1).
param(
    [string]$Klid = 'a0a10443'
)
$ErrorActionPreference = 'Stop'

Add-Type -TypeDefinition @'
using System;
using System.Runtime.InteropServices;
using System.Text;

public static class Kbd {
    [DllImport("user32.dll", CharSet = CharSet.Unicode)]
    public static extern IntPtr LoadKeyboardLayoutW(string klid, uint flags);
    [DllImport("user32.dll", CharSet = CharSet.Unicode)]
    public static extern int ToUnicodeEx(uint vk, uint scan, byte[] state, StringBuilder buf, int cch, uint flags, IntPtr hkl);
    [DllImport("user32.dll")]
    public static extern uint MapVirtualKeyExW(uint code, uint mapType, IntPtr hkl);

    public static string Press(IntPtr hkl, uint vk, bool shift, bool altGr, bool caps, bool ctrl) {
        var state = new byte[256];
        if (shift) { state[0x10] = 0x80; state[0xA0] = 0x80; }
        if (ctrl || altGr) { state[0x11] = 0x80; state[0xA2] = 0x80; }
        if (altGr) { state[0x12] = 0x80; state[0xA5] = 0x80; }
        if (caps) { state[0x14] = 0x01; }
        var buf = new StringBuilder(8);
        uint scan = MapVirtualKeyExW(vk, 0, hkl);
        // Флаг 4: не менять состояние клавиатуры между вызовами.
        int n = ToUnicodeEx(vk, scan, state, buf, buf.Capacity, 4, hkl);
        return n > 0 ? buf.ToString(0, n) : "";
    }
}
'@

$hkl = [Kbd]::LoadKeyboardLayoutW($Klid, 0)
if ($hkl -eq [IntPtr]::Zero) { throw "Windows не загрузила раскладку $Klid" }
$bits = if ([Environment]::Is64BitProcess) { '64' } else { '32' }
Write-Host "Раскладка $Klid загружена (HKL 0x$($hkl.ToString('x'))), процесс $bits-битный"

$VK = @{
    OEM_1 = 0xBA; OEM_PLUS = 0xBB; OEM_COMMA = 0xBC; OEM_MINUS = 0xBD; OEM_PERIOD = 0xBE
    OEM_2 = 0xBF; OEM_3 = 0xC0; OEM_4 = 0xDB; OEM_5 = 0xDC; OEM_6 = 0xDD; OEM_7 = 0xDE
    OEM_102 = 0xE2; SPACE = 0x20; NUMPAD0 = 0x60; NUMPAD7 = 0x67
}
function vk($k) { if ($VK.ContainsKey($k)) { $VK[$k] } else { [int][char]$k } }
function ch([int[]]$codes) { -join ($codes | ForEach-Object { [char]$_ }) }

# Клавиша, режим, ожидаемый символ (кодами Unicode, чтобы файл не зависел от кодировки).
$cases = @(
    # Новые буквы на основных клавишах и Caps Lock для них
    @('OEM_4', '', 0x011F), @('OEM_4', 'shift', 0x011E), @('OEM_4', 'caps', 0x011E), @('OEM_4', 'caps shift', 0x011F)
    @('OEM_6', '', 0x00F6), @('OEM_6', 'shift', 0x00D6), @('OEM_6', 'caps', 0x00D6)
    @('OEM_5', '', 0x00E7), @('OEM_5', 'shift', 0x00C7), @('OEM_5', 'caps', 0x00C7)
    @('OEM_1', '', 0x015F), @('OEM_1', 'shift', 0x015E), @('OEM_1', 'caps', 0x015E)
    @('OEM_7', '', 0x02BC), @('OEM_7', 'shift', 0x0022), @('OEM_7', 'caps', 0x02BC)
    # Вытесненные символы через AltGr
    @('OEM_4', 'altgr', 0x005B), @('OEM_4', 'altgr shift', 0x007B)
    @('OEM_6', 'altgr', 0x005D), @('OEM_6', 'altgr shift', 0x007D)
    @('OEM_5', 'altgr', 0x005C), @('OEM_5', 'altgr shift', 0x007C)
    @('OEM_1', 'altgr', 0x003B), @('OEM_1', 'altgr shift', 0x003A)
    , @('OEM_7', 'altgr', 0x0027)   # запятая: иначе PowerShell развернёт одиночный массив
    # Остальное как в английской раскладке
    @('Q', '', 0x0071), @('Q', 'shift', 0x0051), @('Q', 'caps', 0x0051), @('Q', 'caps shift', 0x0071)
    @('I', '', 0x0069), @('I', 'shift', 0x0049), @('I', 'caps', 0x0049)
    @('A', '', 0x0061), @('Z', 'shift', 0x005A), @('C', '', 0x0063), @('W', '', 0x0077)
    @('1', '', 0x0031), @('1', 'shift', 0x0021), @('2', 'shift', 0x0040), @('0', 'shift', 0x0029)
    @('OEM_MINUS', '', 0x002D), @('OEM_MINUS', 'shift', 0x005F), @('OEM_PLUS', 'shift', 0x002B)
    @('OEM_COMMA', '', 0x002C), @('OEM_COMMA', 'shift', 0x003C), @('OEM_PERIOD', '', 0x002E)
    @('OEM_2', '', 0x002F), @('OEM_2', 'shift', 0x003F), @('OEM_3', '', 0x0060), @('OEM_3', 'shift', 0x007E)
    @('OEM_102', '', 0x005C), @('OEM_102', 'shift', 0x007C)
    @('SPACE', '', 0x0020), @('NUMPAD0', '', 0x0030), @('NUMPAD7', '', 0x0037)
    # Ctrl-коды, на которые рассчитывают консоль и редакторы
    @('OEM_4', 'ctrl', 0x001B), @('A', 'ctrl', 0x0001)
)

$failed = 0
foreach ($c in $cases) {
    $key, $mode, $code = $c
    $expected = ch @($code)
    $actual = [Kbd]::Press($hkl, (vk $key), $mode -match 'shift', $mode -match 'altgr', $mode -match 'caps', $mode -match 'ctrl')
    if ($actual -ne $expected) {
        $failed++
        $got = ($actual.ToCharArray() | ForEach-Object { 'U+{0:X4}' -f [int]$_ }) -join ' '
        Write-Host ("FAIL {0,-10} {1,-12} ожидалось U+{2:X4}, получено [{3}]" -f $key, $mode, $code, $got)
    }
}
Write-Host "$($cases.Count - $failed)/$($cases.Count) проверок прошли"
if ($failed) { exit 1 }
