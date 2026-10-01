#!/usr/bin/env python3
"""Generate launcher icons from the supplied light/dark square artwork.

Keep the artwork inside the adaptive icon's 66/108 dp safe zone: Samsung's
launcher masks and scales a full-bleed background much more aggressively.
The -night resources follow the device's dark mode.
"""
from pathlib import Path
from PIL import Image

ROOT = Path(__file__).resolve().parents[1]
RES = ROOT / "TMessagesProj/src/branding/res"
SIZES = {"mdpi": 48, "hdpi": 72, "xhdpi": 96, "xxhdpi": 144, "xxxhdpi": 192}
CANVAS = 432
# Android adaptive icons reserve 18dp bleed on each side of their 108dp canvas;
# the guaranteed safe center is 66dp. Keep all artwork within it (264/432px).
ADAPTIVE_ART_WIDTH = 264
ADAPTIVE_MARK_WIDTH = 208


def mark_layer(mark, width):
    height = round(width * mark.height / mark.width)
    assert width <= CANVAS and height <= CANVAS
    layer = Image.new("RGBA", (CANVAS, CANVAS), (0, 0, 0, 0))
    layer.alpha_composite(mark.resize((width, height), Image.Resampling.LANCZOS),
                          ((CANVAS - width) // 2, (CANVAS - height) // 2))
    return layer


def generate():
    for variant, qualifier in (("light", ""), ("dark", "-night")):
        with Image.open(ROOT / f"logo/icon_{variant}.png") as source, \
             Image.open(ROOT / "logo/icon_clean_light.png") as mono_source:
            artwork = source.convert("RGBA")
            base = Image.new("RGBA", (CANVAS, CANVAS), artwork.getpixel((0, 0)))
            foreground = mark_layer(artwork, ADAPTIVE_ART_WIDTH)
            directory = RES / f"drawable{qualifier}-nodpi"
            directory.mkdir(parents=True, exist_ok=True)
            for name, image in (("regram_launcher_background", base),
                                ("regram_launcher_foreground", foreground),
                                ("regram_launcher_art", artwork.resize((CANVAS, CANVAS), Image.Resampling.LANCZOS)),
                                ("regram_launcher_monochrome", mark_layer(
                                    mono_source.convert("RGBA"), ADAPTIVE_MARK_WIDTH))):
                image.save(directory / f"{name}.png", optimize=True)
            for density, size in SIZES.items():
                directory = RES / f"mipmap{qualifier}-{density}"
                directory.mkdir(parents=True, exist_ok=True)
                icon = artwork.resize((size, size), Image.Resampling.LANCZOS)
                for name in ("ic_launcher_regram", "ic_launcher_regram_round"):
                    icon.save(directory / f"{name}.png", optimize=True)

    directory = RES / "mipmap-anydpi-v26"
    directory.mkdir(parents=True, exist_ok=True)
    xml = '''<?xml version="1.0" encoding="utf-8"?>
<adaptive-icon xmlns:android="http://schemas.android.com/apk/res/android">
    <background android:drawable="@drawable/regram_launcher_background"/>
    <foreground android:drawable="@drawable/regram_launcher_foreground"/>
    <monochrome android:drawable="@drawable/regram_launcher_monochrome"/>
</adaptive-icon>
'''
    for name in ("ic_launcher_regram", "ic_launcher_regram_round"):
        (directory / f"{name}.xml").write_text(xml, encoding="utf-8")


if __name__ == "__main__":
    generate()
    print(f"Generated launcher icons in {RES}")
