"""Quick theme palette math, nesting and integration (device drawing still needs testing)."""
from pathlib import Path
import re
import shutil
import subprocess
import xml.etree.ElementTree as ET

import pytest

ROOT = Path(__file__).resolve().parents[1]
JAVA = ROOT / 'TMessagesProj/src/main/java'
POLICY = JAVA / 'app/regram/appearance/QuickTheme.java'


def method(source, signature):
    start = source.index(signature)
    brace = source.index('{', start)
    depth, end = 1, brace + 1
    while depth:
        depth += (source[end] == '{') - (source[end] == '}')
        end += 1
    return source[start:end]


def test_settings_are_reachable_translated_and_disabled_by_default():
    route = (JAVA / 'app/regram/settings/OpenExteraAppearanceActivity.java').read_text()
    assert 'quickThemeRow = addRow("quickTheme", "RegramQuickTheme")' in route
    assert 'presentFragment(new QuickThemeActivity())' in route
    config = (JAVA / 'tw/nekomimi/nekogram/NekoConfig.java').read_text()
    assert 'addConfig("RegramQuickTheme", configTypeBool, false)' in config
    assert 'addConfig("RegramQuickContrast", configTypeInt, 100)' in config
    settings = (JAVA / 'app/regram/settings/QuickThemeActivity.java').read_text()
    strings = []
    for locale in ('values', 'values-ru-rRU'):
        strings.append({node.get('name') for node in ET.parse(
            ROOT / f'TMessagesProj/src/main/res/{locale}/regram_quick_theme.xml').getroot()})
    assert strings[0] == strings[1]
    assert set(re.findall(r'R.string.(RegramQuick\w+)', settings)) <= strings[0]
    assert 'items[index].saveConfig();' in settings
    drag = method(settings, 'public void onSeekBarDrag(boolean stop, float progress)')
    assert 'items[index].value = value;' in drag
    assert '.saveConfig(' not in drag
    assert 'preview.invalidate()' in drag
    assert 'if (stop) persist()' in drag
    assert 'QuickTheme.reset()' in settings
    reset = (JAVA / 'app/regram/general/GeneralHelper.java').read_text()
    assert 'app.regram.appearance.QuickTheme.reset()' in reset


def test_opacity_wraps_all_bubble_paths_but_not_text_or_blur_masks():
    source = (JAVA / 'org/telegram/ui/ActionBar/MessageDrawable.java').read_text()
    draw = method(source, 'public void draw(Canvas canvas, Paint paintToUse)')
    assert 'opacity == 255 || paintToUse != null' in draw
    assert 'canvas.saveLayerAlpha(' in draw
    assert 'finally' in draw and 'canvas.restoreToCount(save)' in draw
    assert 'drawInternal(canvas, paintToUse)' in draw
    original = method(source, 'private void drawInternal(Canvas canvas, Paint paintToUse)')
    assert 'getBackgroundDrawable()' in original
    assert 'canvas.drawPath(path, p)' in original
    policy = POLICY.read_text()
    assert 'Theme.key_chat_messageTextIn' not in policy
    assert 'Theme.key_chat_outBubbleGradientAnimated' not in policy
    for prohibited in ('Theme.setColor', 'currentColors.put', 'BitmapFactory', 'ColorMatrixColorFilter'):
        assert prohibited not in policy


def test_real_policy_math_and_provider_recursion(tmp_path):
    javac, java = shutil.which('javac'), shutil.which('java')
    if not javac or not java:
        pytest.skip('JDK unavailable')
    source = POLICY.read_text()
    message = (JAVA / 'org/telegram/ui/ActionBar/MessageDrawable.java').read_text()
    message_color = method(message, 'protected int getColor(int key)')
    message_current = method(message, 'protected int getCurrentColor(int key)')
    keys = sorted(set(re.findall(r'Theme\.(key_\w+)', source)))
    constants = '\n'.join(f'public static final int {key} = {index};' for index, key in enumerate(keys, 1))
    theme = (JAVA / 'org/telegram/ui/ActionBar/Theme.java').read_text()
    with_provider = method(theme, 'public static int getColor(int key, ResourcesProvider provider)')
    global_color = method(theme, 'public static int getColor(int key, boolean[] isDefault, boolean ignoreAnimation)')
    sources = {
        'app/regram/appearance/QuickTheme.java': source,
        'tw/nekomimi/nekogram/NekoConfig.java': '''package tw.nekomimi.nekogram;
            public class NekoConfig {
                public static final Item regramQuickTheme = new Item(0),
                    regramQuickTransparency = new Item(0), regramQuickContrast = new Item(100),
                    regramQuickSaturation = new Item(100), regramQuickBrightness = new Item(0);
                public static class Item {
                    public int value;
                    Item(int value) { this.value = value; }
                    public int Int() { return value; }
                    public boolean Bool() { return value != 0; }
                    public void setConfigInt(int v) { value = v; }
                    public void setConfigBool(boolean v) { value = v ? 1 : 0; }
                }
            }''',
        'org/telegram/ui/ActionBar/Theme.java': '''package org.telegram.ui.ActionBar;
            public class Theme {
            ''' + constants + with_provider + global_color + '''
                public interface ResourcesProvider {
                    int getColor(int key);
                    default int getCurrentColor(int key) { return getColor(key); }
                }
                public static int getCurrentColor(int key) { return 0x9f63a5d7; }
                public static int getColor(int key) { return getColor(key, null, false); }
                private static int getColorWithProviderRaw(int key, ResourcesProvider provider) {
                    return provider == null ? getColor(key) : provider.getColor(key);
                }
                private static int getColorRaw(int key, boolean[] isDefault, boolean ignoreAnimation) {
                    return 0x9f63a5d7;
                }
            }''',
        'MessageColorProbe.java': '''import org.telegram.ui.ActionBar.Theme;
            public class MessageColorProbe {
                private static final int TYPE_PREVIEW = 2;
                private int currentType;
                private final Theme.ResourcesProvider resourcesProvider;
                public MessageColorProbe(Theme.ResourcesProvider provider) { resourcesProvider = provider; }
                public int color(int key) { return getColor(key); }
                public int current(int key) { return getCurrentColor(key); }
            ''' + message_color + message_current + '}',
        'CheckQuickTheme.java': '''import app.regram.appearance.QuickTheme;
            import tw.nekomimi.nekogram.NekoConfig;
            import org.telegram.ui.ActionBar.Theme;
            public class CheckQuickTheme {
                static void check(boolean value) { if (!value) throw new AssertionError(); }
                public static void main(String[] args) {
                    for (int color : new int[]{0, 0xffffffff, 0xff000000, 0x0063a5d7, 0x9f63a5d7}) {
                        check(QuickTheme.transform(color,100,100,0) == color);
                        check((QuickTheme.transform(color,150,200,30) >>> 24) == (color >>> 24));
                    }
                    int gray = QuickTheme.transform(0xfff03090,100,0,0);
                    check(((gray >>> 16) & 255) == ((gray >>> 8) & 255));
                    check(((gray >>> 8) & 255) == (gray & 255));
                    check(QuickTheme.transform(0xff102030,100,100,30) != 0xff102030);
                    check(QuickTheme.transform(0xffffffff,999,999,999) == 0xffffffff);
                    check(QuickTheme.bubbleAlpha() == 255);
                    check(!QuickTheme.beginResolve());
                    NekoConfig.regramQuickTheme.value = 1;
                    NekoConfig.regramQuickTransparency.value = 80;
                    check(QuickTheme.bubbleAlpha() == 51);
                    NekoConfig.regramQuickTransparency.value = 999;
                    check(QuickTheme.bubbleAlpha() == 51);
                    NekoConfig.regramQuickTransparency.value = -1;
                    check(QuickTheme.bubbleAlpha() == 255);
                    check(!QuickTheme.beginResolve()); // Transparency alone needs no palette guard.
                    NekoConfig.regramQuickContrast.value = 125;
                    check(QuickTheme.beginResolve());
                    check(!QuickTheme.beginResolve());
                    QuickTheme.endResolve(false);
                    check(!QuickTheme.beginResolve());
                    QuickTheme.endResolve(true);
                    check(QuickTheme.beginResolve());
                    QuickTheme.endResolve(true);
                    NekoConfig.regramQuickContrast.value = 125;
                    NekoConfig.regramQuickSaturation.value = 140;
                    NekoConfig.regramQuickBrightness.value = 8;
                    int key = Theme.key_chat_outBubble;
                    int expected = QuickTheme.transform(0x9f63a5d7,125,140,8);
                    check(Theme.getColor(key) == expected);
                    MessageColorProbe global = new MessageColorProbe(null);
                    MessageColorProbe local = new MessageColorProbe(Theme::getColor);
                    check(global.color(key) == expected && global.current(key) == expected);
                    check(local.color(key) == expected && local.current(key) == expected);
                    check(local.current(Theme.key_chat_outBubbleGradient1) == expected);
                    check(Theme.getColor(key, (Theme.ResourcesProvider)null) == expected);
                    check(Theme.getColor(key, (Theme.ResourcesProvider)Theme::getColor) == expected);
                    check(Theme.getColor(key, (Theme.ResourcesProvider)k -> Theme.getColor(k,
                          (Theme.ResourcesProvider)Theme::getColor)) == expected);
                    try { Theme.getColor(key, (Theme.ResourcesProvider)k -> {throw new RuntimeException();}); }
                    catch (RuntimeException expectedException) {}
                    check(Theme.getColor(key) == expected);
                    check(QuickTheme.adjust(9999, 1) == 1);
                    check(QuickTheme.adjust(key, 0) == 0);
                    java.util.Random random = new java.util.Random(1161);
                    for (int n = 0; n < 20000; n++) {
                        int color = random.nextInt() | 0x01000000;
                        int contrast = random.nextInt(240) - 40;
                        int saturation = random.nextInt(300) - 40;
                        int brightness = random.nextInt(100) - 50;
                        NekoConfig.regramQuickContrast.value = contrast;
                        NekoConfig.regramQuickSaturation.value = saturation;
                        NekoConfig.regramQuickBrightness.value = brightness;
                        int transformed = QuickTheme.transform(color, contrast, saturation, brightness);
                        check(QuickTheme.adjust(key, color) == transformed);
                        check(QuickTheme.adjust(key, color) == transformed); // Cache hit.
                        check(QuickTheme.adjust(key, color ^ 0x00010001) ==
                              QuickTheme.transform(color ^ 0x00010001, contrast, saturation, brightness));
                    }
                    NekoConfig.regramQuickContrast.value = 145;
                    NekoConfig.regramQuickSaturation.value = 180;
                    NekoConfig.regramQuickBrightness.value = -10;
                    java.util.concurrent.atomic.AtomicReference<Throwable> failure =
                        new java.util.concurrent.atomic.AtomicReference<>();
                    Thread[] workers = new Thread[4];
                    for (int i = 0; i < workers.length; i++) {
                        final int id = i;
                        workers[i] = new Thread(() -> {
                            try {
                                java.util.Random inputs = new java.util.Random(id);
                                for (int n = 0; n < 5000; n++) {
                                    int color = inputs.nextInt() | 0xff000000;
                                    check(QuickTheme.adjust(key, color) == QuickTheme.transform(color,145,180,-10));
                                    check(QuickTheme.adjust(key, color) == QuickTheme.transform(color,145,180,-10));
                                }
                            } catch (Throwable t) { failure.set(t); }
                        });
                        workers[i].start();
                    }
                    for (Thread worker : workers) {
                        try { worker.join(); } catch (InterruptedException e) { throw new AssertionError(e); }
                    }
                    check(failure.get() == null);
                    QuickTheme.reset();
                    check(Theme.getColor(key) == 0x9f63a5d7);
                    check(QuickTheme.bubbleAlpha() == 255);
                    check(NekoConfig.regramQuickContrast.Int() == 100);
                    check(NekoConfig.regramQuickSaturation.Int() == 100);
                    check(NekoConfig.regramQuickBrightness.Int() == 0);
                }
            }''',
    }
    files = []
    for name, text in sources.items():
        path = tmp_path / name
        path.parent.mkdir(parents=True, exist_ok=True)
        path.write_text(text)
        files.append(str(path))
    subprocess.run([javac, '-d', str(tmp_path), *files], check=True, capture_output=True, text=True)
    subprocess.run([java, '-cp', str(tmp_path), 'CheckQuickTheme'], check=True, capture_output=True, text=True)
