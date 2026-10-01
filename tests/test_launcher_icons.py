"""Launcher artwork comes from the supplied logo, not an upstream icon."""
from pathlib import Path
import hashlib
import xml.etree.ElementTree as ET

import pytest

ROOT = Path(__file__).resolve().parents[1]
RES = ROOT / "TMessagesProj/src/branding/res"


def test_adaptive_icons_use_supplied_artwork():
    for name in ("ic_launcher_regram", "ic_launcher_regram_round"):
        root = ET.parse(RES / "mipmap-anydpi-v26" / f"{name}.xml").getroot()
        android_drawable = "{http://schemas.android.com/apk/res/android}drawable"
        assert root.find("background").get(android_drawable) == "@drawable/regram_launcher_background"
        assert root.find("foreground").get(android_drawable) == "@drawable/regram_launcher_foreground"
        assert root.find("monochrome").get(android_drawable) == "@drawable/regram_launcher_monochrome"
    for variant in ("drawable-nodpi", "drawable-night-nodpi"):
        for name in ("art", "background", "foreground", "monochrome"):
            assert (RES / variant / f"regram_launcher_{name}.png").is_file()
    for density in ("mdpi", "hdpi", "xhdpi", "xxhdpi", "xxxhdpi"):
        for suffix in ("", "-night"):
            for name in ("ic_launcher_regram", "ic_launcher_regram_round"):
                assert (RES / f"mipmap{suffix}-{density}" / f"{name}.png").is_file()


def test_other_launcher_variants_reference_existing_monochrome_art():
    icons = ROOT / 'TMessagesProj/src/main/res/mipmap-anydpi-v26'
    assert (ROOT / 'TMessagesProj/src/main/res/drawable/regram_icon_monochrome.xml').is_file()
    for name in ('go', 'hand', 'mono', 'nothing', 'plus'):
        for suffix in ('', '_round'):
            xml = (icons / f'ic_launcher_{name}{suffix}.xml').read_text()
            assert '@drawable/regram_icon_monochrome' in xml
            assert 'exteraless_icon_monochrome' not in xml


def test_light_and_dark_icons_are_exactly_the_supplied_artwork():
    image = pytest.importorskip("PIL.Image")
    for theme, qualifier in (("light", ""), ("dark", "-night")):
        with image.open(ROOT / f"logo/icon_{theme}.png") as original:
            artwork = original.convert("RGBA")
            expected = artwork.resize((432, 432), image.Resampling.LANCZOS)
            with image.open(RES / f"drawable{qualifier}-nodpi" / "regram_launcher_art.png") as icon:
                assert icon.convert("RGBA").tobytes() == expected.tobytes()
            with image.open(RES / f"drawable{qualifier}-nodpi" / "regram_launcher_background.png") as background:
                assert background.size == (432, 432)
                assert background.getpixel((0, 0)) == background.getpixel((216, 216)) == artwork.getpixel((0, 0))
            with image.open(RES / f"drawable{qualifier}-nodpi" / "regram_launcher_foreground.png") as layer:
                assert layer.size == (432, 432)
                assert layer.getchannel("A").getbbox() == (84, 84, 348, 348)
                assert layer.crop((84, 84, 348, 348)).tobytes() == artwork.resize(
                    (264, 264), image.Resampling.LANCZOS).tobytes()
            for density, size in (("mdpi", 48), ("hdpi", 72), ("xhdpi", 96),
                                  ("xxhdpi", 144), ("xxxhdpi", 192)):
                with image.open(RES / f"mipmap{qualifier}-{density}" / "ic_launcher_regram.png") as icon:
                    assert icon.convert("RGBA").tobytes() == artwork.resize(
                        (size, size), image.Resampling.LANCZOS).tobytes()


def test_in_app_icon_previews_use_layers_and_intro_has_no_zoom():
    java = ROOT / "TMessagesProj/src/main/java"
    selector = (java / "org/telegram/ui/LauncherIconController.java").read_text()
    assert 'REGRAM("RegramIcon", R.drawable.regram_launcher_background,\n                R.drawable.regram_launcher_foreground' in selector
    intro = (java / "org/telegram/ui/IntroActivity.java").read_text()
    assert "layer.setBounds(0, 0, size, size)" in intro
    assert "layer.setBounds(-bleed" not in intro
    about = (java / "app/regram/settings/AboutHeaderCell.java").read_text()
    assert "ImageView.ScaleType.FIT_CENTER" in about


def test_only_explicitly_pinned_plugin_payloads_are_bundled():
    source = ROOT / "TMessagesProj/src/main"
    payloads = {
        "exitFy_v2.py": "c073c4c6ac6915c36675d7f1eab6cc6e8e8d9e36af0d7fbc352968039c1438a6",
        "custom_profile.py": "466b66b99475f12845828eb9a7c48fd3c21269933fc8354b5e458be580c5314f",
    }
    assert not list(source.rglob("*.plugin"))
    assert {p.name for p in (source / "assets").rglob("*.py")} == set(payloads)
    for name, digest in payloads.items():
        assert hashlib.sha256((source / "assets/regram" / name).read_bytes()).hexdigest() == digest
    for name in ("BundledExitFy", "BundledCustomProfile"):
        bridge = (source / "java/app/regram/ui" / f"{name}.java").read_text()
        assert 'installPlugin(staged, false' in bridge
