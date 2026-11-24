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
