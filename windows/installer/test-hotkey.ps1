# Проверка горячей клавиши на настоящем рабочем столе: печатаем старый текст в Блокноте,
# выделяем, жмём Ctrl+Shift+Y и смотрим, что получилось и вернулся ли прежний буфер обмена.
param(
    [string]$Exe = "$env:ProgramFiles\Alifbo\AlifboConverter.exe"
)
$ErrorActionPreference = 'Stop'

$expected = "$([char]0x00C7)oy $([char]0x015F)ahar"   # Çoy şahar
$converter = Start-Process $Exe -PassThru
Start-Sleep 3
$notepad = Start-Process notepad -PassThru
Start-Sleep 2
try {
    $shell = New-Object -ComObject WScript.Shell
    $null = $shell.AppActivate($notepad.Id)
    Start-Sleep 1
    Set-Clipboard -Value 'prejniy bufer'
    $shell.SendKeys('Choy shahar'); Start-Sleep 1
    $shell.SendKeys('^a'); Start-Sleep 1
    $shell.SendKeys('^+y'); Start-Sleep 3

    $restored = (Get-Clipboard -Raw).Trim()
    $shell.SendKeys('^a'); Start-Sleep 1
    $shell.SendKeys('^c'); Start-Sleep 1
    $result = (Get-Clipboard -Raw).Trim()

    Write-Host "Блокнот: [$result], буфер после конвертации: [$restored]"
    if ($result -ne $expected) { throw "Ожидалось [$expected], получено [$result]" }
    if ($restored -ne 'prejniy bufer') { throw "Буфер обмена не вернулся: [$restored]" }
    Write-Host 'Горячая клавиша работает, буфер обмена восстановлен.'
} finally {
    Stop-Process -Id $notepad.Id -Force -ErrorAction SilentlyContinue
    Stop-Process -Id $converter.Id -Force -ErrorAction SilentlyContinue
}
