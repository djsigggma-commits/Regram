"""Execute production APK selection/length checks on a host JVM."""
from pathlib import Path
import shutil
import subprocess

import pytest

ROOT = Path(__file__).resolve().parents[1]
JAVA = ROOT / "TMessagesProj/src/main/java/app/regram/updater"


def test_artifact_architectures_and_download_integrity(tmp_path):
    javac, java = shutil.which("javac"), shutil.which("java")
    if not javac or not java:
        pytest.skip("host JDK required")
    harness = tmp_path / "ArtifactTest.java"
    harness.write_text('''
import app.regram.updater.UpdateArtifact;
import java.io.IOException;
public class ArtifactTest {
    static void select(int expected, String[] names, String... abis) {
        int actual = UpdateArtifact.select(names, abis);
        if (actual != expected) throw new AssertionError(actual + " != " + expected);
    }
    static void reject(long done, long content, long asset) throws Exception {
        try {
            UpdateArtifact.verifyLength(done, content, asset);
            throw new AssertionError("accepted incomplete APK");
        } catch (IOException expected) {}
    }
    public static void main(String[] args) throws Exception {
        select(-1, new String[]{"regram-arm64-v8a.apk"}, "armeabi-v7a");
        select(-1, new String[]{"regram-x86_64.apk"}, "x86");
        select(-1, new String[]{"regram-armeabi-v7a.apk"}, "arm64-v8a");
        select(1, new String[]{"regram-arm64-v8a.apk", "regram-universal.apk"}, "x86");
        select(1, new String[]{"regram-universal.apk", "regram-ARM64-V8A.APK"}, "arm64-v8a");
        select(1, new String[]{"regram-armeabi-v7a.apk", "regram-arm64-v8a.apk"}, "arm64-v8a", "armeabi-v7a");
        select(0, new String[]{"regram-armeabi-v7a.apk"}, "arm64-v8a", "armeabi-v7a");
        select(2, new String[]{null, "checksum.apk.sha256", "regram.apk"}, "x86_64");
        select(-1, new String[]{}, "arm64-v8a");
        reject(0, -1, 0);
        reject(9, 10, 10);
        reject(9, -1, 10);
        reject(11, -1, 10);
        reject(10, 0, 10);
        UpdateArtifact.verifyLength(10, 10, 10);
        UpdateArtifact.verifyLength(10, -1, 10);
        UpdateArtifact.verifyLength(10, 10, 0);
        UpdateArtifact.verifyLength(10, -1, 0);
    }
}
''')
    compiled = subprocess.run(
        [javac, "-d", str(tmp_path), str(JAVA / "UpdateArtifact.java"), str(harness)],
        capture_output=True, text=True,
    )
    assert compiled.returncode == 0, compiled.stderr
    run = subprocess.run([java, "-cp", str(tmp_path), "ArtifactTest"], capture_output=True, text=True, timeout=20)
    assert run.returncode == 0, run.stdout + run.stderr


def test_updater_wires_selection_and_verifies_before_install():
    code = (JAVA / "GitHubUpdater.java").read_text()
    assert "UpdateArtifact.select(names, Build.SUPPORTED_ABIS)" in code
    assert code.index("UpdateArtifact.verifyLength(done, contentLength, release.apkSize)") < code.index("ok = true;")
