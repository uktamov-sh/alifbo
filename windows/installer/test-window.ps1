# Проверка окна конвертера: запуск с --tray не показывает окно, второй запуск (как ярлык
# в «Пуске») не создаёт вторую копию, а открывает окно первой; в окне текст переводится сразу.
param(
    [string]$Exe = "$env:ProgramFiles\Alifbo\AlifboConverter.exe"
)
$ErrorActionPreference = 'Stop'
Add-Type -AssemblyName UIAutomationClient, UIAutomationTypes

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

    $root = [System.Windows.Automation.AutomationElement]::FromHandle($first.MainWindowHandle)
    # Многострочные поля WinForms UI Automation отдаёт как Edit или Document: берём всё, у чего есть значение.
    $hasValue = New-Object System.Windows.Automation.PropertyCondition(
        [System.Windows.Automation.AutomationElement]::IsValuePatternAvailableProperty, $true)
    $values = @()
    foreach ($el in $root.FindAll([System.Windows.Automation.TreeScope]::Descendants, $hasValue)) {
        $values += $el.GetCurrentPattern([System.Windows.Automation.ValuePattern]::Pattern).Current.Value.Trim()
    }
    Write-Host "Поля окна: [$($values -join '] [')]"
    if ($values -notcontains 'Choy shahar') { throw 'Текст не попал в поле ввода' }
    if ($values -notcontains $expected) { throw "Ожидался результат [$expected]" }
    Write-Host 'Окно конвертера работает: одна копия, перевод сразу при наборе.'
} finally {
    Get-Process AlifboConverter -ErrorAction SilentlyContinue | Stop-Process -Force
}
