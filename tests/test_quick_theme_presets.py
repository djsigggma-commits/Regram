"""Host tests for complete named snapshots, reset, and corrupted/empty slots."""
from pathlib import Path
import re
import shutil
import subprocess

import pytest

ROOT = Path(__file__).resolve().parents[1]
JAVA = ROOT / 'TMessagesProj/src/main/java'


def test_extended_controls_are_wired_to_renderers():
    settings = (JAVA / 'app/regram/settings/QuickThemeActivity.java').read_text()
    assert 'QuickTheme.bubbleAlpha(outgoing)' in settings
    assert 'QuickTheme.bubbleRadius(16)' in settings
    message = (JAVA / 'org/telegram/ui/ActionBar/MessageDrawable.java').read_text()
    assert 'QuickTheme.bubbleAlpha(isOut)' in message
    assert message.count('QuickTheme.bubbleRadius(SharedConfig.bubbleRadius)') == 4
    for path, surface in [('org/telegram/ui/MainTabsLayout.java', 'navigation'),
                          ('org/telegram/ui/Components/FilterTabsView.java', 'folder')]:
        source = (JAVA / path).read_text()
        assert f'QuickTheme.{surface}Color(0)' in source
        assert f'QuickTheme.{surface}Radius(' in source
        assert 'setQuickThemeRadius(' in source
    blur = (JAVA / 'org/telegram/ui/Components/blur3/DownscaleScrollableNoiseSuppressor.java').read_text()
    assert 'sourcePart.updateQuickBlur()' in blur
    assert 'QuickTheme.blurPercent()' in blur
    assert 'if (quickBlurPercent == percent) return' in blur
    assert 'if (radius <= 0) { setPrimaryEffect(null); return; }' in blur
    assert 'if (radius <= 0) { setPrimaryEffect(secondEffect); return; }' in blur


def test_real_preset_roundtrip_and_failure_is_atomic(tmp_path):
    jars = list((Path.home() / '.gradle/caches/modules-2/files-2.1/org.json/json').glob('*/*/json-*.jar'))
    if not shutil.which('javac') or not jars:
        pytest.skip('JDK and org.json host library required')
    policy = (JAVA / 'app/regram/appearance/QuickTheme.java').read_text()
    presets = (JAVA / 'app/regram/appearance/QuickThemePresets.java').read_text()
    config = (JAVA / 'tw/nekomimi/nekogram/NekoConfig.java').read_text()
    options = re.findall(r'public static ConfigItem (regramQuick\w+) = addConfig\("(\w+)", configTypeInt, (-?\d+)\)', config)
    fields = '\n'.join(f'public static ConfigItem {field} = new ConfigItem("{key}",{value});' for field, key, value in options)
    # Every quick-theme integer option must participate in save/load and reset.
    for field, _, _ in options:
        assert f'NekoConfig.{field}' in presets
        assert f'NekoConfig.{field}.setConfigInt(' in policy
    keys = sorted(set(re.findall(r'Theme\.(key_\w+)', policy)))
    sources = {
        'app/regram/appearance/QuickTheme.java': policy,
        'app/regram/appearance/QuickThemePresets.java': presets,
        'org/telegram/ui/ActionBar/Theme.java': 'package org.telegram.ui.ActionBar; public class Theme {' + ''.join(
            f'public static final int {key}={i};' for i, key in enumerate(keys)) + '}',
        'tw/nekomimi/nekogram/config/ConfigItem.java': '''package tw.nekomimi.nekogram.config;
            public class ConfigItem {
                public final String key; public int value;
                public ConfigItem(String k,int v){key=k;value=v;}
                public int Int(){return value;} public boolean Bool(){return value!=0;}
                public void setConfigInt(int v){value=v;} public void setConfigBool(boolean b){value=b?1:0;}
            }''',
        'tw/nekomimi/nekogram/NekoConfig.java': '''package tw.nekomimi.nekogram;
            import tw.nekomimi.nekogram.config.ConfigItem;
            public class NekoConfig {
                public static ConfigItem regramQuickTheme=new ConfigItem("enabled",0);
                public static ConfigItem regramQuickHideNavigationBackdrop=new ConfigItem("RegramQuickHideNavigationBackdrop",0);
            ''' + fields + '''
                private static final Prefs prefs=new Prefs();
                public static Prefs getPreferences(){return prefs;}
                public static class Prefs {
                    private final java.util.Map<String,String> data=new java.util.HashMap<>();
                    public String getString(String k,String d){return data.getOrDefault(k,d);}
                    public Editor edit(){return new Editor();}
                    public class Editor {
                        public Editor putString(String k,String v){data.put(k,v);return this;}
                        public Editor remove(String k){data.remove(k);return this;}
                        public void apply(){}
                    }
                }
            }''',
        'CheckPresets.java': '''import app.regram.appearance.*;
            import tw.nekomimi.nekogram.NekoConfig;
            import tw.nekomimi.nekogram.config.ConfigItem;
            public class CheckPresets {
                static void check(boolean value){if(!value)throw new AssertionError();}
                public static void main(String[] args) {
                    check(!QuickThemePresets.load(0));
                    ConfigItem[] items=QuickThemePresets.items();
                    for(int i=0;i<items.length;i++)items[i].setConfigInt(i*7-1);
                    NekoConfig.regramQuickHideNavigationBackdrop.setConfigBool(true);
                    check(QuickThemePresets.save(0,"  Personal  "));
                    check(QuickThemePresets.name(0).equals("Personal"));
                    QuickTheme.reset();
                    check(!NekoConfig.regramQuickTheme.Bool());
                    check(QuickThemePresets.load(0));
                    check(NekoConfig.regramQuickTheme.Bool());
                    check(NekoConfig.regramQuickHideNavigationBackdrop.Bool());
                    for(int i=0;i<items.length;i++)check(items[i].Int()==i*7-1);
                    org.json.JSONObject legacy=new org.json.JSONObject(NekoConfig.getPreferences().getString("RegramQuickPreset0",""));
                    legacy.remove("RegramQuickHideNavigationBackdrop");
                    NekoConfig.getPreferences().edit().putString("RegramQuickPreset2",legacy.toString()).apply();
                    check(QuickThemePresets.load(2));
                    check(!NekoConfig.regramQuickHideNavigationBackdrop.Bool());
                    check(!QuickThemePresets.load(1));
                    NekoConfig.getPreferences().edit().putString("RegramQuickPreset1","{}").apply();
                    check(!QuickThemePresets.load(1));
                    for(int i=0;i<items.length;i++)check(items[i].Int()==i*7-1);
                    NekoConfig.getPreferences().edit().putString("RegramQuickPreset1","broken json").apply();
                    check(!QuickThemePresets.load(1));
                    QuickThemePresets.builtIn(0);
                    check(NekoConfig.regramQuickBlur.Int()==140);
                    QuickThemePresets.builtIn(1);
                    check(NekoConfig.regramQuickBlur.Int()==100);
                    check(NekoConfig.regramQuickBubbleRadius.Int()==24);
                    QuickThemePresets.builtIn(2);
                    check(NekoConfig.regramQuickSaturation.Int()==135);
                    check(NekoConfig.regramQuickFolderRadius.Int()==12);
                    check(QuickThemePresets.load(0)); // Built-ins/reset preserve saved slots.
                    QuickThemePresets.delete(0);
                    check(QuickThemePresets.name(0).isEmpty());
                    check(!QuickThemePresets.load(0));
                    try {QuickThemePresets.name(3);throw new AssertionError();}
                    catch(IllegalArgumentException expected){}
                }
            }''',
    }
    files = []
    for name, content in sources.items():
        path = tmp_path / name
        path.parent.mkdir(parents=True, exist_ok=True)
        path.write_text(content)
        files.append(str(path))
    jar = str(jars[0])
    subprocess.run(['javac', '-cp', jar, '-d', str(tmp_path), *files], check=True, capture_output=True, text=True)
    subprocess.run(['java', '-cp', f'{tmp_path}:{jar}', 'CheckPresets'], check=True, capture_output=True, text=True)
