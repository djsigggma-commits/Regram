"""Guards for background-only CPU/network work and optional debug tooling."""
from pathlib import Path

ROOT = Path(__file__).resolve().parents[1]
JAVA = ROOT / "TMessagesProj/src/main/java"


def test_live_proxy_ping_pauses_in_background_and_resumes():
    code = (JAVA / "org/telegram/messenger/ProxyPingController.java").read_text()
    assert 'implements ForegroundDetector.Listener' in code
    assert 'if (!NekoConfig.regramLiveProxyPing.Bool() || !isForeground())' in code
    assert 'detector.addListener(INSTANCE)' in code
    assert 'onBecameBackground()' in code
    assert '++generation; // Late native callbacks' in code
    assert 'onBecameForeground()' in code


def test_pills_pause_refresh_while_detached_hidden_or_backgrounded():
    code = (JAVA / "app/regram/pillstack/pills/BasePill.java").read_text()
    assert 'stackVisible && isAttachedToWindow() && isAppForeground()' in code
    assert 'detector.addListener(foregroundListener)' in code
    assert 'detector.removeListener(foregroundListener)' in code
    assert 'removeCallbacks(autoRefreshRunnable)' in code
    assert 'if (isRefreshDue()) onUpdateData(false);' in code


def test_telemetry_does_not_enqueue_overlapping_measurements():
    code = (JAVA / "app/regram/pillstack/pills/TelemetryPill.java").read_text()
    assert 'if (measurementPending) return;' in code
    assert 'measurementPending = false;' in code
    assert 'if (!isAttachedToWindow()) return;' in code


def test_exitfy_lite_bounds_concurrent_probes_and_ignores_stale_switches():
    code = (JAVA / "app/regram/ui/ExitFyLite.java").read_text()
    assert 'MAX_PARALLEL_CHECKS = 4' in code
    assert 'while (pending.size() < MAX_PARALLEL_CHECKS' in code
    assert 'CHECK_BATCH_TIMEOUT_MS' in code
    assert 'originalProxy != SharedConfig.currentProxy' in code
    assert 'if (activeBatch != this) return;' in code


def test_download_and_badge_fetch_do_not_block_telegram_global_queue():
    badges = (JAVA / 'app/regram/badges/SupporterBadges.java').read_text()
    installer = (JAVA / 'app/regram/plugins/ui/RecommendedPluginsInstaller.java').read_text()
    profile = (JAVA / 'org/telegram/ui/ProfileActivity.java').read_text()
    chat = (JAVA / 'org/telegram/ui/ChatActivity.java').read_text()
    assert 'private static final ExecutorService IO' in badges
    assert 'private static final ExecutorService COPY_IO' in installer
    assert 'Utilities.globalQueue.postRunnable' not in badges
    assert 'Utilities.globalQueue.postRunnable' not in installer
    assert 'pending.get(nextIndex++)' in installer
    assert 'pending.remove(0)' not in installer
    assert 'SupporterBadges.clear(title)' in profile
    assert 'SupporterBadges.withoutBadge(titleTextView.getRightDrawable2())' in profile
    assert 'SupporterBadges.clear(avatarContainer.getTitleTextView())' in chat


def test_remote_debugger_opt_in_and_plugin_bundles_kept():
    build = (ROOT / 'TMessagesProj/build.gradle').read_text()
    assert "if (System.getenv('REGRAM_PY_DEBUGGER') == '1')" in build
    assert "install 'debugpy'" in build
    assert "install 'requests'" in build
    assert (ROOT / 'TMessagesProj/src/main/assets/regram/custom_profile.py').is_file()
    assert (ROOT / 'TMessagesProj/src/main/assets/regram/exitFy_v2.py').is_file()
