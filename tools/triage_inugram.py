#!/usr/bin/env python3
"""Triage Inugram patches against the re:gram tree.

`patch --dry-run` matches nothing here: the patches target stock Telegram while
the base is exteraless, whose files moved and already carry many features under
other class names. So applicability is measured per hunk instead: for every
hunk we look for its anchor (the context line above the change) in the current
file, and separately look for the patch's config keys among the settings the
base already ships.

The output is a review queue with confidence labels, never an approval to copy
code: "anchor found" means a small edit is plausible, "absent" means the code
shape differs and must be re-derived by hand.
"""
import argparse
import json
import re
from pathlib import Path

ROOT = Path(__file__).resolve().parents[1]
REGRAM_JAVA = ROOT / "TMessagesProj/src/main/java"


def hunks(patch_text):
    """Yield (file, [context lines], [added lines]) for every hunk of the patch."""
    for block in re.split(r"^diff --git ", patch_text, flags=re.M)[1:]:
        match = re.search(r"b/(TMessagesProj/src/main/java/\S+)", block)
        if not match:
            continue
        path, context, added = match.group(1), [], []
        for line in block.splitlines():
            if line.startswith(("+++", "---")):
                continue
            if line.startswith("@@"):
                if context or added:
                    yield path, context, added
                    context, added = [], []
                continue
            if line.startswith("+"):
                added.append(line[1:].strip())
            elif line.startswith("-"):
                continue
            elif line.startswith(" "):
                context.append(line[1:].strip())
        yield path, context, added


def anchor_hits(path, context):
    target = REGRAM_JAVA / Path(path).relative_to("TMessagesProj/src/main/java")
    if not target.exists():
        return None
    body = target.read_text()
    probes = [c for c in context if len(c) > 12] or context
    if not probes:
        return 1.0
    found = sum(1 for probe in probes if probe in body)
    return found / len(probes)


def applied_hits(path, added):
    """Share of the patch's *new* lines that the current file already contains.

    A high share means the change (or an equivalent) was already folded into the
    base — which is common: exteraless carries Nagram XF fixes under other names.
    It is a review signal, not proof: a line can appear in an unrelated method.
    """
    target = REGRAM_JAVA / Path(path).relative_to("TMessagesProj/src/main/java")
    if not target.exists():
        return None
    body = target.read_text()
    probes = [line for line in added if len(line) > 12] or added
    if not probes:
        return 0.0
    return sum(1 for line in probes if line in body) / len(probes)


def names_in(patch_text):
    """Identifiers a patch introduces — the usual place to spot an inherited feature."""
    return set(re.findall(r"InuConfig\.([A-Z0-9_]+)", patch_text)) | \
        set(re.findall(r"R\.string\.(\w+)", patch_text))


def normalize(name):
    """collapse DISABLE_SWIPE_TO_UNARCHIVE and disableSwipeToUnarchive to one key"""
    return re.sub(r"[^a-z0-9]", "", name.lower())


def base_vocabulary():
    settings = set()
    for file in (REGRAM_JAVA / "tw/nekomimi/nekogram/NekoConfig.java",
                 ROOT / "TMessagesProj/src/main/kotlin/xyz/nextalone/nagram/NaConfig.kt"):
        try:
            text = file.read_text()
        except OSError:
            continue
        settings |= set(re.findall(r'addConfig\("(\w+)"', text))
        settings |= set(re.findall(r"val\s+(\w+)\s*[:=]", text))
        settings |= set(re.findall(r"public static ConfigItem (\w+)", text))
    resources = set()
    for strings in (ROOT / "TMessagesProj/src/main/res").glob("values*/*.xml"):
        resources |= set(re.findall(r'name="(\w+)"', strings.read_text()))
    return settings, resources


POLARITY = re.compile(r"^(disable|enable|hide|show|dont|no|always|never)")


def strip_polarity(key):
    return POLARITY.sub("", key)


def match_names(introduced, settings):
    """Split patch names into exact and inverted-polarity matches against the base."""
    exact, polarity = [], []
    by_key = {normalize(s): s for s in settings}
    loose = {}
    for key, original in by_key.items():
        loose.setdefault(strip_polarity(key), set()).add(original)
    for name in introduced:
        key = normalize(name)
        if key in by_key:
            exact.append(f"{name}≈{by_key[key]}")
        elif strip_polarity(key) in loose:
            polarity.append(f"{name}~{'/'.join(sorted(loose[strip_polarity(key)]))}")
    return sorted(exact), sorted(polarity)


def triage(source_dir, series):
    settings, resources = base_vocabulary()
    rows = []
    for name in series:
        text = (source_dir / "patches" / name).read_text()
        scores, applied, files, missing_files = [], [], set(), set()
        for path, context, added in hunks(text):
            files.add(path)
            hit = anchor_hits(path, context)
            if hit is None:
                missing_files.add(path)
            else:
                scores.append(hit)
                applied.append(applied_hits(path, added))
        if not scores:
            verdict = "file-missing"
        elif applied and min(applied) >= 0.8:
            verdict = "looks-applied"
        elif all(score == 1.0 for score in scores):
            verdict = "anchors-match"
        elif sum(scores) / len(scores) >= 0.5:
            verdict = "anchors-partial"
        else:
            verdict = "anchors-missing"
        introduced = names_in(text)
        exact, polarity = match_names(introduced, settings)
        rows.append({
            "patch": name,
            "verdict": verdict,
            "hunks": len(scores),
            "added_lines_already_present": round(sum(applied) / len(applied), 2) if applied else None,
            "files": sorted(f.split("java/")[-1] for f in files),
            "missing_files": sorted(f.split("java/")[-1] for f in missing_files),
            "same_setting_in_base": sorted(exact),
            "same_subject_inverted_polarity": sorted(polarity),
            "new_strings_already_in_base": sorted(n for n in introduced if n in resources),
        })
    return rows


if __name__ == "__main__":
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--source", type=Path, default=ROOT.parent / "inugram-md3-sliders")
    parser.add_argument("--output", type=Path, default=ROOT / "docs/inugram-triage.json")
    args = parser.parse_args()
    series = [line.strip() for line in (args.source / "series").read_text().splitlines()
              if line.strip() and not line.strip().startswith("#")]
    rows = triage(args.source, series)
    args.output.write_text(json.dumps(rows, ensure_ascii=False, indent=2) + "\n")
    counts = {}
    for row in rows:
        counts[row["verdict"]] = counts.get(row["verdict"], 0) + 1
    print(f"разобрано патчей: {len(rows)}")
    for verdict in ("looks-applied", "anchors-match", "anchors-partial", "anchors-missing", "file-missing"):
        print(f"  {verdict:16s} {counts.get(verdict, 0)}")
    already = [r for r in rows if r["same_setting_in_base"] or r["same_subject_inverted_polarity"]]
    print(f"  настройка с тем же именем уже в основе: {len([r for r in rows if r['same_setting_in_base']])}")
    print(f"  тот же предмет с обратной логикой (требует чтения): {len([r for r in rows if r['same_subject_inverted_polarity']])}")
    print(args.output)
