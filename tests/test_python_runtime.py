import os
import re
import zipfile

import pytest

import corpus

MAGIC_BY_VERSION = {
    "3.8": 3413,
    "3.9": 3425,
    "3.10": 3439,
    "3.11": 3495,
    "3.12": 3531,
    "3.13": 3571,
}

ARCHIVE_DIR = os.environ.get(
    "EXTERALESS_PLUGIN_ARCHIVES", "/home/coral/openExtera/plugin-corpus/archives")

BUILD_GRADLE = os.path.join(corpus.REPO, "TMessagesProj", "build.gradle")


def configured_python_version():
    with open(BUILD_GRADLE, encoding="utf-8") as fh:
        text = fh.read()
    block = re.search(r"\bpython\s*\{(.*?)\n\s{8}\}", text, re.S)
    assert block, f"no python block in {BUILD_GRADLE}"
    m = re.search(r"version\s+'([\d.]+)'", block.group(1))
    assert m, f"no version line in python block of {BUILD_GRADLE}"
    return m.group(1)


def compiled_archives():
    if not os.path.isdir(ARCHIVE_DIR):
        return []
    out = []
    for fn in sorted(os.listdir(ARCHIVE_DIR)):
        path = os.path.join(ARCHIVE_DIR, fn)
        if os.path.isfile(path) and zipfile.is_zipfile(path):
            out.append(path)
    return out


def pyc_magics(path):
    found = {}
    with zipfile.ZipFile(path) as z:
        for name in z.namelist():
            if not name.endswith(".pyc"):
                continue
            head = z.read(name)[:2]
            if len(head) == 2:
                found.setdefault(int.from_bytes(head, "little"), []).append(name)
    return found


def test_configured_python_version_is_known():
    version = configured_python_version()
    assert version in MAGIC_BY_VERSION, (
        f"build.gradle asks for Python {version}, "
        f"which this test does not know a bytecode magic for")


def test_compiled_plugins_match_app_python_version():
    archives = compiled_archives()
    if not archives:
        pytest.skip(f"no plugin archives in {ARCHIVE_DIR}")
    version = configured_python_version()
    expected = MAGIC_BY_VERSION[version]
    broken = []
    for path in archives:
        magics = pyc_magics(path)
        for magic, names in magics.items():
            if magic != expected:
                other = [v for v, m in MAGIC_BY_VERSION.items() if m == magic]
                broken.append(
                    f"{os.path.basename(path)}: {len(names)} .pyc with magic {magic}"
                    f" (Python {other[0] if other else '?'}), app runs {version};"
                    f" first is {names[0]}")
    assert not broken, (
        "compiled plugins cannot be imported by the app's interpreter:\n  "
        + "\n  ".join(broken))


def test_build_python_is_configurable_without_editing_the_repo():
    with open(BUILD_GRADLE, encoding="utf-8") as fh:
        text = fh.read()
    assert "CHAQUOPY_BUILD_PYTHON" in text, (
        "build.gradle must take buildPython from local.properties or the"
        " environment so the pinned interpreter path stays out of git")
