"""Last.fm was removed upstream in Actions 41526234112; keep it out of the runtime."""
from pathlib import Path

MAIN = Path(__file__).resolve().parents[1] / "TMessagesProj/src/main"
JAVA = MAIN / "java/app/regram/nowplaying"


def test_lastfm_workers_and_stamping_are_removed():
    for name in ("LastFmNowPlaying", "LastFmWebFetcher", "ProfileMusicMark",
                 "ProfileMusicStamp", "ZeroWidthCodec"):
        assert not (JAVA / f"{name}.java").exists()
    config = (MAIN / "kotlin/app/regram/general/GeneralConfig.kt").read_text()
    assert "lastfmNick" not in config
    settings = (MAIN / "java/app/regram/settings/OpenExteraGeneralActivity.java").read_text()
    assert "LastFm" not in settings


def test_saved_music_card_remains_without_background_web_requests():
    card = (MAIN / "java/app/regram/components/ProfileMusicCard.java").read_text()
    assert "public void set(TLRPC.Document document, long emojiDocumentId)" in card
    assert "LastFm" not in card
    assert "ZeroWidthCodec" not in card
    shim = (JAVA / "NowPlayingController.java").read_text()
    assert "return savedMusic != null;" in shim
