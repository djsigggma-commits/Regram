"""Release numbering belongs to re:gram, not the upstream Telegram base."""
import re
from pathlib import Path

ROOT = Path(__file__).resolve().parents[1]


def test_first_release_version_and_monotonic_android_code():
    properties = (ROOT / 'gradle.properties').read_text()
    gradle = (ROOT / 'TMessagesProj/build.gradle').read_text()
    version = re.search(r'^REGRAM_VERSION=(\d+)\.(\d+)(?:\.(\d+))?(?:\.(\d+))?$', properties, re.M)
    assert version is not None
    major, minor, patch, revision = (int(part or 0) for part in version.groups())
    # Four-part numbering must upgrade all shipped legacy releases, including 1.1 (10100).
    assert major * 1000000 + minor * 10000 + patch * 100 + revision > 10100
    assert 'def verName = project.findProperty(\'REGRAM_VERSION\')' in gradle
    assert 'def verCode = major * 1000000 + minor * 10000 + patch * 100 + revision' in gradle
    assert 'minor > 99 || patch > 99 || revision > 99' in gradle
    assert 2099 * 1000000 + 99 * 10000 + 99 * 100 + 99 <= 2100000000
    assert 'def officialVer = APP_VERSION_NAME' in gradle


def test_regram_version_is_shown_and_used_for_update_checks():
    java = ROOT / 'TMessagesProj/src/main/java/app/regram'
    about = (java / 'settings/AboutHeaderCell.java').read_text()
    updater = (java / 'updater/GitHubUpdater.java').read_text()
    assert 'new StringBuilder(BuildConfig.VERSION_NAME)' in about
    assert 'private static int[] version(' not in updater
    assert 'ReleaseVersion.compare(BuildConfig.VERSION_NAME' in updater
