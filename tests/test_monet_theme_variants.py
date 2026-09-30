"""The four Monet presets must stay selectable with either palette generator."""
from pathlib import Path

ROOT = Path(__file__).resolve().parents[1]
ASSETS = ROOT / 'TMessagesProj/src/main/assets'
THEME = ROOT / 'TMessagesProj/src/main/java/org/telegram/ui/ActionBar/Theme.java'
WHITE_SURFACES = {
    'actionBarDefault', 'windowBackgroundWhite', 'windowBackgroundGray',
    'dialogBackground', 'chat_wallpaper', 'chat_messagePanelBackground',
}


def parse(path):
    return dict(line.split('=', 1) for line in path.read_text().splitlines() if '=' in line)


def test_four_monet_presets_registered_and_reloadable():
    code = THEME.read_text()
    for name, asset in [('Monet Light', 'monet_light'), ('Monet Dark', 'monet_dark'),
                        ('Monet AMOLED', 'monet_amoled'), ('Monet AMOLED Light', 'monet_amoled_light')]:
        assert f'themeInfo.name = "{name}"' in code
        assert f'monetAssetName("{asset}")' in code
        assert f'{{"{name}", "{asset}"}}' in code
        for suffix in ('', '_gram'):
            assert (ASSETS / f'{asset}{suffix}.attheme').is_file()
    assert '"Monet AMOLED Light".equals(name)' in code
    assert 'themeInfo.previewBackgroundColor = MonetHelper.getColor("white")' in code


def test_amoled_light_keeps_light_contrast_and_accent_in_both_styles():
    for suffix in ('', '_gram'):
        base = parse(ASSETS / f'monet_light{suffix}.attheme')
        amoled = parse(ASSETS / f'monet_amoled_light{suffix}.attheme')
        assert base.keys() == amoled.keys()
        assert all(amoled[key] == 'white' for key in WHITE_SURFACES)
        for key in ('windowBackgroundWhiteBlackText', 'actionBarDefaultTitle',
                    'chat_inBubble', 'chat_outBubble', 'chat_inAudioTitleText'):
            assert amoled[key] == base[key]
