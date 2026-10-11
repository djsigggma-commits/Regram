from pathlib import Path
import xml.etree.ElementTree as ET
import hashlib

import pytest

ROOT = Path(__file__).resolve().parents[1] / 'TMessagesProj/src/main'


def test_credits_usernames_open_telegram_and_art_is_displayed():
    code = (ROOT / 'java/app/regram/ui/RegramCreditsActivity.java').read_text()
    assert 'Browser.openUrl(getParentActivity(), "https://t.me/" + username.substring(1))' in code
    assert 'isUsername(CREATORS[position - firstCreatorRow])' in code
    assert 'isUsername(SOURCES[position - firstSourceRow])' in code
    assert 'artRow = addRow()' in code
    assert 'setImageResource(R.drawable.regram_credits_art)' in code
    assert 'ImageView.ScaleType.FIT_CENTER' in code
    assert (ROOT / 'res/drawable-nodpi/regram_credits_art.png').is_file()
    for locale in ('values', 'values-ru-rRU'):
        xml = ET.parse(ROOT / f'res/{locale}/strings_regram.xml')
        assert xml.find(".//string[@name='RegramCreditsArtDescription']") is not None


def test_credits_art_uses_new_landscape_image_without_cropping():
    image = pytest.importorskip('PIL.Image')
    with image.open(ROOT / 'res/drawable-nodpi/regram_credits_art.png') as art:
        assert art.size == (736, 463)
        assert hashlib.sha256(art.convert('RGB').tobytes()).hexdigest() == (
            '4b13551013fd22ad58d10c11107987471ee1ac3510d07068e8223ca925f1a78b')
    code = (ROOT / 'java/app/regram/ui/RegramCreditsActivity.java').read_text()
    assert 'ViewGroup.LayoutParams.MATCH_PARENT, AndroidUtilities.dp(244), Gravity.CENTER' in code
    assert 'layout.leftMargin = layout.rightMargin = AndroidUtilities.dp(16)' in code
    assert 'ImageView.ScaleType.FIT_CENTER' in code


def test_donation_cards_are_shown_and_copyable_in_credits():
    code = (ROOT / 'java/app/regram/ui/RegramCreditsActivity.java').read_text()
    assert '2200 7012 4526 7221' in code
    assert '4937 2410 0693 1323' in code
    assert 'donationHeaderRow = addRow(' in code
    assert 'mirRow = addRow(' in code and 'visaRow = addRow(' in code
    assert 'AndroidUtilities.addToClipboard' in code
    for locale in ('values', 'values-ru-rRU'):
        xml = ET.parse(ROOT / f'res/{locale}/strings_regram.xml')
        for key in ('RegramDonation', 'RegramDonationMir', 'RegramDonationVisa', 'RegramDonationCopyHint'):
            assert xml.find(f".//string[@name='{key}']") is not None
