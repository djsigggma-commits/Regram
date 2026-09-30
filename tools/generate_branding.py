#!/usr/bin/env python3
"""Generate build-type resource overlays without rewriting upstream translations."""
from pathlib import Path
import re
import xml.etree.ElementTree as ET

ROOT = Path(__file__).resolve().parents[1]
# Do not rewrite licenses, author credits, URLs, Java packages or plugin API aliases.
# Only interface strings: do not rename code identifiers, URLs, plugin API aliases,
# original source translations, copyright notices or the credited source handles.
RENAME = {
    "OpenExteraPreferences", "CustomTitleHint", "OEGeneralResetSettingsDone",
    "OECrashTitle", "AppIconRegram", "OfficialChannel", "XChannel",
    "OEGeneralNagramSettings", "UnifiedPushNeverReceivedNotifications",
    "UnifiedPushLastReceivedNotification", "OEGlyphInfo", "N_Config",
    "NekoSettings", "PasscodeShowInSettings", "CloudConfigDesc",
    "PushServiceTypeInApp",
}
BRAND = re.compile(r"(?i)nagram[ -]?xf|nagram[ -]?x|nagram|exteragramm?|exteraless")
FIXED = {
    "AppName": "re:gram", "AppNameBeta": "re:gram Beta",
    "Nagram": "re:gram", "NagramX": "re:gram", "OpenExtera": "re:gram",
}


def overlays(root=ROOT):
    result = {}
    for values in sorted((root / "TMessagesProj/src/main/res").glob("values*")):
        strings = dict(FIXED) if values.name == "values" else {}
        for source in sorted(values.glob("*.xml")):
            for node in ET.parse(source).getroot().findall("string"):
                name = node.get("name")
                if name in FIXED:
                    strings[name] = FIXED[name]
                elif name in {"OfficialChannel", "XChannel"} and node.text and BRAND.search(node.text):
                    # Legacy channel titles must not claim to be re:gram's channel.
                    strings[name] = "Канал источника" if values.name == "values-ru-rRU" else "Source channel"
                elif name in RENAME and node.text and BRAND.search(node.text):
                    strings[name] = BRAND.sub("re:gram", node.text)
        if not strings:
            continue
        if values.name in {"values", "values-ru-rRU"}:
            strings["OpenExteraInfo"] = (
                "Настройки и возможности re:gram" if values.name.endswith("ru-rRU")
                else "re:gram settings and features"
            )
        resources = ET.Element("resources")
        for name, text in sorted(strings.items()):
            ET.SubElement(resources, "string", name=name).text = text
        ET.indent(resources, space="    ")
        result[Path(values.name) / "strings_branding.xml"] = (
            '<?xml version="1.0" encoding="utf-8"?>\n'
            + ET.tostring(resources, encoding="unicode") + "\n"
        )
    return result


if __name__ == "__main__":
    destination = ROOT / "TMessagesProj/src/branding/res"
    for relative, content in overlays().items():
        target = destination / relative
        target.parent.mkdir(parents=True, exist_ok=True)
        target.write_text(content, encoding="utf-8")
    print(f"Generated branding in {destination}")
