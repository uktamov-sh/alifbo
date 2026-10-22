# Собирает раскладку для x64, ARM64 и 32-битных программ (WOW64) и складывает пакет установки.
# Нужны Visual Studio или Build Tools с C++ (и ARM64) и Windows Driver Kit, где лежит kbd.h.
param(
    [string]$OutDir = "$PSScriptRoot\build"
)
$ErrorActionPreference = 'Stop'
$src = $PSScriptRoot

$vswhere = "${env:ProgramFiles(x86)}\Microsoft Visual Studio\Installer\vswhere.exe"
if (-not (Test-Path $vswhere)) { throw 'Не найдена Visual Studio: нужен компилятор C++ (Visual Studio или Build Tools).' }
$vs = & $vswhere -latest -products * -requires Microsoft.VisualStudio.Component.VC.Tools.x86.x64 -property installationPath
if (-not $vs) { throw 'В Visual Studio нет компонента C++ (VC.Tools.x86.x64).' }
$vcvars = Join-Path $vs 'VC\Auxiliary\Build\vcvarsall.bat'

$kbdh = Get-ChildItem "${env:ProgramFiles(x86)}\Windows Kits\10\Include" -Recurse -Filter kbd.h -ErrorAction SilentlyContinue |
    Sort-Object FullName -Descending | Select-Object -First 1
if (-not $kbdh) { throw 'Не найден kbd.h: установите Windows Driver Kit.' }
Write-Host "kbd.h: $($kbdh.FullName)"

if (Test-Path $OutDir) { Remove-Item $OutDir -Recurse -Force }
$inc = New-Item -ItemType Directory -Force "$OutDir\include"
# Берём только kbd.h, чтобы остальные заголовки режима ядра не подменили обычные.
Copy-Item $kbdh.FullName $inc

$targets = @(
    @{ Name = 'x64';   Vc = 'x64';       Machine = 'X64';   Flags = '' }
    @{ Name = 'arm64'; Vc = 'x64_arm64'; Machine = 'ARM64'; Flags = '' }
    # 32-битные программы на 64-битной Windows: указатели в таблицах должны быть 64-битными.
    @{ Name = 'wow64'; Vc = 'x64_x86';   Machine = 'X86';   Flags = '/DBUILD_WOW6432 /Gz' }
)

foreach ($t in $targets) {
    $dir = New-Item -ItemType Directory -Force "$OutDir\$($t.Name)"
    $steps = @(
        "call `"$vcvars`" $($t.Vc) >nul",
        "cl /nologo /c /W3 /O1 /GS- /Zl /Gy $($t.Flags) /I`"$inc`" /Fo`"$dir\kbduzya.obj`" `"$src\kbduzya.c`"",
        "rc /nologo /fo `"$dir\kbduzya.res`" `"$src\kbduzya.rc`"",
        "link /nologo /dll /noentry /nodefaultlib /release /machine:$($t.Machine) /subsystem:native /def:`"$src\kbduzya.def`" /merge:.rdata=.data /merge:.bss=.data /ignore:4254 /out:`"$dir\kbduzya.dll`" `"$dir\kbduzya.obj`" `"$dir\kbduzya.res`""
    )
    Write-Host "== $($t.Name)"
    cmd /c ($steps -join ' && ')
    if ($LASTEXITCODE -ne 0) { throw "Сборка $($t.Name) не удалась" }
}

# Пакет: DLL для каждой архитектуры, скрипты и контрольные суммы.
$pkg = New-Item -ItemType Directory -Force "$OutDir\package"
foreach ($t in $targets) {
    New-Item -ItemType Directory -Force "$pkg\$($t.Name)" | Out-Null
    Copy-Item "$OutDir\$($t.Name)\kbduzya.dll" "$pkg\$($t.Name)\"
}
Copy-Item "$src\install.ps1", "$src\uninstall.ps1", "$src\test-layout.ps1" $pkg
$sums = Get-ChildItem $pkg -Recurse -File | Sort-Object FullName | ForEach-Object {
    $rel = $_.FullName.Substring($pkg.FullName.Length + 1).Replace('\', '/')
    "$((Get-FileHash $_.FullName -Algorithm SHA256).Hash.ToLower())  $rel"
}
[IO.File]::WriteAllLines("$pkg\SHA256SUMS", $sums)
Write-Host "Пакет: $pkg"
$sums | ForEach-Object { Write-Host "  $_" }
