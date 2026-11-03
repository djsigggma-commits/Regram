"""Restored sources must not reuse native objects from a different source tree."""
import importlib.util
import os
from pathlib import Path

ROOT = Path(__file__).resolve().parents[1]
spec = importlib.util.spec_from_file_location('native_source_fingerprint', ROOT / 'tools/native_source_fingerprint.py')
fingerprint_module = importlib.util.module_from_spec(spec)
spec.loader.exec_module(fingerprint_module)


def test_fingerprint_detects_same_size_source_with_old_timestamp(tmp_path):
    source = tmp_path / 'wrapper.cpp'
    source.write_text('old method')
    original = fingerprint_module.fingerprint(tmp_path)
    stamp = source.stat().st_mtime_ns
    source.write_text('new method')
    os.utime(source, ns=(stamp, stamp))
    assert fingerprint_module.fingerprint(tmp_path) != original


def test_fingerprint_ignores_generated_files_but_tracks_headers(tmp_path):
    header = tmp_path / 'header.h'
    header.write_text('int method();')
    original = fingerprint_module.fingerprint(tmp_path)
    for directory in ('.git', '.cxx', 'build'):
        output = tmp_path / directory / 'object.o'
        output.parent.mkdir()
        output.write_bytes(b'generated')
    assert fingerprint_module.fingerprint(tmp_path) == original
    os.utime(header, (1, 1))
    assert fingerprint_module.fingerprint(tmp_path) == original
    header.rename(tmp_path / 'renamed.h')
    assert fingerprint_module.fingerprint(tmp_path) != original


def test_build_invalidates_unknown_or_changed_native_cache():
    code = (ROOT / 'tools/build.sh').read_text()
    assert 'rsync -a --checksum --delete' in code
    assert 'NATIVE_DIGEST="$(python3' in code
    assert 'if [[ "$NATIVE_DIGEST" != "$PREVIOUS_NATIVE_DIGEST" ]]' in code
    assert '"$BUILD_DIR/TMessagesProj/.cxx"' in code
    assert code.index('Native sources changed') < code.index('exec "$GRADLE"')


def test_startup_preserves_original_native_exception():
    code = (ROOT / 'TMessagesProj/src/main/java/org/telegram/messenger/ApplicationLoader.java').read_text()
    assert 'NativeLoader.log, error)' in code
