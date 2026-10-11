"""Portable settings profile and first-run/default/reset policy."""
import json
from pathlib import Path
import shutil
import subprocess

import pytest

ROOT = Path(__file__).resolve().parents[1]
JAVA = ROOT / 'TMessagesProj/src/main/java'
ASSET = ROOT / 'TMessagesProj/src/main/assets/regram/default-settings.json'


def test_profile_contains_selected_settings_but_no_account_or_cache_data():
    profile = json.loads(ASSET.read_text())
    assert set(profile) == {'userconfing', 'mainconfig', 'themeconfig', 'nkmrcfg'}
    assert profile['mainconfig']['theme'] == 'Monet Dark'
    assert profile['mainconfig']['selectedAutoNightType'] == 0
    assert profile['mainconfig']['useThreeLinesLayout'] is True
    config = profile['nkmrcfg']
    assert config['SwitchStyle'] == config['SliderStyle'] == 2
    assert config['OEAppearanceAvatarCorners'] == 14
    assert config['OEAppearanceSectionRadius'] == 20
    assert config['OEPillActivePills'] == '5,6,1,7'
    assert config['OEIconPacksActive'] == 'hu,materialexpressiveicons'
    assert config['EnableSaveDeletedMessages'] is True
    assert config['EnableSaveEditsHistory'] is True
    assert config['RegramAllLeft'] is config['RegramMonoColor'] is False
    assert config['stickerSize_float'] == 14
    for values in profile.values():
        for key, value in values.items():
            assert not key.startswith(('userNameOverride_', 'chatNameOverride_', 'accents_'))
            assert not key.startswith(('lastLoading', '2remoteThemesHash'))
            assert key not in {'themes2', 'lastKeepMediaCheckTime', 'OELegacyDefaultsGeneration',
                               'OELegacyDefaultsPinned', 'OEIconPacksEditing'}
            assert '/data/' not in str(value)
            assert isinstance(value, (str, bool, int, float))


def test_profile_is_applied_before_config_load_and_used_for_item_defaults():
    loader = (JAVA / 'org/telegram/messenger/ApplicationLoader.java').read_text()
    attach = loader[loader.index('protected void attachBaseContext('):
                    loader.index('public static ILocationServiceProvider')]
    assert 'BundledDefaults.initializeForFreshInstall(applicationContext)' in attach
    config = (JAVA / 'tw/nekomimi/nekogram/config/ConfigItem.java').read_text()
    assert 'this.defaultValue = app.regram.config.BundledDefaults.configDefault(key, type, defaultValue)' in config
    assert 'this.value = this.defaultValue;' in config


def test_first_install_existing_install_and_typed_defaults(tmp_path):
    javac, java = shutil.which('javac'), shutil.which('java')
    jars = list((Path.home() / '.gradle/caches/modules-2/files-2.1/org.json/json').glob('*/*/*.jar'))
    if not javac or not java or not jars:
        pytest.skip('JDK and local org.json jar required')
    jar = jars[0]
    sources = {
        'app/regram/config/BundledDefaults.java':
            (JAVA / 'app/regram/config/BundledDefaults.java').read_text(),
        'android/os/Build.java': '''package android.os;
            public class Build {
                public static class VERSION { public static int SDK_INT = 31; }
                public static class VERSION_CODES { public static final int S = 31; }
            }''',
        'android/content/SharedPreferences.java': '''package android.content;
            import java.util.*;
            public class SharedPreferences {
                public final Map<String,Object> values = new HashMap<>();
                public Map<String,Object> getAll() { return values; }
                public boolean contains(String key) { return values.containsKey(key); }
                public Editor edit() { return new Editor(); }
                public class Editor {
                    private final Map<String,Object> pending = new HashMap<>();
                    public Editor putBoolean(String k, boolean v) { pending.put(k,v); return this; }
                    public Editor putInt(String k, int v) { pending.put(k,v); return this; }
                    public Editor putLong(String k, long v) { pending.put(k,v); return this; }
                    public Editor putFloat(String k, float v) { pending.put(k,v); return this; }
                    public Editor putString(String k, String v) { pending.put(k,v); return this; }
                    public boolean commit() { values.putAll(pending); return true; }
                }
            }''',
        'android/content/res/AssetManager.java': '''package android.content.res;
            import java.io.*;
            public class AssetManager {
                public String path;
                public InputStream open(String name) throws IOException {
                    if (!name.equals("regram/default-settings.json")) throw new AssertionError(name);
                    return new FileInputStream(path);
                }
            }''',
        'android/content/Context.java': '''package android.content;
            import java.util.*;
            public class Context {
                public static final int MODE_PRIVATE = 0;
                public final Map<String,SharedPreferences> prefs = new HashMap<>();
                public final android.content.res.AssetManager assets = new android.content.res.AssetManager();
                public SharedPreferences getSharedPreferences(String name, int mode) {
                    return prefs.computeIfAbsent(name, k -> new SharedPreferences());
                }
                public android.content.res.AssetManager getAssets() { return assets; }
            }''',
        'org/telegram/messenger/ApplicationLoader.java': '''package org.telegram.messenger;
            public class ApplicationLoader { public static android.content.Context applicationContext; }''',
        'org/telegram/messenger/FileLog.java': '''package org.telegram.messenger;
            public class FileLog {
                public static void e(Exception e) { throw new AssertionError(e); }
                public static void d(String s) { throw new AssertionError(s); }
            }''',
        'tw/nekomimi/nekogram/config/ConfigItem.java': '''package tw.nekomimi.nekogram.config;
            public class ConfigItem {
                public static final int configTypeBool=0, configTypeInt=1, configTypeString=2,
                    configTypeSetInt=3, configTypeMapIntInt=4, configTypeLong=5, configTypeFloat=6;
            }''',
        'CheckDefaults.java': '''import android.content.*;
            import app.regram.config.BundledDefaults;
            import org.telegram.messenger.ApplicationLoader;
            public class CheckDefaults {
                static Object value(Context c, String section, String key) {
                    return c.getSharedPreferences(section,0).getAll().get(key);
                }
                static void check(boolean condition) { if (!condition) throw new AssertionError(); }
                public static void main(String[] args) {
                    // Context not yet attached: keep original defaults and allow retry later.
                    check(BundledDefaults.configDefault("SliderStyle",1,0).equals(0));
                    BundledDefaults.initializeForFreshInstall(null);
                    for (String section : new String[]{"mainconfig","nkmrcfg","userconfing","themeconfig"}) {
                        Context existing = new Context();
                        existing.assets.path = args[0];
                        existing.getSharedPreferences(section,0).edit().putString("existing", "keep").commit();
                        BundledDefaults.initializeForFreshInstall(existing);
                        check(existing.prefs.values().stream().mapToInt(p -> p.getAll().size()).sum() == 1);
                    }
                    Context fresh = new Context();
                    fresh.assets.path = args[0];
                    ApplicationLoader.applicationContext = fresh;
                    BundledDefaults.initializeForFreshInstall(fresh);
                    check(value(fresh,"mainconfig","theme").equals("Monet Dark"));
                    check(value(fresh,"mainconfig","autoNightBrighnessThreshold") instanceof Float);
                    check(value(fresh,"mainconfig","autoNightBrighnessThreshold").equals(0.25f));
                    check(value(fresh,"nkmrcfg","stickerSize") instanceof Float);
                    check(value(fresh,"nkmrcfg","stickerSize").equals(14.0f));
                    check(value(fresh,"nkmrcfg","SliderStyle") instanceof Integer);
                    check(value(fresh,"nkmrcfg","EnableSaveDeletedMessages").equals(true));
                    fresh.getSharedPreferences("nkmrcfg",0).edit().putInt("SliderStyle",0).commit();
                    BundledDefaults.initializeForFreshInstall(fresh);
                    check(value(fresh,"nkmrcfg","SliderStyle").equals(0));
                    // Default lookup is separate from persisted user values.
                    check(BundledDefaults.configDefault("SliderStyle",1,0).equals(2));
                    check(BundledDefaults.configDefault("stickerSize",6,0f).equals(14f));
                    check(BundledDefaults.configDefault("EnableSaveDeletedMessages",0,false).equals(true));
                    check(BundledDefaults.configDefault("OEPillActivePills",2,"").equals("5,6,1,7"));
                    check(BundledDefaults.configDefault("missing",5,123L).equals(123L));
                    check(BundledDefaults.configDefault("SliderStyle",0,false).equals(false));
                    android.os.Build.VERSION.SDK_INT = 30;
                    Context oldAndroid = new Context();
                    oldAndroid.assets.path = args[0];
                    BundledDefaults.initializeForFreshInstall(oldAndroid);
                    check(value(oldAndroid,"mainconfig","theme").equals("Night"));
                    check(value(oldAndroid,"themeconfig","lastDayTheme").equals("Blue"));
                    Object set = new Object();
                    check(BundledDefaults.configDefault("SliderStyle",3,set) == set);
                }
            }''',
    }
    files = []
    for name, text in sources.items():
        path = tmp_path / name
        path.parent.mkdir(parents=True, exist_ok=True)
        path.write_text(text)
        files.append(str(path))
    subprocess.run([javac, '-cp', str(jar), '-d', str(tmp_path), *files],
                   check=True, capture_output=True, text=True)
    subprocess.run([java, '-cp', f'{tmp_path}:{jar}', 'CheckDefaults', str(ASSET)],
                   check=True, capture_output=True, text=True)
