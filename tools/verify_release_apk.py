#!/usr/bin/env python3
"""Check a release candidate's Android identity, ABI, contents and APK signature."""
import argparse
import hashlib
import os
from pathlib import Path
import re
import subprocess
import tempfile
import xml.etree.ElementTree as ET
import zipfile

PACKAGE = "app.regram.android"
ABIS = {"armeabi-v7a", "arm64-v8a", "x86", "x86_64"}

# RegisterNatives and GetMethodID contracts in jni/exoplayer/ffmpeg_jni.cc.
BUFFER = "androidx.media3.decoder.SimpleDecoderOutputBuffer"
FFMPEG_JNI = {
    "FfmpegLibrary": {
        "ffmpegGetVersion": ("java.lang.String", (), True),
        "ffmpegGetInputBufferPaddingSize": ("int", (), True),
        "ffmpegHasDecoder": ("boolean", ("java.lang.String",), True),
        "ffmpegGetAv1DecoderName": ("java.lang.String", (), True),
    },
    "FfmpegAudioDecoder": {
        "ffmpegInitialize": ("long", ("java.lang.String", "byte[]", "boolean", "int", "int"), True),
        "ffmpegDecode": ("int", ("long", "java.nio.ByteBuffer", "int", BUFFER, "java.nio.ByteBuffer", "int"), True),
        "ffmpegGetChannelCount": ("int", ("long",), True),
        "ffmpegGetSampleRate": ("int", ("long",), True),
        "ffmpegReset": ("long", ("long", "byte[]"), True),
        "ffmpegRelease": ("void", ("long",), True),
        "growOutputBuffer": ("java.nio.ByteBuffer", (BUFFER, "int"), False),
    },
}


def verify_ffmpeg_jni(apk, tools, run=subprocess.run):
    """Inspect actual post-R8 DEX declarations, not source or method references."""
    dexdump = tools / "dexdump"
    if not dexdump.is_file():
        raise ValueError("Android SDK dexdump is required for release JNI verification")
    found = {}
    with zipfile.ZipFile(apk) as archive, tempfile.TemporaryDirectory(prefix="regram-jni-") as work:
        for name in archive.namelist():
            if not re.fullmatch(r"classes(?:[0-9]+)?\.dex", name):
                continue
            data = archive.read(name)
            if not any(("Landroidx/media3/decoder/ffmpeg/" + cls + ";").encode() in data
                       for cls in FFMPEG_JNI):
                continue
            dex = Path(work) / name
            dex.write_bytes(data)
            output = run([str(dexdump), "-l", "xml", str(dex)],
                         capture_output=True, text=True, check=True).stdout
            try:
                tree = ET.fromstring(output)
            except ET.ParseError as error:
                raise ValueError("Invalid dexdump XML during JNI verification") from error
            for package in tree.findall("package"):
                if package.get("name") != "androidx.media3.decoder.ffmpeg":
                    continue
                for cls in package.findall("class"):
                    if cls.get("name") in FFMPEG_JNI:
                        found[cls.get("name")] = {
                            method.get("name"): (method.get("return"),
                                                 tuple(p.get("type") for p in method.findall("parameter")),
                                                 method.get("native") == "true")
                            for method in cls.findall("method")
                        }
    missing = [f"{cls}.{method}" for cls, methods in FFMPEG_JNI.items()
               for method, signature in methods.items()
               if found.get(cls, {}).get(method) != signature]
    if missing:
        raise ValueError("FFmpeg JNI contract mismatch in release DEX: " + ", ".join(missing))


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
    verify_ffmpeg_jni(apk, tools, run)
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
