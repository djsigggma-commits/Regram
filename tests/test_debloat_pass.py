"""Source guards plus executable Java checks; not on-device benchmarks."""
import os
from pathlib import Path
import shutil
import subprocess

import pytest

ROOT = Path(__file__).resolve().parents[1]
JAVA = ROOT / 'TMessagesProj/src/main/java'


def source(path):
    return (JAVA / path).read_text()


def test_downloads_share_bounded_progress_delivery():
    for path in ('app/regram/updater/GitHubUpdater.java', 'app/regram/speech/VoskManager.java'):
        code = source(path)
        assert 'new DownloadProgress(' in code
        assert 'AndroidUtilities.runOnUIThread(task, 100)' in code
        assert 'progress.update(done,' in code
        assert 'progress.finish();' in code
    vosk = source('app/regram/speech/VoskManager.java')
    assert vosk.index('progress.finish();') < vosk.index('downloadingCode = null;')
    unzip = vosk.split('while ((read = zip.read(buffer)) != -1)', 1)[1]
    assert unzip.index('cancelFlag.get()') < unzip.index('output.write(')


def test_model_sizes_are_not_scanned_during_ui_binding():
    settings = source('app/regram/speech/VoskSettingsActivity.java')
    assert 'VoskManager.installedSize(' not in settings
    assert 'pendingSizes.add(model.code)' in settings
    assert 'VoskManager.requestInstalledSize(model.code' in settings
    assert 'if (generation != sizeGeneration) return;' in settings
    assert 'installedSizes.remove(model.code)' in settings
    manager = source('app/regram/speech/VoskManager.java')
    query = manager.split('public static void requestInstalledSize', 1)[1].split('public static String', 1)[0]
    assert 'MODEL_INFO_IO.execute(' in query
    assert 'long size = installedSize(code);' in query


def test_hidden_recent_users_screen_does_not_refresh():
    code = source('app/regram/ui/RecentlyOnlineActivity.java')
    pause = code.split('public void onPause()', 1)[1].split('public void onFragmentDestroy()', 1)[0]
    assert 'removeObserver(this, NotificationCenter.updateInterfaces)' in pause
    assert 'cancelRunOnUIThread(refreshRunnable)' in pause
    assert 'if (resumed && list != null) refresh();' in code
    assert 'if (resumed && list != null && !refreshScheduled)' in code


def test_ring_uses_arc_when_flat_and_precomputed_wave_samples():
    code = source('app/regram/player/RingPlayButton.java')
    assert 'if (amplitude == 0f)' in code
    assert 'canvas.drawArc(' in code
    assert 'geometry.radiusAt(' in code
    assert 'WaveRingGeometry.X[i]' in code
    assert '!isShown() || getWindowVisibility() != VISIBLE' in code


def test_unused_tab_measurements_removed():
    code = source('org/telegram/ui/MainTabsLayout.java')
    for unused in ('tabsLeftPos', 'biggestTabTextWidth', 'maxTabWidthF', 'import android.util.Log;'):
        assert unused not in code
    # Public compatibility entry point is retained.
    assert 'public void updateLayout()' in source('org/telegram/ui/MainTabsActivity.java')


def test_java_progress_and_geometry(tmp_path):
    javac = shutil.which('javac')
    if not javac:
        candidates = [Path(os.environ.get('JAVA_HOME', '/nonexistent')) / 'bin/javac',
                      Path.home() / 'jdk17/bin/javac']
        javac = next((str(p) for p in candidates if p.is_file()), None)
    if not javac:
        pytest.skip('JDK required for executable Java regression checks')
    harness = tmp_path / 'DebloatCheck.java'
    harness.write_text('''
package app.regram.player;
import app.regram.utils.DownloadProgress;
import java.util.ArrayDeque;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.atomic.AtomicInteger;

public class DebloatCheck {
    static void check(boolean value) { if (!value) throw new AssertionError(); }
    public static void main(String[] args) throws Exception {
        ArrayDeque<Runnable> queue = new ArrayDeque<>();
        long[] last = {-1, -1};
        int[] calls = {0};
        DownloadProgress progress = new DownloadProgress(queue::add, (done, total) -> {
            last[0] = done; last[1] = total; calls[0]++;
        });
        for (int i = 0; i < 100000; i++) progress.update(i, 100000);
        check(queue.size() == 1);
        queue.remove().run();
        check(calls[0] == 1 && last[0] == 99999 && last[1] == 100000);
        progress.update(100000, 100000);
        progress.finish();
        check(calls[0] == 2 && last[0] == 100000);
        queue.remove().run();
        progress.update(100001, 100000);
        check(queue.isEmpty() && calls[0] == 2);
        progress.finish();
        check(calls[0] == 2);

        // Updating during a callback must queue the next (latest) value, not lose it.
        DownloadProgress[] reentrant = new DownloadProgress[1];
        reentrant[0] = new DownloadProgress(queue::add, (done, total) -> {
            last[0] = done;
            if (done == 1) reentrant[0].update(2, total);
        });
        reentrant[0].update(1, 2);
        queue.remove().run();
        check(queue.size() == 1);
        queue.remove().run();
        check(last[0] == 2);
        reentrant[0].finish();

        ConcurrentLinkedQueue<Runnable> concurrent = new ConcurrentLinkedQueue<>();
        AtomicInteger delivered = new AtomicInteger();
        DownloadProgress parallel = new DownloadProgress(concurrent::add, (done, total) -> delivered.incrementAndGet());
        Thread[] workers = new Thread[4];
        for (int i = 0; i < workers.length; i++) {
            workers[i] = new Thread(() -> {
                for (int n = 0; n < 10000; n++) parallel.update(n, 10000);
            });
            workers[i].start();
        }
        for (Thread worker : workers) worker.join();
        check(concurrent.size() == 1);
        parallel.finish();
        concurrent.remove().run();
        check(delivered.get() == 1);

        for (int waves : new int[] {0, 1, 3, 16, 32}) {
            WaveRingGeometry geometry = new WaveRingGeometry(waves);
            for (int p = 0; p < 20; p++) {
                float phase = p * 0.31f;
                for (int i = 0; i <= WaveRingGeometry.STEPS; i++) {
                    double deg = i * WaveRingGeometry.STEP_DEGREES;
                    double expected = 30 + 4 * Math.sin(waves * Math.toRadians(deg) - phase);
                    float actual = geometry.radiusAt(i, 30, 4, (float)Math.sin(phase), (float)Math.cos(phase));
                    check(Math.abs(actual - expected) < 0.00001);
                    check(Math.abs(WaveRingGeometry.X[i] - Math.cos(Math.toRadians(deg - 90))) < 0.000001);
                    check(Math.abs(WaveRingGeometry.Y[i] - Math.sin(Math.toRadians(deg - 90))) < 0.000001);
                }
            }
        }
    }
}
''')
    subprocess.run([javac, '-d', str(tmp_path),
                    str(JAVA / 'app/regram/utils/DownloadProgress.java'),
                    str(JAVA / 'app/regram/player/WaveRingGeometry.java'), str(harness)],
                   check=True, capture_output=True, text=True)
    java = str(Path(javac).resolve().with_name('java'))
    subprocess.run([java, '-cp', str(tmp_path), 'app.regram.player.DebloatCheck'],
                   check=True, capture_output=True, text=True, timeout=30)
