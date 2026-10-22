#!/usr/bin/env bash
# Запуск сквозных тестов на эмуляторе в CI: ставим приложение, включаем клавиатуру, гоняем Maestro.
set -euo pipefail
adb install -r app/build/outputs/apk/debug/app-debug.apk
sleep 5
adb shell ime enable uz.alifbo.keyboard/.AlifboIME
adb shell ime set uz.alifbo.keyboard/.AlifboIME
adb shell settings put secure show_ime_with_hard_keyboard 1
adb shell settings get secure default_input_method
"$HOME/.maestro/bin/maestro" test .maestro --format junit --output maestro-report.xml
