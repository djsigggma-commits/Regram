"""Local release gating tests; does not claim Android runtime validation."""
import importlib.util
from pathlib import Path
import subprocess
import tempfile
import unittest
import zipfile

ROOT = Path(__file__).resolve().parents[1]
spec = importlib.util.spec_from_file_location("verify_release_apk", ROOT / "tools/verify_release_apk.py")
verifier = importlib.util.module_from_spec(spec)
spec.loader.exec_module(verifier)


class ReleaseCandidateTests(unittest.TestCase):
    def setUp(self):
        self.temp = tempfile.TemporaryDirectory()
        self.addCleanup(self.temp.cleanup)
        self.root = Path(self.temp.name)
        self.tools = self.root / "build-tools/36.0.0"
        self.tools.mkdir(parents=True)
        for name in ("aapt", "apksigner"):
            (self.tools / name).touch()
        self.apk = self.root / "candidate.apk"
        self.calls = []

    def run_tool(self, argv, **kwargs):
        self.calls.append(argv)
        return subprocess.CompletedProcess(argv, 0, "package: name='app.regram.android' versionCode='1261'\n", "")

    def make_apk(self, abis=("arm64-v8a",), plugins=True):
        with zipfile.ZipFile(self.apk, "w") as archive:
            for abi in abis:
                archive.writestr(f"lib/{abi}/libtmessages.49.so", "example")
            if plugins:
                archive.writestr("assets/chaquopy/bootstrap-native/bootstrap.so", "example")

    def test_rejects_wrong_abi_and_missing_engine(self):
        self.make_apk()
        with self.assertRaisesRegex(ValueError, "Native library ABIs"):
            verifier.verify(self.apk, "x86_64", self.root, self.run_tool)
        self.make_apk(plugins=False)
        with self.assertRaisesRegex(ValueError, "Python plugin engine"):
            verifier.verify(self.apk, "arm64-v8a", self.root, self.run_tool)

    def test_rejects_debuggable_or_wrong_package(self):
        self.make_apk()
        def fake_run(argv, **kwargs):
            return subprocess.CompletedProcess(argv, 0, "package: name='com.exteraless.app'\n", "")
        with self.assertRaisesRegex(ValueError, "APK package"):
            verifier.verify(self.apk, "arm64-v8a", self.root, fake_run)
        def debug_run(argv, **kwargs):
            return subprocess.CompletedProcess(argv, 0,
                                               "package: name='app.regram.android'\napplication-debuggable\n", "")
        with self.assertRaisesRegex(ValueError, "debuggable"):
            verifier.verify(self.apk, "arm64-v8a", self.root, debug_run)

    def test_verifies_both_abis_and_invokes_signature_checker(self):
        self.make_apk(("arm64-v8a", "x86_64"))
        self.assertEqual(64, len(verifier.verify(self.apk, "universal", self.root, self.run_tool)))
        self.assertEqual("verify", self.calls[-1][1])
        self.assertEqual("apksigner", Path(self.calls[-1][0]).name)

    def test_missing_sdk_tools_fail_closed(self):
        self.make_apk()
        (self.tools / "apksigner").unlink()
        with self.assertRaisesRegex(ValueError, "build-tools"):
            verifier.verify(self.apk, "arm64-v8a", self.root, self.run_tool)


if __name__ == "__main__":
    unittest.main()
