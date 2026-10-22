# Собирает конвертер в трее и установщик alifbo-setup-<версия>.exe.
# Перед этим нужен windows/build.ps1 (DLL раскладки и пакет). Нужны .NET SDK и Inno Setup 6.
$ErrorActionPreference = 'Stop'
$root = Split-Path $PSScriptRoot -Parent

dotnet build "$root\converter\AlifboConverter.csproj" -c Release --nologo
if ($LASTEXITCODE -ne 0) { throw 'Сборка конвертера не удалась' }

$iscc = "${env:ProgramFiles(x86)}\Inno Setup 6\ISCC.exe"
if (-not (Test-Path $iscc)) { throw 'Не найден Inno Setup 6 (ISCC.exe).' }
& $iscc /Q "$PSScriptRoot\alifbo.iss"
if ($LASTEXITCODE -ne 0) { throw 'Сборка установщика не удалась' }

Get-ChildItem "$root\build\installer\*.exe" | ForEach-Object {
    Write-Host ("{0}  {1:N0} KB  sha256 {2}" -f $_.Name, ($_.Length / 1KB), (Get-FileHash $_.FullName).Hash.ToLower())
}
