#!/usr/bin/env python3
"""Check a release candidate's Android identity, ABI, contents and APK signature."""
import argparse
import hashlib
import os
from pathlib import Path
import re
import subprocess
import zipfile

PACKAGE = "app.regram.android"
ABIS = {"arm64-v8a", "x86_64"}


def verify(apk, abi, sdk=None, run=subprocess.run):
    apk = Path(apk)
    sdk = Path(sdk or os.environ.get("ANDROID_HOME") or os.environ.get("ANDROID_SDK_ROOT") or "")
    tools = sdk / "build-tools/36.0.0"
    if not apk.is_file() or not (tools / "aapt").is_file() or not (tools / "apksigner").is_file():
        raise ValueError("APK or Android SDK build-tools/36.0.0 not found")
    if abi not in ABIS | {"universal"}:
        raise ValueError("Unknown target ABI")
    with zipfile.ZipFile(apk) as archive:
        contents = set(archive.namelist())
        actual_abis = {match.group(1) for name in contents
                       if (match := re.fullmatch(r"lib/([^/]+)/libtmessages\.49\.so", name))}
        expected = ABIS if abi == "universal" else {abi}
        if actual_abis != expected:
            raise ValueError(f"Native library ABIs: expected {sorted(expected)}, got {sorted(actual_abis)}")
        if not any(name.startswith("assets/chaquopy/") for name in contents):
            raise ValueError("Python plugin engine is missing from APK")
    badging = run([str(tools / "aapt"), "dump", "badging", str(apk)],
                  capture_output=True, text=True, check=True).stdout
    if not re.search(r"^package: name='app\.regram\.android'\s", badging, re.MULTILINE):
        raise ValueError(f"APK package must be {PACKAGE}")
    if "application-debuggable" in badging:
        raise ValueError("Release APK is debuggable")
    run([str(tools / "apksigner"), "verify", "--verbose", str(apk)],
        capture_output=True, text=True, check=True)
    return hashlib.sha256(apk.read_bytes()).hexdigest()


if __name__ == "__main__":
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--abi", choices=sorted(ABIS | {"universal"}), required=True)
    parser.add_argument("apk", help="exactly one release APK")
    args = parser.parse_args()
    try:
        digest = verify(args.apk, args.abi)
    except (ValueError, OSError, zipfile.BadZipFile, subprocess.CalledProcessError) as error:
        parser.exit(1, f"Release candidate verification failed: {error}\n")
    print(f"Verified {args.apk} ({args.abi}); SHA-256: {digest}")
