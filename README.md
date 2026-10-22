# Alifbo

[![CI](https://github.com/uktamov-sh/alifbo/actions/workflows/ci.yml/badge.svg)](https://github.com/uktamov-sh/alifbo/actions/workflows/ci.yml)

Klaviatura yangi özbek alifbosi uçun (21.10.2026 dagi O‘RQ-1184-son Qonun). Ş, Ç, Ö va Ğ harflari
asosiy qatorlarda turadi. Klaviatura internetga ulanmaydi va yozganlaringizni saqlamaydi.

Клавиатура для нового узбекского алфавита (закон O‘RQ-1184 от 21.10.2026). Буквы Ş, Ç, Ö и Ğ стоят
на основных рядах, а не прячутся под долгим нажатием. Клавиатура не подключается к интернету и не
сохраняет набранный текст.

**Сайт:** https://uktamov-sh.github.io/alifbo/ · **Скачать:** [выпуски](https://github.com/uktamov-sh/alifbo/releases)

<p>
  <img src="docs/screenshots/android-light.png" width="260" alt="Клавиатура в светлой теме">
  <img src="docs/screenshots/android-dark.png" width="260" alt="Клавиатура в тёмной теме">
</p>

## Что есть

- **Android-клавиатура** (`app`, Android 8.0+): узбекская и русская раскладки, подсказки из словаря
  на 60 000 узбекских и 48 000 русских слов, кнопка «eski→yangi», эмодзи, крупная буква при нажатии,
  настройки (вибрация, звук, высота клавиш), TalkBack.
- **Раскладка Windows** (`windows`): системная раскладка «Özbek (yangi alifbo)» для Windows 10 и 11,
  x64 и ARM64.
- **Конвертер для Windows** (`windows/converter`): значок в трее. Выделите текст в любой программе
  и нажмите **Ctrl+Shift+Y** — он перепишется на новый алфавит.
- **Конвертер** (`converter`): библиотека на Kotlin без зависимостей. Правила —
  [spec/converter-rules.md](spec/converter-rules.md), эталоны — [spec/cases.txt](spec/cases.txt).
  Версии на C# и JavaScript проверяются теми же эталонами.
- **Прототип** (`prototype`) и **сайт** (`docs`).

## Android

Скачайте `alifbo-0.1.1.apk` из [выпусков](https://github.com/uktamov-sh/alifbo/releases), установите,
откройте приложение и следуйте трём шагам на экране. Отпечаток сертификата подписи (SHA-256):
`f6:56:a0:83:5b:65:c1:ac:67:0e:61:b2:8e:67:fe:22:20:fc:61:c0:2d:4c:71:42:55:99:48:7c:ae:a4:20:2d`.

## Windows

Скачайте `alifbo-setup-0.1.1.exe` из [выпусков](https://github.com/uktamov-sh/alifbo/releases) и
запустите. Установщик ставит раскладку и конвертер, а удаляется обычным способом через «Приложения».
Переключайтесь на раскладку сочетанием Win + Пробел.

Основа — американская раскладка. Пять клавиш справа дают новые буквы, а прежние символы
доступны через AltGr (правый Alt):

| Клавиша | Обычно | Shift | AltGr | AltGr + Shift |
|---|---|---|---|---|
| `[` | ğ | Ğ | `[` | `{` |
| `]` | ö | Ö | `]` | `}` |
| `\` | ç | Ç | `\` | `\|` |
| `;` | ş | Ş | `;` | `:` |
| `'` | ʼ | `"` | `'` | |

Установщик пока не подписан цифровой подписью, поэтому Windows SmartScreen может предупредить о
неизвестном издателе («Подробнее» → «Выполнить в любом случае»). Без установщика: пакет
`alifbo-windows.zip` с `install.ps1`, который сверяет файлы с `SHA256SUMS`.

## Безопасность

Клавиатура видит всё, что набирает человек, поэтому защита заложена в устройство приложения:

- У Android-приложения нет ни одного разрешения, в том числе доступа в интернет. Сборка падает, если
  разрешение появится (задача `checkReleasePermissions`).
- В полях паролей и PIN-кодов клавиатура не читает текст вокруг курсора, ничего не подсказывает,
  не показывает нажатую букву крупно, а TalkBack называет клавиши «символ» (буквы — только в наушниках).
- Набранный текст не сохраняется и не пишется в журнал. Резервное копирование в облако запрещено.
- Нет аналитики, рекламы и сторонних библиотек.
- Конвертер для Windows читает буфер обмена только по Ctrl+Shift+Y и через секунду возвращает в нём
  прежний текст.
- Действия GitHub Actions закреплены по хешу коммита, у сборки только доступ на чтение.

[Политика конфиденциальности](https://uktamov-sh.github.io/alifbo/privacy.html)

## Сборка и тесты

Нужны JDK 17+ и Android SDK (API 36).

```bash
./gradlew :converter:test :app:testDebugUnitTest :app:assembleDebug
node prototype/converter.test.js
```

Раскладка и установщик Windows собираются на GitHub Actions (нужны Visual Studio с C++, Windows Driver
Kit, .NET SDK и Inno Setup): `windows/build.ps1`, затем `windows/installer/build-installer.ps1`.
При каждом коммите автосборка ставит раскладку и установщик на одноразовые машины Windows x64 и ARM64,
проверяет символы через `ToUnicodeEx`, горячую клавишу в Блокноте и удаление.

Словари подсказок собирает workflow [Dictionary](.github/workflows/dictionary.yml) из узбекской
Википедии и FrequencyWords (оба CC BY-SA 4.0). Материалы для Google Play и F-Droid — в
[store](store/README.md).

## Лицензия

[GPL-3.0](LICENSE). Любая доработка этой клавиатуры тоже должна оставаться открытой, чтобы каждый
мог проверить, что она ничего не собирает. Словари — CC BY-SA 4.0 (авторы узбекской Википедии,
FrequencyWords by Hermit Dave).
