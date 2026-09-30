#!/usr/bin/env python3
"""Write a syntactically valid, structurally fake google-services.json.

Firebase is not initialised by this config: it exists only so that the
google-services Gradle plugin can generate its resource values and Java/Kotlin
compilation can be checked offline. Every identifier is a placeholder, so no
upstream project number or API key is reused. For a build that actually runs,
download the real file for app.regram.android from the Firebase console into
TMessagesProj/google-services.json (the debug placeholder is used only for
compile checks and is ignored by release builds).
"""
import json
from pathlib import Path

PACKAGE = "app.regram.android"
ROOT = Path(__file__).resolve().parents[1]
OUT = ROOT / "TMessagesProj" / "src" / "debug" / "google-services.json"

PLACEHOLDER = {
    "project_info": {
        "project_number": "000000000000",
        "project_id": "regram-compile-check",
        "storage_bucket": "regram-compile-check.appspot.com",
    },
    "client": [
        {
            "client_info": {
                "mobilesdk_app_id": "1:000000000000:android:0000000000000000000000",
                "android_client_info": {"package_name": PACKAGE},
            },
            "oauth_client": [],
            "api_key": [{"current_key": "AIzaSyPLACEHOLDERPLACEHOLDERPLACEHOLDER0"}],
            "services": {"crashlytics": True},
        }
    ],
    "configuration_version": "1",
}

def create_placeholder(root=ROOT):
    """Never shadow a real Firebase config with a higher-priority debug placeholder."""
    if (root / "TMessagesProj" / "google-services.json").exists():
        raise FileExistsError("Real Firebase config already exists; refusing debug placeholder")
    out = root / "TMessagesProj" / "src" / "debug" / "google-services.json"
    if out.exists():
        raise FileExistsError("Debug Firebase config already exists; refusing to overwrite it")
    out.parent.mkdir(parents=True, exist_ok=True)
    out.write_text(json.dumps(PLACEHOLDER, indent=2) + "\n")
    return out


if __name__ == "__main__":
    try:
        print(f"placeholder written: {create_placeholder()}")
    except FileExistsError as error:
        raise SystemExit(str(error))
    print("compile-check only — replace with your own Firebase config before running the app")
