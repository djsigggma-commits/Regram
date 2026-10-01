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


def test_system_accent_and_glass_colors_resolve_in_both_monet_styles():
    for variant in ('light', 'dark', 'amoled', 'amoled_light'):
        for suffix in ('', '_gram'):
            colors = parse(ASSETS / f'monet_{variant}{suffix}.attheme')
            accent = 'a1_600' if variant in ('light', 'amoled_light') else 'a1_200'
            assert colors['color_blue'] == accent
            assert colors['telegram_color'] == accent
            assert colors['telegram_color_text'] == accent
            assert colors['glass_tabSelected'].startswith('a1_')
            assert colors['glass_tabSelectedText'].startswith('a1_')
            assert colors['botKeyboard_button_primary'].startswith('a1_')
            assert colors['chat_tagAdmin'].startswith('a1_')
            assert colors['chat_tagCreator'].startswith('a3_')
            assert colors['stories_circle_live1'].startswith('a1_')
            assert colors['stories_circle_live2'].startswith('a1_')
            assert colors['glass_targetMainTabs'] in {'white', 'black', 'n1_50', 'n1_900'} or colors['glass_targetMainTabs'].startswith('surface_')
            assert len(colors) == len([line for line in (ASSETS / f'monet_{variant}{suffix}.attheme').read_text().splitlines() if '=' in line])


def test_resume_reads_fresh_system_palette_instead_of_cached_token():
    code = (ROOT / 'TMessagesProj/src/main/java/tw/nekomimi/nekogram/helpers/MonetHelper.java').read_text()
    refresh = code.split('public static void refreshMonetThemeIfChanged()', 1)[1]
    assert 'context.getColor(android.R.color.system_accent1_600)' in refresh
    assert 'context.getColor(android.R.color.system_accent2_600)' in refresh
    assert 'context.getColor(android.R.color.system_neutral1_900)' in refresh
    assert 'Arrays.equals(lastMonetColors, currentColors)' in refresh
    assert 'int currentColor = getColor("a1_600")' not in refresh


def test_amoled_light_keeps_light_contrast_and_accent_in_both_styles():
    for suffix in ('', '_gram'):
        base = parse(ASSETS / f'monet_light{suffix}.attheme')
        amoled = parse(ASSETS / f'monet_amoled_light{suffix}.attheme')
        assert base.keys() == amoled.keys()
        assert all(amoled[key] == 'white' for key in WHITE_SURFACES)
        for key in ('windowBackgroundWhiteBlackText', 'actionBarDefaultTitle',
                    'chat_inBubble', 'chat_outBubble', 'chat_inAudioTitleText'):
            assert amoled[key] == base[key]
