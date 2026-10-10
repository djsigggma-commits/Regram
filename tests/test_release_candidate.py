"""Local release gating tests; does not claim Android runtime validation."""
import importlib.util
from pathlib import Path
import re
import subprocess
import tempfile
import unittest
import xml.etree.ElementTree as ET
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
        for name in ("aapt", "apksigner", "dexdump"):
            (self.tools / name).touch()
        self.apk = self.root / "candidate.apk"
        self.calls = []
        tree = ET.Element("api")
        package = ET.SubElement(tree, "package", name="androidx.media3.decoder.ffmpeg")
        for name, methods in verifier.FFMPEG_JNI.items():
            cls = ET.SubElement(package, "class", name=name)
            for name, (returns, parameters, native) in methods.items():
                method = ET.SubElement(cls, "method", {"name": name, "return": returns, "native": str(native).lower()})
                for parameter in parameters:
                    ET.SubElement(method, "parameter", type=parameter)
        self.dex_xml = tree

    def run_tool(self, argv, **kwargs):
        self.calls.append(argv)
        output = (ET.tostring(self.dex_xml, encoding="unicode") if Path(argv[0]).name == "dexdump"
                  else "package: name='app.regram.android' versionCode='1261'\n")
        return subprocess.CompletedProcess(argv, 0, output, "")

    def make_apk(self, abis=("arm64-v8a",), plugins=True):
        with zipfile.ZipFile(self.apk, "w") as archive:
            archive.writestr("classes.dex", "Landroidx/media3/decoder/ffmpeg/FfmpegLibrary;")
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

    def test_verifies_all_abis_and_invokes_signature_checker(self):
        self.make_apk(("armeabi-v7a", "arm64-v8a", "x86", "x86_64"))
        self.assertEqual(64, len(verifier.verify(self.apk, "universal", self.root, self.run_tool)))
        self.assertEqual("verify", self.calls[-1][1])
        self.assertEqual("apksigner", Path(self.calls[-1][0]).name)

    def test_rejects_missing_av1_native_method_before_signing(self):
        self.make_apk()
        cls = self.dex_xml.find("package/class[@name='FfmpegLibrary']")
        cls.remove(cls.find("method[@name='ffmpegGetAv1DecoderName']"))
        with self.assertRaisesRegex(ValueError, "FfmpegLibrary.ffmpegGetAv1DecoderName"):
            verifier.verify(self.apk, "arm64-v8a", self.root, self.run_tool)
        self.assertFalse(any(Path(call[0]).name == "apksigner" for call in self.calls))

    def test_rejects_wrong_jni_signature_and_missing_dexdump(self):
        self.make_apk()
        method = self.dex_xml.find("package/class[@name='FfmpegAudioDecoder']/method[@name='ffmpegReset']")
        method.set("return", "int")
        with self.assertRaisesRegex(ValueError, "FfmpegAudioDecoder.ffmpegReset"):
            verifier.verify(self.apk, "arm64-v8a", self.root, self.run_tool)
        (self.tools / "dexdump").unlink()
        with self.assertRaisesRegex(ValueError, "dexdump"):
            verifier.verify(self.apk, "arm64-v8a", self.root, self.run_tool)

    def test_gate_covers_current_native_registration_tables(self):
        source = (ROOT / 'TMessagesProj/jni/exoplayer/ffmpeg_jni.cc').read_text()
        primitives = {'void': 'V', 'boolean': 'Z', 'byte': 'B', 'short': 'S',
                      'char': 'C', 'int': 'I', 'long': 'J', 'float': 'F', 'double': 'D'}
        def descriptor(name):
            if name.endswith('[]'):
                return '[' + descriptor(name[:-2])
            return primitives.get(name, 'L' + name.replace('.', '/') + ';')
        def signature(spec):
            returns, params, _ = spec
            return '(' + ''.join(map(descriptor, params)) + ')' + descriptor(returns)
        for cls in verifier.FFMPEG_JNI:
            table = source.split('k' + cls + 'Methods[] = {', 1)[1].split('};', 1)[0]
            registered = {name: ''.join(re.findall(r'"([^\"]*)"', literals))
                          for name, literals in re.findall(r'\{\s*"([^\"]+)"\s*,\s*((?:"[^\"]*"\s*)+),', table)}
            expected = {name: signature(spec) for name, spec in verifier.FFMPEG_JNI[cls].items() if spec[2]}
            self.assertEqual(expected, registered)
        callback = re.search(r'GetMethodID\(\s*clazz,\s*"growOutputBuffer",\s*((?:"[^\"]*"\s*)+)\)', source)
        self.assertIsNotNone(callback)
        self.assertEqual(signature(verifier.FFMPEG_JNI['FfmpegAudioDecoder']['growOutputBuffer']),
                         ''.join(re.findall(r'"([^\"]*)"', callback.group(1))))

    def test_rejects_dex_references_without_class_declarations(self):
        self.make_apk()
        self.dex_xml.find("package").clear()
        with self.assertRaisesRegex(ValueError, "FFmpeg JNI contract mismatch"):
            verifier.verify(self.apk, "arm64-v8a", self.root, self.run_tool)

    def test_missing_sdk_tools_fail_closed(self):
        self.make_apk()
        (self.tools / "apksigner").unlink()
        with self.assertRaisesRegex(ValueError, "build-tools"):
            verifier.verify(self.apk, "arm64-v8a", self.root, self.run_tool)


if __name__ == "__main__":
    unittest.main()
