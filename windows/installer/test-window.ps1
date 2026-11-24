# Проверка окна конвертера: запуск с --tray не показывает окно, второй запуск (как ярлык
# в «Пуске») не создаёт вторую копию, а открывает окно первой; в окне текст переводится сразу.
param(
    [string]$Exe = "$env:ProgramFiles\Alifbo\AlifboConverter.exe"
)
$ErrorActionPreference = 'Stop'

$title = "Alifbo $([char]0x2014) konvertor"
$expected = "$([char]0x00C7)oy $([char]0x015F)ahar"   # Çoy şahar

$first = Start-Process $Exe -ArgumentList '--tray' -PassThru
Start-Sleep 3
try {
    $first.Refresh()
    if ($first.MainWindowTitle) { throw "С --tray окно не должно открываться: [$($first.MainWindowTitle)]" }

    $second = Start-Process $Exe -PassThru
    if (-not $second.WaitForExit(10000)) { throw 'Второй запуск не завершился' }
    Start-Sleep 2
    $count = @(Get-Process AlifboConverter).Count
    if ($count -ne 1) { throw "Ожидалась одна копия программы, работает $count" }
    $first.Refresh()
    if ($first.MainWindowTitle -ne $title) { throw "Окно не открылось: [$($first.MainWindowTitle)]" }

    $shell = New-Object -ComObject WScript.Shell
    $null = $shell.AppActivate($first.Id)
    Start-Sleep 1
    $shell.SendKeys('Choy shahar'); Start-Sleep 1

    # Снимок окна для просмотра вёрстки (артефакт window-screenshot в CI).
    Add-Type -AssemblyName System.Drawing
    Add-Type @"
using System; using System.Runtime.InteropServices;
public static class Win { [StructLayout(LayoutKind.Sequential)] public struct R { public int L, T, Ri, B; }
  [DllImport("user32.dll")] public static extern bool GetWindowRect(IntPtr h, out R r); }
"@
    $r = New-Object Win+R
    $null = [Win]::GetWindowRect($first.MainWindowHandle, [ref]$r)
    $bmp = New-Object System.Drawing.Bitmap ($r.Ri - $r.L), ($r.B - $r.T)
    $g = [System.Drawing.Graphics]::FromImage($bmp)
    $g.CopyFromScreen($r.L, $r.T, 0, 0, $bmp.Size)
    $null = New-Item -ItemType Directory -Force 'windows\build'
    $bmp.Save((Join-Path (Get-Location) 'windows\build\window.png'))
    $g.Dispose(); $bmp.Dispose()

    # Ctrl+Enter в окне копирует результат: читаем его из буфера обмена.
    Set-Clipboard -Value 'pusto'
    $shell.SendKeys('^{ENTER}'); Start-Sleep 1
    $copied = (Get-Clipboard -Raw).Trim()
    Write-Host "Результат в буфере: [$copied]"
    if ($copied -ne $expected) { throw "Ожидался результат [$expected], получено [$copied]" }
    Write-Host 'Окно конвертера работает: одна копия, перевод сразу при наборе.'
} finally {
    Get-Process AlifboConverter -ErrorAction SilentlyContinue | Stop-Process -Force
}
