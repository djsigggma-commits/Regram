"""Telegram-based release numbering with a historical pre-migration marker."""
from pathlib import Path
import shutil
import subprocess

import pytest

ROOT = Path(__file__).resolve().parents[1]
JAVA = ROOT / 'TMessagesProj/src/main/java/app/regram'


def properties():
    return dict(line.split('=', 1) for line in (ROOT / 'gradle.properties').read_text().splitlines()
                if '=' in line and not line.startswith('#'))


def test_telegram_release_version_and_monotonic_android_code():
    props = properties()
    gradle = (ROOT / 'TMessagesProj/build.gradle').read_text()
    assert props['REGRAM_VERSION'] == '13.1.1.1'
    assert props['APP_VERSION_NAME'] == '13.1.1'
    assert props['REGRAM_LEGACY_VERSION'] == '1.4.8.9'
    parts = [int(part) for part in props['REGRAM_VERSION'].split('.')]
    major, minor, patch, revision = (parts + [0] * 4)[:4]
    code = major * 1000000 + minor * 10000 + patch * 100 + revision
    assert code == 13010101
    assert code > 13010100  # Previous shipped release.
    assert code > 1040809  # Last shipped version in the old scheme.
    assert "def verName = project.findProperty('REGRAM_VERSION')" in gradle
    assert 'def verCode = major * 1000000 + minor * 10000 + patch * 100 + revision' in gradle
    assert 'minor > 99 || patch > 99 || revision > 99' in gradle
    assert 2099 * 1000000 + 99 * 10000 + 99 * 100 + 99 <= 2100000000
    assert 'def officialVer = APP_VERSION_NAME' in gradle
    assert "verName != officialVer && !verName.startsWith(officialVer + '.')" in gradle
    assert "buildConfigField 'String', 'REGRAM_LEGACY_VERSION'" in gradle
    assert 'versionName verName' in gradle
    assert 'versionCode verCode' in gradle


def test_telegram_version_is_used_for_updates_not_historical_marker():
    about = (JAVA / 'settings/AboutHeaderCell.java').read_text()
    updater = (JAVA / 'updater/GitHubUpdater.java').read_text()
    assert 'return BuildConfig.VERSION_NAME;' in about
    assert 'BuildConfig.OFFICIAL_VERSION' not in about
    assert 'BuildConfig.REGRAM_LEGACY_VERSION' not in about
    assert 'BuildVars.BUILD_COMMIT_ID' not in about
    assert 'info.versionCode' not in about
    assert 'private static int[] version(' not in updater
    assert 'ReleaseVersion.compare(BuildConfig.VERSION_NAME' in updater
    assert 'REGRAM_LEGACY_VERSION' not in updater


def test_exact_about_and_updater_version_label(tmp_path):
    javac, java = shutil.which('javac'), shutil.which('java')
    if not javac or not java:
        pytest.skip('JDK required for executable version label check')
    about = (JAVA / 'settings/AboutHeaderCell.java').read_text()
    # Compile the actual method independently of the Android view containing it.
    method = about[about.index('    public static String buildVersionString()'):].rsplit('}', 1)[0]
    props = properties()
    harness = tmp_path / 'VersionLabelCheck.java'
    harness.write_text('''
class BuildConfig {
    static final String VERSION_NAME = "%s";
    static final String OFFICIAL_VERSION = "%s";
    static final String REGRAM_LEGACY_VERSION = "%s";
}
class BuildVars { static String BUILD_COMMIT_ID = ""; }
class TextUtils { static boolean isEmpty(String s) { return s == null || s.isEmpty(); } }
public class VersionLabelCheck {
%s
    public static void main(String[] args) {
        String expected = "13.1.1.1";
        if (!expected.equals(buildVersionString())) throw new AssertionError(buildVersionString());
        BuildVars.BUILD_COMMIT_ID = "c83765c";
        if (!expected.equals(buildVersionString())) throw new AssertionError();
        BuildVars.BUILD_COMMIT_ID = null;
        if (!expected.equals(buildVersionString())) throw new AssertionError();
    }
}
''' % (props['REGRAM_VERSION'], props['APP_VERSION_NAME'], props['REGRAM_LEGACY_VERSION'], method))
    subprocess.run([javac, '-d', str(tmp_path), str(harness)], check=True, capture_output=True, text=True)
    subprocess.run([java, '-cp', str(tmp_path), 'VersionLabelCheck'],
                   check=True, capture_output=True, text=True, timeout=30)
    sheet = (JAVA / 'updater/UpdaterSheet.java').read_text()
    assert 'version.setText(AboutHeaderCell.buildVersionString())' in sheet
