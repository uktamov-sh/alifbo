"""Иконка 512×512 и баннер 1024×500 для Google Play и F-Droid.

Рисунок тот же, что у иконки приложения (app/src/main/res/drawable/ic_launcher_foreground.xml):
три ряда клавиш на лазурном фоне, одна клавиша бирюзовая.
Запуск (Windows, нужен Pillow): python tools/store/make_graphics.py <папка fastlane/metadata/android>
"""
import sys
from pathlib import Path

from PIL import Image, ImageDraw, ImageFont

LAPIS = (0x1E, 0x3C, 0x9E)
TURQ = (0x45, 0xD1, 0xC5)
WHITE = (255, 255, 255)
FONT_BOLD = "C:/Windows/Fonts/segoeuib.ttf"
FONT = "C:/Windows/Fonts/segoeui.ttf"

# Клавиши в координатах 108×108, как в векторной иконке: (x, y, ширина, высота, бирюзовая?).
KEYS = [(x, 34, 10, 12, False) for x in (30, 43, 56, 69)] + \
       [(30, 49, 10, 12, False), (43, 49, 10, 12, True), (56, 49, 10, 12, False), (69, 49, 10, 12, False)] + \
       [(37, 64, 35, 10, False)]


def draw_keys(draw, scale, dx=0.0, dy=0.0):
    for x, y, w, h, accent in KEYS:
        box = [dx + x * scale, dy + y * scale, dx + (x + w) * scale, dy + (y + h) * scale]
        draw.rounded_rectangle(box, radius=2 * scale, fill=TURQ if accent else WHITE)


def icon(path: Path):
    size = 512
    img = Image.new("RGB", (size, size), LAPIS)
    # Ключи в векторе занимают центр 30..79 из 108; увеличиваем до заметного размера.
    scale = size / 72
    draw_keys(ImageDraw.Draw(img), scale, dx=-18 * scale, dy=-18 * scale)
    img.save(path)


def feature(path: Path):
    w, h = 1024, 500
    img = Image.new("RGB", (w, h), LAPIS)
    d = ImageDraw.Draw(img)
    draw_keys(d, 5.0, dx=-80, dy=-60)
    title = ImageFont.truetype(FONT_BOLD, 96)
    sub = ImageFont.truetype(FONT, 40)
    letters = ImageFont.truetype(FONT_BOLD, 64)
    d.text((440, 110), "Alifbo", font=title, fill=WHITE)
    d.text((444, 230), "Ş  Ç  Ö  Ğ  ʼ", font=letters, fill=TURQ)
    d.text((444, 330), "yangi özbek alifbosi", font=sub, fill=WHITE)
    d.text((444, 385), "новый узбекский алфавит", font=sub, fill=(0xC9, 0xD3, 0xF5))
    img.save(path)


if __name__ == "__main__":
    root = Path(sys.argv[1])
    for locale in ("uz", "ru-RU", "en-US"):
        images = root / locale / "images"
        images.mkdir(parents=True, exist_ok=True)
        icon(images / "icon.png")
        feature(images / "featureGraphic.png")
    print("ok")
