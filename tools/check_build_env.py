#!/usr/bin/env python3
"""Report build prerequisites without downloading dependencies or printing credentials."""
import argparse
import base64
import configparser
import json
import os
from pathlib import Path
import re
import shutil
import subprocess
import sys

ROOT = Path(__file__).resolve().parents[1]
PACKAGE = "app.regram.android"


def properties(text):
    return dict(line.split("=", 1) for raw in text.splitlines()
                if (line := raw.strip()) and not line.startswith(("#", "!")) and "=" in line)


def parse_version(text):
    return tuple(int(part) for part in re.findall(r"\d+", text)[:3]) or (0,)


def version_text(version):
    return ".".join(map(str, version))


def gradle_value(root, pattern, default):
    """First capture group of `pattern` in the app Gradle file — pinned versions live there."""
    try:
        match = re.search(pattern, (root / "TMessagesProj" / "build.gradle").read_text())
    except OSError:
        return default
    return match.group(1) if match else default


def cmake_versions(sdk):
    versions = []
    for properties_file in sorted((sdk / "cmake").glob("*/source.properties")):
        match = re.search(r"Pkg\.Revision\s*=\s*([0-9.]+)", properties_file.read_text())
        if match:
            versions.append(parse_version(match.group(1)))
    return versions


def python_minor(interpreter):
    """Major.minor of a build interpreter, or None when it is missing or unusable."""
    if not interpreter:
        return None
    try:
        result = subprocess.run(
            [str(interpreter), "-c", "import sys; print('%d.%d' % sys.version_info[:2])"],
            capture_output=True, text=True, timeout=20,
        )
    except (OSError, subprocess.SubprocessError):
        return None
    return result.stdout.strip() or None


def check(root=ROOT, env=None):
    env = os.environ if env is None else env
    errors = []
    config = {}
    local = root / "local.properties"
    try:
        if env.get("LOCAL_PROPERTIES"):
            config = properties(base64.b64decode(env["LOCAL_PROPERTIES"], validate=True).decode())
        elif local.exists():
            config = properties(local.read_text())
    except (ValueError, UnicodeError):
        errors.append("LOCAL_PROPERTIES must be base64-encoded UTF-8 properties.")
    if os.name == "posix" and ":" in str(root.resolve()):
        errors.append(
            "Path contains ':'. Gradle cannot load its wrapper jar here, and Chaquopy refuses to "
            "create its Python venv ('contains the PATH separator'). Build from a copy at a "
            "colon-free path with tools/build.sh."
        )
    if not shutil.which("java"):
        errors.append("Java is missing. Install a JDK compatible with the pinned Gradle/AGP versions (Java target: 21).")
    app_id = config.get("TELEGRAM_APP_ID") or env.get("TELEGRAM_APP_ID", "")
    app_hash = config.get("TELEGRAM_APP_HASH") or env.get("TELEGRAM_APP_HASH", "")
    if not re.fullmatch(r"[1-9][0-9]*", app_id):
        errors.append("Set your TELEGRAM_APP_ID in local.properties or the environment.")
    if not re.fullmatch(r"[a-fA-F0-9]{32}", app_hash):
        errors.append("Set your TELEGRAM_APP_HASH in local.properties or the environment.")
    # Chaquopy discovers the interpreter itself when nothing is configured, and the plugin
    # requires the same minor version the app declares.
    want_python = gradle_value(root, r"version '([0-9.]+)'", "3.11")
    interpreter = config.get("CHAQUOPY_BUILD_PYTHON") or env.get("CHAQUOPY_BUILD_PYTHON") or shutil.which("python3")
    found_python = python_minor(interpreter)
    if found_python != want_python:
        errors.append(
            f"Chaquopy needs build Python {want_python}, got {found_python or 'nothing'}. "
            "Set CHAQUOPY_BUILD_PYTHON (see README, 'Подготовка сборки')."
        )
    sdk = config.get("sdk.dir") or env.get("ANDROID_HOME") or env.get("ANDROID_SDK_ROOT")
    if not sdk:
        errors.append("Set sdk.dir in local.properties or ANDROID_HOME.")
    else:
        sdk = Path(sdk)
        for relative in ("build-tools/36.0.0", "ndk/27.2.12479018"):
            if not (sdk / relative).is_dir():
                errors.append(f"Missing Android SDK component: {relative}")
        if not any((sdk / "platforms" / version).is_dir() for version in ("android-37", "android-37.0")):
            errors.append("Missing Android SDK platform 37.")
        if env.get("NATIVE_TARGET") != "SKIP":
            want_cmake = parse_version(gradle_value(root, r"version = '([0-9.]+)\+'", "3.31.6"))
            installed = cmake_versions(sdk)
            if not any(version >= want_cmake for version in installed):
                errors.append(
                    f"No CMake >= {version_text(want_cmake)} under {sdk / 'cmake'} "
                    f"(found: {', '.join(sorted(map(version_text, installed))) or 'none'}). "
                    f"Install it with sdkmanager --install 'cmake;{version_text(want_cmake)}'."
                )
    firebase = root / "TMessagesProj/google-services.json"
    placeholder = (root / "TMessagesProj" / "src" / "debug" / "google-services.json").exists()
    try:
        clients = json.loads(firebase.read_text()).get("client", [])
        if not any(c.get("client_info", {}).get("android_client_info", {}).get("package_name") == PACKAGE for c in clients):
            errors.append(f"Firebase config must contain a client for {PACKAGE}.")
    except (OSError, ValueError):
        if not placeholder:
            errors.append(f"Provide your TMessagesProj/google-services.json for {PACKAGE}, "
                          "or tools/make_placeholder_google_services.py for a compile-only check.")
    if env.get("NATIVE_TARGET") != "SKIP":
        modules = configparser.ConfigParser()
        modules.read(root / ".gitmodules")
        for section in modules.sections():
            path = modules[section].get("path")
            if path and (not (root / path).is_dir() or not any((root / path).iterdir())):
                errors.append(f"Missing native submodule contents: {path} (restore with tools/restore_submodules.sh)")
    return errors


def check_release(root=ROOT, env=None):
    """Extra fail-closed checks for a signed candidate, never prints secret values."""
    env = os.environ if env is None else env
    errors = []
    try:
        config = (properties(base64.b64decode(env["LOCAL_PROPERTIES"], validate=True).decode())
                  if env.get("LOCAL_PROPERTIES") else
                  properties((root / "local.properties").read_text())
                  if (root / "local.properties").exists() else {})
    except (ValueError, UnicodeError):
        return ["LOCAL_PROPERTIES must be base64-encoded UTF-8 properties."]
    if env.get("REGRAM_COMPILE_CHECK") == "1":
        errors.append("REGRAM_COMPILE_CHECK is forbidden for release builds.")
    if (config.get("TELEGRAM_APP_ID") or env.get("TELEGRAM_APP_ID")) == "9999999" or (
            config.get("TELEGRAM_APP_HASH") or env.get("TELEGRAM_APP_HASH")) == "0" * 32:
        errors.append("Compile-only Telegram credentials are forbidden for release builds.")
    if not (root / "TMessagesProj/release.keystore").is_file():
        errors.append("Provide TMessagesProj/release.keystore (your own signing key).")
    for name in ("KEYSTORE_PASS", "ALIAS_NAME", "ALIAS_PASS"):
        if not (config.get(name) or env.get(name) or "").strip():
            errors.append(f"Set {name} in local.properties or the environment.")
    firebase = root / "TMessagesProj/google-services.json"
    try:
        payload = json.loads(firebase.read_text())
        client = next((c for c in payload.get("client", [])
                       if c.get("client_info", {}).get("android_client_info", {}).get("package_name") == PACKAGE), None)
        if (payload.get("project_info", {}).get("project_id") == "regram-compile-check" or
                not client or not client.get("client_info", {}).get("mobilesdk_app_id") or
                "000000000000" in client["client_info"]["mobilesdk_app_id"] or
                not any(key.get("current_key") and "PLACEHOLDER" not in key["current_key"]
                        for key in client.get("api_key", []))):
            errors.append(f"Provide a non-placeholder Firebase client for {PACKAGE} in TMessagesProj/google-services.json.")
    except (OSError, ValueError, TypeError, AttributeError):
        errors.append(f"Provide valid TMessagesProj/google-services.json for {PACKAGE} (debug placeholder is not enough).")
    return errors


def notes(root=ROOT, env=None):
    """What a successful build still will not do. Not blockers, so CI stays usable."""
    env = os.environ if env is None else env
    result = []
    if ((root / "TMessagesProj" / "src" / "debug" / "google-services.json").exists()
            and not (root / "TMessagesProj" / "google-services.json").exists()):
        result.append(
            "Firebase config is the src/debug placeholder from "
            "tools/make_placeholder_google_services.py: enough to compile, no push and no "
            "Crashlytics. Debug-only — a release build still needs your own "
            "TMessagesProj/google-services.json."
        )
    if not (root / "local.properties").exists() and not (env.get("TELEGRAM_APP_ID") or env.get("LOCAL_PROPERTIES")):
        result.append("Credentials come from the environment, not local.properties.")
    return result


if __name__ == "__main__":
    parser = argparse.ArgumentParser(description="Check re:gram build prerequisites without printing credentials")
    parser.add_argument("--release", action="store_true", help="require real signing and Firebase configuration")
    args = parser.parse_args()
    problems = check() + (check_release() if args.release else [])
    for problem in problems:
        print(f"BLOCKED: {problem}")
    for note in notes():
        print(f"NOTE: {note}")
    print("Gradle/plugin/Maven availability and Android runtime behavior are not checked here.")
    if not problems:
        print("Local prerequisites found; compilation and device tests are still required.")
    sys.exit(bool(problems))
