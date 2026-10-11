"""Bounded work, unchanged wave geometry and four-part release ordering; not device benchmarks."""
from pathlib import Path
import shutil
import subprocess

import pytest

ROOT = Path(__file__).resolve().parents[1]
JAVA = ROOT / 'TMessagesProj/src/main/java'


def source(path):
    return (JAVA / path).read_text()


def test_icons_have_bounded_prewarm_memory_and_stale_decode_guards():
    code = source('app/regram/icons/IconPackManager.java')
    assert 'Math.max(2048, Math.min(8192, maxMemory / 16))' in code
    assert '(value.getAllocationByteCount() + 1023) / 1024' in code
    assert 'MAX_PREWARM_ICONS = 64' in code
    assert 'considered++ >= MAX_PREWARM_ICONS' in code
    assert 'ICON_IO.execute(this::reloadInternal)' in code
    warm = code.split('public synchronized void launchPrewarm()', 1)[1].split('// ---- Иконка уведомлений', 1)[0]
    assert 'ICON_IO.execute(' in warm
    assert 'Utilities.globalQueue' not in warm
    assert 'Thread.sleep(' not in warm
    assert 'generation == packGeneration' in warm
    assert 'existing == null || existing.isRecycled()' in warm
    assert 'bitmap.recycle()' in warm
    assert 'if (generation != packGeneration)' in code
    assert 'if (generation == packGeneration) missing.add(resId)' in code


def test_player_reuses_ripples_and_stops_hidden_frame_scheduling():
    sheet = source('app/regram/player/PlayerSheet.java')
    update = sheet.split('private void applyColors(PlayerColors c)', 1)[1].split('private void applySystemBars', 1)[0]
    assert 'createSelectorDrawable' not in update
    assert update.count('Theme.setSelectorDrawableColor(') == 2
    assert 'if (colors == target) return' in sheet
    assert 'if (headerRippleColor != ripple)' in update
    for name in ('WavySeekBar', 'MorphButton'):
        code = source(f'app/regram/player/{name}.java')
        assert 'isShown() && getWindowVisibility() == VISIBLE' in code
        assert 'protected void onWindowVisibilityChanged(int visibility)' in code
        assert 'protected void onVisibilityChanged(' in code
    seek = source('app/regram/player/WavySeekBar.java')
    assert 'if (amplitude == 0f)' in seek
    assert 'canvas.drawLine(left, cy, waveEnd, cy, wavePaint)' in seek
    assert 'wave.reset(' in seek and 'wave.advance()' in seek
    circular = source('app/regram/appearance/M3CircularProgress.java')
    assert 'wave.reset(waveStart, waveStep)' in circular
    assert 'waveAmplitude * wave.value()' in circular
    assert 'Math.sin(waveStart + i * waveStep)' not in circular


def test_camera_noop_removed_and_hide_invalidates_only_once():
    code = source('org/telegram/ui/Components/ChatAttachAlertPhotoLayout.java')
    assert 'onPhotoEditModeChanged' not in code
    hide = code.split('public void onHide()', 1)[1].split('private void pauseCameraPreview()', 1)[0]
    assert hide.count('cameraViewItemDecoration.updateBitmap()') == 1
    loop = hide.split('for (int a = 0; a < count; a++)', 1)[1].split('cameraViewItemDecoration.updateBitmap()', 1)[0]
    assert loop.rstrip().endswith('}')
    # Thumb persistence remains: StoryRecorder also reads the shared cthumb.jpg.
    assert 'private void saveLastCameraBitmap()' in code
    assert 'cthumb.jpg' in source('org/telegram/ui/Stories/recorder/StoryRecorder.java')


def test_executable_version_ordering_and_wave_math(tmp_path):
    javac, java = shutil.which('javac'), shutil.which('java')
    if not javac or not java:
        pytest.skip('JDK required for executable policy checks')
    harness = tmp_path / 'ReleaseDebloatCheck.java'
    harness.write_text('''
import app.regram.utils.SineWave;
import app.regram.updater.ReleaseVersion;
import java.util.Arrays;
import java.util.Random;

public class ReleaseDebloatCheck {
    static void check(boolean value) { if (!value) throw new AssertionError(); }
    static int code(int[] p) { return p[0]*1000000 + p[1]*10000 + p[2]*100 + p[3]; }
    public static void main(String[] args) {
        check(Arrays.equals(ReleaseVersion.parse("v1.1.6.1"), new int[]{1,1,6,1}));
        check(Arrays.equals(ReleaseVersion.parse("regram-v1.1(10100)-release.apk"), new int[]{1,1,0,0}));
        check(ReleaseVersion.compare("1.1", "v1.1.6.1") > 0);
        check(ReleaseVersion.compare("1.1.6.1", "v1.1.6.2") > 0);
        check(ReleaseVersion.compare("1.1.6.1", "v1.1.6") < 0);
        check(ReleaseVersion.compare("1.1.6.1", "v1.1.7") > 0);
        check(ReleaseVersion.compare("1.1.6.1", "v1.2") > 0);
        check(ReleaseVersion.compare("1.1.6.1", "v1.1.6.1") == 0);
        check(ReleaseVersion.compare("1.1.6.1", "unknown") == null);
        check(ReleaseVersion.parse("1.1.6.1.9") == null);
        check(ReleaseVersion.parse("999999999999999999.1") == null);
        check(ReleaseVersion.compare("1.1", null, "bad", "regram-v1.1.6.1-arm64-v8a.apk") > 0);
        check(code(ReleaseVersion.parse("1.1.6.1")) == 1010601);
        check(code(ReleaseVersion.parse("1.1.6.1")) > 10100);
        check(code(ReleaseVersion.parse("1.1.7")) > code(ReleaseVersion.parse("1.1.6.1")));
        check(code(new int[]{2099,99,99,99}) <= 2100000000);

        SineWave wave = new SineWave();
        Random random = new Random(1161);
        for (int run = 0; run < 2000; run++) {
            double start = (random.nextDouble() - .5) * 4000;
            double step = (random.nextDouble() - .5) * 6;
            wave.reset(start, step);
            for (int i = 0; i <= 600; i++) {
                check(Math.abs(wave.value() - Math.sin(start + i * step)) < 2e-12);
                wave.advance();
            }
        }
        // The slider used float arithmetic; subpixel agreement also covers dense/wide screens.
        float tau = (float)(Math.PI * 2);
        for (int density = 1; density <= 4; density++) {
            float left = 3 * density, step = 2 * density, lambda = 34 * density;
            for (float phase = 0; phase < tau; phase += .2f) {
                wave.reset(tau * left / lambda - phase, tau * step / lambda);
                for (int i = 0; i < 2000; i++) {
                    float x = left + i * step;
                    double oldY = 4 * density * Math.sin(tau * x / lambda - phase);
                    double newY = 4 * density * wave.value();
                    check(Math.abs(oldY - newY) < .01);
                    wave.advance();
                }
            }
        }
    }
}
''')
    subprocess.run([javac, '-d', str(tmp_path), str(JAVA / 'app/regram/utils/SineWave.java'),
                    str(JAVA / 'app/regram/updater/ReleaseVersion.java'), str(harness)],
                   check=True, capture_output=True, text=True)
    subprocess.run([java, '-cp', str(tmp_path), 'ReleaseDebloatCheck'],
                   check=True, capture_output=True, text=True, timeout=30)
