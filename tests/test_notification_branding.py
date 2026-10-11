"""Notification small icons must use the supplied re:gram silhouette."""
from pathlib import Path
import xml.etree.ElementTree as ET

import pytest

ROOT = Path(__file__).resolve().parents[1]
RES = ROOT / 'TMessagesProj/src/main/res'
JAVA = ROOT / 'TMessagesProj/src/main/java'
ANDROID = '{http://schemas.android.com/apk/res/android}'


def test_notification_resource_no_longer_contains_exteraless_vector():
    icon = ET.parse(RES / 'drawable-anydpi/regram_notification.xml').getroot()
    assert icon.tag == 'bitmap'
    assert icon.get(ANDROID + 'src') == '@drawable/regram_notification_mark'
    assert not icon.findall('.//path')
    call = ET.parse(RES / 'drawable/call_custom_notification_icon.xml').getroot()
    assert any(item.get(ANDROID + 'drawable') == '@drawable/regram_notification'
               for item in call.findall('item'))


@pytest.mark.parametrize('density,size', [('mdpi',24), ('hdpi',36), ('xhdpi',48),
                                         ('xxhdpi',72), ('xxxhdpi',96)])
def test_small_icon_is_white_alpha_mask_of_supplied_logo(density, size):
    image = pytest.importorskip('PIL.Image')
    with image.open(ROOT / 'logo/icon_clean_light.png') as source:
        alpha = source.convert('RGBA').getchannel('A')
        width = round(size * 22 / 24)
        height = round(width * alpha.height / alpha.width)
        expected = image.new('L', (size, size), 0)
        expected.paste(alpha.resize((width, height), image.Resampling.LANCZOS),
                       ((size - width) // 2, (size - height) // 2))
    with image.open(RES / f'drawable-{density}/regram_notification_mark.png') as icon:
        assert icon.mode == 'RGBA'
        assert icon.size == (size, size)
        assert icon.getchannel('A').tobytes() == expected.tobytes()
        assert icon.getchannel('A').getbbox() is not None
        assert icon.getpixel((0, 0))[3] == 0
        for channel in 'RGB':
            assert icon.getchannel(channel).getextrema() == (255, 255)


def test_message_push_and_migration_notifications_use_new_resource():
    controller = (JAVA / 'org/telegram/messenger/NotificationsController.java').read_text()
    assert controller.count('.setSmallIcon(getNotificationIconResId())') == 2
    selection = controller[controller.index('private int getNotificationIconResId()'):]
    assert 'return R.drawable.regram_notification;' in selection
    push = (JAVA / 'org/telegram/messenger/NotificationsService.java').read_text()
    assert '.setSmallIcon(R.drawable.regram_notification)' in push
    migration = (JAVA / 'org/telegram/messenger/FilesMigrationService.java').read_text()
    assert migration.count('.setSmallIcon(R.drawable.regram_notification)') == 2
    assert '.setSmallIcon(R.drawable.notification)' not in migration
    generator = (ROOT / 'tools/generate_launcher_icons.py').read_text()
    assert 'def generate_notification_icons():' in generator
    assert '    generate_notification_icons()' in generator
