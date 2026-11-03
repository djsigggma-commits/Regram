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


def test_dialog_list_reserves_badge_space_and_preserves_existing_icons():
    cell = (SRC / 'java/org/telegram/ui/Cells/DialogCell.java').read_text()
    dialogs = (SRC / 'java/org/telegram/ui/DialogsActivity.java').read_text()
    assert 'SupporterBadges.forView(this, supporterBadge,' in cell
    assert 'customDialog == null && currentDialogFolderId == 0 && !isTopic && user != null ? user.id : 0' in cell
    reserve = cell.index('if (supporterBadge != null) {')
    assert reserve < cell.index('final boolean reserveMuteSlot') < cell.index('int ellipsizeWidth = nameWidth')
    assert 'nameWidth -= w;' in cell[reserve:cell.index('final boolean reserveMuteSlot')]
    assert 'nameMuteLeft += w;' in cell and 'nameMuteLeft -= w;' in cell
    assert 'nameMutedIconLeft += w;' in cell and 'nameMutedIconLeft -= w;' in cell
    assert 'supporterBadge.draw(canvas);' in cell
    assert 'SupporterBadges.refresh(() -> updateVisibleRows(0, false))' in dialogs


def test_canvas_badges_reuse_drawables_and_release_emoji_on_rebind():
    code = (SRC / 'java/app/regram/badges/SupporterBadges.java').read_text()
    factory = code[code.index('public static Drawable forView('):code.index('/** Don\'t share a badge drawable')]
    assert 'return previous;' in factory
    assert 'previous.release(owner)' in factory
    assert 'previous.setCallback(null)' in factory
    assert 'if (configured == null) return null;' in factory
    assert 'new BadgeAndIcon(owner, null, account, configured.emojiId' in factory
    assert 'BadgeAndIcon(View name,' in code
    assert 'name.addOnAttachStateChangeListener(this)' in code
    assert 'name.removeOnAttachStateChangeListener(this)' in code
    assert 'onViewAttachedToWindow(View view)' in code
    assert 'onViewDetachedFromWindow(View view)' in code


def test_id_line_format_allows_optional_emoji_and_message_but_not_malformed_ids():
    code = (SRC / 'java/app/regram/badges/SupporterBadges.java').read_text()
    expression = re.search(r'private static final Pattern LINE = Pattern.compile\(\s*"(.*?)"\);', code).group(1)
    # Decode the Java string escapes used by the Pattern constructor.
    expression = expression.replace('\\\\', '\\')
    pattern = re.compile(expression)
    assert pattern.fullmatch('748121342').groups() == ('748121342', None, None)
    assert pattern.fullmatch('7902274224 - tg://emoji?id=5391150165207852041').groups() == (
        '7902274224', '5391150165207852041', None)
    for line, groups in (
        ('748121342 - tg://emoji?id=5296437331648096571 - разраб1',
         ('748121342', '5296437331648096571', 'разраб1')),
        ('7902274224 - tg://emoji?id=5391150165207852041 - разраб2',
         ('7902274224', '5391150165207852041', 'разраб2')),
        ('1235025966 - tg://emoji?id=5296437331648096571 - дезигнер',
         ('1235025966', '5296437331648096571', 'дезигнер')),
    ):
        assert pattern.fullmatch(line).groups() == groups
    for bad in ('0', '-1', '7902274224 - tg://emoji?id=0', '7902274224 - https://evil.test',
                '7902274224 - tg://emoji?id=abc', '123 junk',
                '123 - tg://emoji?id=456 - ', '123 - кастомный текст'):
        assert not pattern.fullmatch(bad)


def test_message_change_updates_existing_badge_and_old_entries_keep_fallback():
    code = (SRC / 'java/app/regram/badges/SupporterBadges.java').read_text()
    assert 'MAX_MESSAGE_LENGTH = 200' in code
    assert 'message.length() > MAX_MESSAGE_LENGTH' in code
    assert 'return emojiId == info.emojiId && TextUtils.equals(message, info.message);' in code
    assert 'TextUtils.equals(previous.message, configured.message)' in code
    assert 'badge.message != null ? badge.message' in code
    assert 'name.getContext().getString(R.string.RegramSupporterInfo)' in code
