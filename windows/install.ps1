#Requires -RunAsAdministrator
# Alifbo: установка раскладки «Özbek (yangi alifbo)» для Windows 10 и 11 (x64 и ARM64).
# O‘rnatish: PowerShell'ni administrator sifatida oching va bu faylni ishga tushiring.
# Установка: откройте PowerShell от имени администратора и запустите этот файл.
$ErrorActionPreference = 'Stop'

$Klid = 'a0a10443'                 # 0443 = узбекский (латиница)
$Dll = 'kbduzya.dll'
$LayoutText = "$([char]0x00D6)zbek (yangi alifbo)"
$Tip = "0443:$($Klid.ToUpper())"
$LayoutsKey = 'HKLM:\SYSTEM\CurrentControlSet\Control\Keyboard Layouts'

if ([Environment]::Is64BitOperatingSystem -and -not [Environment]::Is64BitProcess) {
    throw '64 bitli PowerShell kerak. / Нужен 64-битный PowerShell.'
}
$native = switch ($env:PROCESSOR_ARCHITECTURE) {
    'AMD64' { 'x64' }
    'ARM64' { 'arm64' }
    default { throw "Bu protsessor qöllab-quvvatlanmaydi / Процессор не поддерживается: $env:PROCESSOR_ARCHITECTURE" }
}

# 1. Файлы не подменены: сверяем с SHA256SUMS из пакета.
foreach ($line in Get-Content "$PSScriptRoot\SHA256SUMS") {
    $hash, $path = $line -split '\s+', 2
    $actual = (Get-FileHash (Join-Path $PSScriptRoot $path) -Algorithm SHA256).Hash
    if ($actual -ne $hash) { throw "Fayl özgargan / Файл изменён: $path" }
}

# 2. Раскладка в системные папки.
Copy-Item "$PSScriptRoot\$native\$Dll" "$env:SystemRoot\System32\$Dll" -Force
Copy-Item "$PSScriptRoot\wow64\$Dll" "$env:SystemRoot\SysWOW64\$Dll" -Force

# 3. Регистрация раскладки в Windows.
$key = "$LayoutsKey\$Klid"
$layoutId = $null
if (Test-Path $key) {
    $existing = Get-ItemProperty $key
    if ($existing.'Layout File' -and $existing.'Layout File' -ne $Dll) {
        throw "Raqam $Klid band / Номер $Klid занят раскладкой $($existing.'Layout File')"
    }
    $layoutId = $existing.'Layout Id'
}
if (-not $layoutId) {
    # Layout Id должен быть уникальным среди всех раскладок системы.
    $used = Get-ChildItem $LayoutsKey | ForEach-Object { (Get-ItemProperty $_.PSPath).'Layout Id' } |
        Where-Object { $_ } | ForEach-Object { [Convert]::ToInt32($_, 16) }
    $layoutId = '{0:x4}' -f (($used | Measure-Object -Maximum).Maximum + 1)
}
New-Item -Path $key -Force | Out-Null
Set-ItemProperty $key 'Layout File' $Dll
Set-ItemProperty $key 'Layout Text' $LayoutText
Set-ItemProperty $key 'Layout Display Name' "@%SystemRoot%\system32\$Dll,-1000"
Set-ItemProperty $key 'Layout Id' $layoutId

# 4. Добавляем раскладку в список языков текущего пользователя.
$list = Get-WinUserLanguageList
$uz = $list | Where-Object { $_.LanguageTag -like 'uz-Latn*' } | Select-Object -First 1
if (-not $uz) {
    $list.Add('uz-Latn-UZ')
    $uz = $list | Where-Object { $_.LanguageTag -like 'uz-Latn*' } | Select-Object -First 1
    # Язык только что добавлен: оставляем в нём одну нашу раскладку.
    $uz.InputMethodTips.Clear()
}
if ($uz.InputMethodTips -notcontains $Tip) { $uz.InputMethodTips.Add($Tip) }
Set-WinUserLanguageList $list -Force

Write-Host "Tayyor! Win + Probel bilan «$LayoutText» ni tanlang."
Write-Host "Готово! Выберите «$LayoutText» сочетанием Win + Пробел."
