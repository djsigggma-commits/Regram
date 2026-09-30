from pathlib import Path
import hashlib
import re
import xml.etree.ElementTree as ET

ROOT = Path(__file__).resolve().parents[1]
SRC = ROOT / 'TMessagesProj/src/main'


def test_badge_art_uses_supplied_mark_with_contrasting_theme():
    for theme, mark in [('light', 'dark'), ('dark', 'light')]:
        actual = SRC / f'res/drawable-nodpi/regram_supporter_{theme}.png'
        original = ROOT / f'logo/icon_clean_{mark}.png'
        assert hashlib.sha256(actual.read_bytes()).digest() == hashlib.sha256(original.read_bytes()).digest()


def test_badge_is_only_attached_to_user_names_and_has_localized_explanation():
    code = (SRC / 'java/app/regram/badges/SupporterBadges.java').read_text()
    assert 'github.com/djsigggmagg-ui/regram-badges/raw/refs/heads/main/ids.txt' in code
    assert 'userId > 0 && ids.containsKey(userId)' in code
    assert 'new AnimatedEmojiDrawable.SwapAnimatedEmojiDrawable(name, iconWidth)' in code
    assert 'animatedIcon.setCurrentAccount(account)' in code
    assert 'animatedIcon.detach()' in code
    assert 'private static final ExecutorService IO' in code
    assert 'Utilities.globalQueue.postRunnable' not in code
    assert 'SupporterBadges.clear(' in (SRC / 'java/org/telegram/ui/ProfileActivity.java').read_text()
    assert 'SupporterBadges.clear(' in (SRC / 'java/org/telegram/ui/ChatActivity.java').read_text()
    assert 'setConnectTimeout(5000)' in code and 'MAX_BYTES = 128 * 1024' in code
    for file in ['org/telegram/ui/ProfileActivity.java', 'org/telegram/ui/ChatActivity.java']:
        assert 'SupporterBadges.decorate(' in (SRC / 'java' / file).read_text()
    for locale in ['values', 'values-ru-rRU']:
        xml = ET.parse(SRC / f'res/{locale}/strings_regram.xml')
        assert xml.find(".//string[@name='RegramSupporterInfo']") is not None


def test_id_line_format_allows_optional_emoji_but_not_malformed_ids():
    code = (SRC / 'java/app/regram/badges/SupporterBadges.java').read_text()
    expression = re.search(r'private static final Pattern LINE = Pattern.compile\(\s*"(.*?)"\);', code).group(1)
    # Decode the Java string escapes used by the Pattern constructor.
    expression = expression.replace('\\\\', '\\')
    pattern = re.compile(expression)
    assert pattern.fullmatch('748121342').groups() == ('748121342', None)
    assert pattern.fullmatch('7902274224 - tg://emoji?id=5391150165207852041').groups() == (
        '7902274224', '5391150165207852041')
    for bad in ('0', '-1', '7902274224 - tg://emoji?id=0', '7902274224 - https://evil.test',
                '7902274224 - tg://emoji?id=abc', '123 junk'):
        assert not pattern.fullmatch(bad)
