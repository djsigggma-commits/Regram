"""Release numbering belongs to re:gram, not the upstream Telegram base."""
import re
from pathlib import Path

ROOT = Path(__file__).resolve().parents[1]


def test_first_release_version_and_monotonic_android_code():
    properties = (ROOT / 'gradle.properties').read_text()
    gradle = (ROOT / 'TMessagesProj/build.gradle').read_text()
    version = re.search(r'^REGRAM_VERSION=(\d+)\.(\d+)(?:\.(\d+))?$', properties, re.M)
    assert version is not None
    major, minor, patch = (int(part or 0) for part in version.groups())
    assert version.group(0) == 'REGRAM_VERSION=1.0'
    assert major * 10000 + minor * 100 + patch == 10000 > 1263
    assert 'def verName = project.findProperty(\'REGRAM_VERSION\')' in gradle
    assert 'def verCode = major * 10000 + minor * 100 + patch' in gradle
    assert 'minor > 99 || patch > 99' in gradle
    assert 'def officialVer = APP_VERSION_NAME' in gradle


def test_regram_version_is_shown_and_used_for_update_checks():
    java = ROOT / 'TMessagesProj/src/main/java/app/regram'
    about = (java / 'settings/AboutHeaderCell.java').read_text()
    updater = (java / 'updater/GitHubUpdater.java').read_text()
    assert 'new StringBuilder(BuildConfig.VERSION_NAME)' in about
    assert 'version(BuildConfig.VERSION_NAME)' in updater
    assert 'matcher.group(3) == null ? 0' in updater
