#!/usr/bin/env python3
"""Build the white-surface AMOLED Light variants from the two Monet Light palettes.

Keep accent/text/bubble colors from the source palette; only replace the
surfaces with pure white so text and message bubbles remain distinguishable.
"""
from pathlib import Path

ASSETS = Path(__file__).resolve().parents[1] / "TMessagesProj/src/main/assets"
WHITE_SURFACES = {
    "actionBarBrowser", "actionBarDefault", "actionBarDefaultArchived",
    "actionBarDefaultSubmenuBackground", "chat_messagePanelBackground",
    "chat_wallpaper", "chats_menuTopBackground", "chats_menuTopBackgroundCats",
    "dialogBackground", "windowBackgroundChecked", "windowBackgroundGray",
    "windowBackgroundUnchecked", "windowBackgroundWhite",
}


def generate():
    for suffix in ("", "_gram"):
        source = ASSETS / f"monet_light{suffix}.attheme"
        target = ASSETS / f"monet_amoled_light{suffix}.attheme"
        lines = source.read_text(encoding="utf-8").splitlines(keepends=True)
        output = []
        seen = set()
        for line in lines:
            key, separator, _ = line.partition("=")
            if separator and key in WHITE_SURFACES:
                line = f"{key}=white\n"
                seen.add(key)
            output.append(line)
        assert seen == WHITE_SURFACES, (source, WHITE_SURFACES - seen)
        target.write_text("".join(output), encoding="utf-8")


if __name__ == "__main__":
    generate()
