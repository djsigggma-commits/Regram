"""Regression guards for the beta14 performance pass (not device benchmarks)."""
from pathlib import Path

ROOT = Path(__file__).resolve().parents[1]
JAVA = ROOT / 'TMessagesProj/src/main/java'


def source(path):
    return (JAVA / path).read_text()


def test_cached_proxy_ping_stops_in_background_and_without_proxy():
    code = source('app/regram/proxy/ProxyPingController.java')
    assert 'implements ForegroundDetector.Listener' in code
    assert 'detector.addListener(INSTANCE)' in code
    assert 'NotificationCenter.proxySettingsChanged' in code
    assert 'detector.isForeground()' in code
    assert 'SharedConfig.isProxyEnabled() && SharedConfig.currentProxy != null' in code
    assert 'if (!shouldPoll()) return;' in code
    background = code.split('public void onBecameBackground()', 1)[1].split('}', 1)[0]
    assert 'cancelRunOnUIThread(PING)' in background
    assert 'if (INSTANCE.initialized) return;' in code


def test_production_hook_registration_does_not_allocate_profile_stats():
    for name in ('PyMethodHook', 'PyMethodReplacement'):
        code = source(f'app/regram/plugins/xposed/{name}.java')
        assert 'BuildConfig.DEBUG_TOOLS ? HookStats.nextId() : -1' in code
        for line in code.splitlines():
            if 'new HookStats(' in line:
                assert 'BuildConfig.DEBUG_TOOLS &&' in line


def test_lyrics_requests_and_negative_cache_are_bounded():
    code = source('app/regram/player/OnlineLyrics.java')
    assert 'LruCache<String, Long> missing = new LruCache<>(128)' in code
    assert 'SystemClock.elapsedRealtime() - checked < MISSING_TTL_MS' in code
    assert 'if (!enqueueFetch(key, callback)) return;' in code
    assert 'inFlight.remove(key)' in code
    assert 'finishFetch(key, lyrics, finalStatus)' in code
    assert 'MAX_RESPONSE_BYTES - out.size()' in code
    assert 'HashSet<String> missing' not in code


def test_cover_quantization_is_coalesced_and_releases_owned_samples():
    art = source('app/regram/player/PlayerArt.java')
    colors = source('app/regram/player/PlayerColors.java')
    assert 'pending.get(key)' in art and 'pending.remove(key)' in art
    assert 'pending.size()' not in art
    assert 'COLORS_IO.execute(' in art
    assert 'Utilities.globalQueue' not in art
    assert 'finally {\n                sample.recycle();' in art
    assert 'source != bitmap) source.recycle();' in colors


def test_color_animation_reuses_immutable_palette_arrays():
    code = source('app/regram/player/PlayerColors.java')
    assert 'LruCache<Long, PlayerColors> PALETTES = new LruCache<>(32)' in code
    assert 'PlayerColors cached = PALETTES.get(key);' in code
    assert 'private final int[] colors;' in code
    assert 'int[] from = a.colors;' in code
    assert 'int[] to = b.colors;' in code
    assert 'private int[] values()' not in code
    icons = source('app/regram/player/PlayerIcon.java')
    assert 'LruCache<String, Path> cache = new LruCache<>(64)' in icons


def test_unreachable_private_code_removed_without_removing_legacy_read():
    app = source('org/telegram/messenger/ApplicationLoader.java')
    ai = source('app/regram/ai/AiConfig.java')
    pills = source('app/regram/pillstack/PillStackSettingsActivity.java')
    assert 'debugEverySecondChecks' not in app
    assert 'setSelectedServiceHash' not in ai
    assert 'getSelectedServiceHash' in ai
    assert 'openLocationPicker' not in pills
    assert 'getWeatherLocationValue' in pills
