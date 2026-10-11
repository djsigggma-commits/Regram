"""Glyph routing regressions; physical LEDs still require a Nothing device."""
from pathlib import Path
import shutil
import subprocess
import zipfile

import pytest

ROOT = Path(__file__).resolve().parents[1]
JAVA = ROOT / 'TMessagesProj/src/main/java'
CONTROLLER = JAVA / 'app/regram/glyph/GlyphController.java'


def method(source, signature):
    start = source.index(signature)
    brace = source.index('{', start)
    depth, end = 1, brace + 1
    while depth:
        depth += (source[end] == '{') - (source[end] == '}')
        end += 1
    return source[start:end]


def test_glyph_settings_are_reachable_on_nothing():
    source = (JAVA / 'app/regram/settings/OpenExteraOtherActivity.java').read_text()
    rows = method(source, 'protected void updateRows()')
    assert 'GlyphController.getInstance().isSupported()' in rows
    assert 'glyphRow = addRow("glyph")' in rows
    assert 'presentFragment(new OpenExteraGlyphActivity())' in source


def test_phone1_registration_and_all_zone_animations():
    source = CONTROLLER.read_text()
    assert 'Common.is20111()' in source
    assert 'stripManager.register(Glyph.DEVICE_20111)' in source
    for signature in ('private void pulseStrip()', 'private void breatheStrip()'):
        animation = method(source, signature)
        for zone in 'ABCDE':
            assert f'.buildChannel{zone}()' in animation
        assert 'stripManager.animate(frame)' in animation


def test_async_connection_resumes_recording_and_call_and_shutdown_turns_off():
    source = CONTROLLER.read_text()
    assert source.count('onGlyphServiceReady();') == 2
    ready = method(source, 'private void onGlyphServiceReady()')
    assert 'handler.post(' in ready
    assert 'updateGlyphActivity();' in ready
    shutdown = method(source, 'public synchronized void shutdown()')
    assert shutdown.index('stripManager.turnOff()') < shutdown.index('stripManager.unInit()')
    assert 'stripManager.closeSession()' in shutdown
    preview = method(source, 'public void preview()')
    assert preview.index('if (glyphBusy)') < preview.index('pulseStrip()')


def test_bundled_sdk_maps_phone1_and_lights_all_15_channels(tmp_path):
    javac, java = shutil.which('javac'), shutil.which('java')
    if not javac or not java:
        pytest.skip('JDK unavailable')
    jar = tmp_path / 'glyph.jar'
    with zipfile.ZipFile(ROOT / 'TMessagesProj/libs/glyph-matrix-sdk-2.0.aar') as aar:
        jar.write_bytes(aar.read('classes.jar'))
    build = tmp_path / 'android/os/Build.java'
    build.parent.mkdir(parents=True)
    build.write_text('package android.os; public class Build { public static String MODEL = "A063"; }')
    package_manager = tmp_path / 'android/content/pm/PackageManager.java'
    package_manager.parent.mkdir(parents=True)
    package_manager.write_text('package android.content.pm; public class PackageManager { '
                               'public static class NameNotFoundException extends Exception {} }')
    main = tmp_path / 'CheckGlyph.java'
    main.write_text('''
import com.nothing.ketchum.*;
public class CheckGlyph {
    public static void main(String[] args) {
        if (!Common.is20111() || Common.is23112() || Common.is25111p())
            throw new AssertionError("Phone (1) must use strips, not matrix");
        GlyphFrame frame = new GlyphFrame.Builder(Glyph.DEVICE_20111)
            .buildChannelA().buildChannelB().buildChannelC().buildChannelD().buildChannelE()
            .buildPeriod(600).buildCycles(2).buildInterval(120).build();
        if (frame.getChannel().length != 15) throw new AssertionError("Channel count");
        for (int brightness : frame.getChannel())
            if (brightness <= 0) throw new AssertionError("Unlit Phone (1) channel");
        if (frame.getPeriod() != 600 || frame.getCycles() != 2 || frame.getInterval() != 120)
            throw new AssertionError("Animation timing");
    }
}
''')
    subprocess.run([javac, '-cp', str(jar), '-d', str(tmp_path), str(build), str(package_manager), str(main)],
                   check=True, capture_output=True, text=True)
    subprocess.run([java, '-cp', f'{tmp_path}:{jar}', 'CheckGlyph'],
                   check=True, capture_output=True, text=True)
