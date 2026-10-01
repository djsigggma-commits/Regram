"""Prevent regressions in the Last.fm worker's memory and queue limits."""
from pathlib import Path

JAVA = Path(__file__).resolve().parents[1] / "TMessagesProj/src/main/java/app/regram/nowplaying"


def test_recent_tracks_cache_is_bounded_and_expired_entries_are_removed():
    code = (JAVA / "LastFmNowPlaying.java").read_text()
    assert "CACHE_LIMIT = 128" in code
    assert "size() > CACHE_LIMIT" in code
    assert "CACHE.remove(nick);" in code
    assert "new CachedTrack(track, System.currentTimeMillis())" in code
    assert "STAMPS" not in code


def test_html_reader_respects_prefix_limit_even_on_final_read():
    code = (JAVA / "LastFmNowPlaying.java").read_text()
    assert "Math.min(buffer.length, PREFIX_LIMIT - sb.length())" in code


def test_web_requests_dequeue_without_shifting_and_reset_idle_release():
    code = (JAVA / "LastFmWebFetcher.java").read_text()
    assert "ArrayDeque<Pending> queue" in code
    assert "queue.removeFirst()" in code
    assert "queue.remove(0)" not in code
    finish = code.split("private static void finish(", 1)[1].split("@SuppressLint", 1)[0]
    assert "scheduleRelease();" in finish
