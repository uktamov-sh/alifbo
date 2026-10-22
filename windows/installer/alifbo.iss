; Установщик Alifbo для Windows: раскладка «Özbek (yangi alifbo)» и конвертер в трее.
; Собирается скриптом windows/installer/build-installer.ps1 после windows/build.ps1.

#define AppVersion "0.1.0"

[Setup]
AppId={{AAB176E6-F487-4873-939D-BB659F8A9692}
AppName=Alifbo
AppVersion={#AppVersion}
AppVerName=Alifbo {#AppVersion}
AppPublisher=Alifbo (uktamov-sh)
AppPublisherURL=https://github.com/uktamov-sh/alifbo
AppSupportURL=https://github.com/uktamov-sh/alifbo/issues
DefaultDirName={autopf}\Alifbo
DisableProgramGroupPage=yes
DisableDirPage=yes
PrivilegesRequired=admin
ArchitecturesAllowed=x64compatible arm64
ArchitecturesInstallIn64BitMode=x64compatible arm64
MinVersion=10.0
OutputDir=..\build\installer
OutputBaseFilename=alifbo-setup-{#AppVersion}
Compression=lzma2
SolidCompression=yes
WizardStyle=modern
UninstallDisplayName=Alifbo — Özbek (yangi alifbo)
UninstallDisplayIcon={app}\AlifboConverter.exe
VersionInfoVersion={#AppVersion}
VersionInfoDescription=Alifbo setup

[Languages]
Name: "en"; MessagesFile: "compiler:Default.isl"
Name: "ru"; MessagesFile: "compiler:Languages\Russian.isl"

[CustomMessages]
en.Autostart=Start the Alifbo converter (Ctrl+Shift+Y) with Windows
ru.Autostart=Запускать конвертер Alifbo (Ctrl+Shift+Y) вместе с Windows
en.ConverterName=Alifbo converter
ru.ConverterName=Конвертер Alifbo
en.RunConverter=Start the converter now
ru.RunConverter=Запустить конвертер сейчас
en.InstallingLayout=Installing the keyboard layout...
ru.InstallingLayout=Устанавливаю раскладку...
en.LayoutFailed=The keyboard layout could not be installed. Run layout\install.ps1 from the Alifbo folder as administrator to see the error.
ru.LayoutFailed=Не удалось установить раскладку. Запустите layout\install.ps1 из папки Alifbo от имени администратора, чтобы увидеть ошибку.
en.Done=Done! Switch to «Özbek (yangi alifbo)» with Win + Space.
ru.Done=Готово! Переключайтесь на «Özbek (yangi alifbo)» сочетанием Win + Пробел.

[Tasks]
Name: "autostart"; Description: "{cm:Autostart}"

[Files]
Source: "..\build\package\*"; DestDir: "{app}\layout"; Flags: recursesubdirs ignoreversion
Source: "..\converter\bin\Release\net48\AlifboConverter.exe"; DestDir: "{app}"; Flags: ignoreversion
Source: "..\..\LICENSE"; DestDir: "{app}"; DestName: "LICENSE.txt"; Flags: ignoreversion

[Icons]
Name: "{autoprograms}\{cm:ConverterName}"; Filename: "{app}\AlifboConverter.exe"
Name: "{autostartup}\{cm:ConverterName}"; Filename: "{app}\AlifboConverter.exe"; Tasks: autostart

[Run]
Filename: "{app}\AlifboConverter.exe"; Description: "{cm:RunConverter}"; Flags: nowait postinstall skipifsilent

[Code]
function PowerShell(Script: String): Integer;
var
  ResultCode: Integer;
begin
  { 64-битный PowerShell: install.ps1 работает только в нём. }
  if not Exec(ExpandConstant('{sysnative}\WindowsPowerShell\v1.0\powershell.exe'),
      '-NoProfile -ExecutionPolicy Bypass -File "' + ExpandConstant('{app}\layout\') + Script + '"',
      '', SW_HIDE, ewWaitUntilTerminated, ResultCode) then
    ResultCode := -1;
  Result := ResultCode;
end;

procedure CurStepChanged(CurStep: TSetupStep);
begin
  if CurStep = ssPostInstall then
  begin
    WizardForm.StatusLabel.Caption := CustomMessage('InstallingLayout');
    if PowerShell('install.ps1') <> 0 then
      SuppressibleMsgBox(CustomMessage('LayoutFailed'), mbError, MB_OK, IDOK)
    else if not WizardSilent then
      MsgBox(CustomMessage('Done'), mbInformation, MB_OK);
  end;
end;

procedure CurUninstallStepChanged(CurUninstallStep: TUninstallStep);
var
  ResultCode: Integer;
begin
  if CurUninstallStep = usUninstall then
  begin
    Exec(ExpandConstant('{sys}\taskkill.exe'), '/F /IM AlifboConverter.exe', '', SW_HIDE, ewWaitUntilTerminated, ResultCode);
    PowerShell('uninstall.ps1');
  end;
end;
