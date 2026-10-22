#Requires -RunAsAdministrator
# Alifbo: удаление раскладки «Özbek (yangi alifbo)».
$ErrorActionPreference = 'Stop'

$Klid = 'a0a10443'
$Dll = 'kbduzya.dll'
$Tip = "0443:$($Klid.ToUpper())"

# 1. Убираем раскладку из списка языков пользователя (язык без раскладок тоже убираем).
$list = Get-WinUserLanguageList
foreach ($lang in @($list)) {
    if ($lang.InputMethodTips -contains $Tip) {
        $lang.InputMethodTips.Remove($Tip) | Out-Null
        if ($lang.InputMethodTips.Count -eq 0) { $list.Remove($lang) | Out-Null }
    }
}
Set-WinUserLanguageList $list -Force

# 2. Регистрация в системе.
$key = "HKLM:\SYSTEM\CurrentControlSet\Control\Keyboard Layouts\$Klid"
if (Test-Path $key) { Remove-Item $key -Recurse -Force }

# 3. Файлы. Если Windows ещё держит раскладку, файл удалится после перезагрузки и повторного запуска.
$locked = @()
foreach ($dir in "$env:SystemRoot\System32", "$env:SystemRoot\SysWOW64") {
    $file = Join-Path $dir $Dll
    if (Test-Path $file) {
        try { Remove-Item $file -Force } catch { $locked += $file }
    }
}

Write-Host 'Klaviatura tartibi öçirildi. / Раскладка удалена.'
if ($locked) {
    Write-Host "Kompyuterni qayta yoqib, skriptni yana ishga tushiring: $($locked -join ', ')"
    Write-Host "Перезагрузите компьютер и запустите скрипт ещё раз, чтобы удалить: $($locked -join ', ')"
}
