"""Retained Chip Folders math/config and removal contracts; not device tests."""
from pathlib import Path
import shutil
import subprocess
import xml.etree.ElementTree as ET
import pytest

ROOT = Path(__file__).resolve().parents[1]
JAVA = ROOT / 'TMessagesProj/src/main/java'


@pytest.fixture(scope='module')
def classes(tmp_path_factory):
    if not shutil.which('javac') or not shutil.which('java'):
        pytest.skip('JDK unavailable')
    out = tmp_path_factory.mktemp('chip-folders')
    sources = {
        'android/content/SharedPreferences.java': 'package android.content; public interface SharedPreferences { java.util.Map<String,?> getAll(); }',
        'android/content/Context.java': '''package android.content; public class Context {
            public int reads;public java.util.Map<String,Object> data=new java.util.HashMap<>();
            public SharedPreferences getSharedPreferences(String name,int mode){assert name.equals("regram_native_styles");return ()->{reads++;return new java.util.HashMap<>(data);};}
        }''',
        'org/telegram/messenger/ApplicationLoader.java': 'package org.telegram.messenger; public class ApplicationLoader {public static android.content.Context applicationContext=new android.content.Context();}',
        'org/telegram/messenger/UserConfig.java': 'package org.telegram.messenger; public class UserConfig {public boolean showCallsTab=true;public static UserConfig getInstance(int i){return new UserConfig();}}',
        'org/telegram/ui/MainTabsActivity.java': 'package org.telegram.ui; public class MainTabsActivity {public static final int TABS_COUNT=5;}',
        'app/regram/appearance/AppearanceConfig.java': 'package app.regram.appearance; public class AppearanceConfig {public static boolean showFeedTab(){return false;}}',
        'app/regram/appearance/MainTabsUiHelper.java': 'package app.regram.appearance; public class MainTabsUiHelper {public static boolean isIosNavigationBar(){return false;}}',
        'xyz/nextalone/nagram/NaConfig.java': '''package xyz.nextalone.nagram; public class NaConfig {
            public static NaConfig INSTANCE=new NaConfig();public static class Flag {public boolean value;public boolean Bool(){return value;}}
            public Flag titles=new Flag(),contacts=new Flag(),calls=new Flag(),profile=new Flag();
            public Flag getMainTabsHideTitles(){return titles;}public Flag getMainTabsHideContacts(){return contacts;}
            public Flag getMainTabsHideCallsSettings(){return calls;}public Flag getMainTabsHideProfile(){return profile;}
        }''',
        'StyleTest.java': '''import app.regram.appearance.*;import tw.nekomimi.nekogram.helpers.MainTabsHelper;
        import org.telegram.messenger.ApplicationLoader;import xyz.nextalone.nagram.NaConfig;
        public class StyleTest {
            static void put(String k,Object v){ApplicationLoader.applicationContext.data.put(k,v);PluginStyles.invalidate();}
            public static void main(String[] args){
                switch(args[0]){
                case "defaults":
                    assert !PluginStyles.chips() && PluginStyles.barHeight()==44 && PluginStyles.gap()==6;
                    assert MainTabsHelper.getChatsPosition()==0 && MainTabsHelper.getContactsPosition()==1;
                    assert MainTabsHelper.getCallsOrSettingsPosition()==2 && MainTabsHelper.getProfilePosition()==3;
                    assert MainTabsHelper.getFragmentsCount()==5 && MainTabsHelper.getMainTabsHeight()==56;
                    NaConfig.INSTANCE.contacts.value=true;assert MainTabsHelper.getFragmentsCount()==4;
                    NaConfig.INSTANCE.profile.value=true;assert MainTabsHelper.getFragmentsCount()==3;
                    break;
                case "legacy":
                    for(String key:new String[]{"expressive","compact","hide_labels","search_button","hide_header_search","weex_md3_nav","weex_palette","font_enabled","profile_chips"})put(key,true);
                    put("panel_scale",125);put("sections","settings,feed,chats,profile");
                    assert !PluginStyles.chips() && PluginStyles.barHeight()==44;
                    assert MainTabsHelper.getChatsPosition()==0 && MainTabsHelper.getFragmentsCount()==5;
                    assert MainTabsHelper.getMainTabsHeight()==56 && !MainTabsHelper.isMainTabsHideTitleStyle();
                    assert !MainTabsHelper.isFeedTabShown() && !MainTabsHelper.isContactsTabHidden();
                    NaConfig.INSTANCE.titles.value=true;assert MainTabsHelper.getMainTabsHeight()==36;
                    put("chips",true);put("chip_style",2);put("chip_shape",7);
                    assert PluginStyles.chips() && PluginStyles.number("chip_style",0,0,2)==2;
                    assert PluginStyles.number("chip_shape",5,0,7)==7; // original storage retained
                    break;
                case "cache":
                    put("chips",true);assert PluginStyles.chips();int reads=ApplicationLoader.applicationContext.reads;
                    for(int i=0;i<100000;i++){assert PluginStyles.chips();PluginStyles.barHeight();PluginStyles.gap();}
                    assert ApplicationLoader.applicationContext.reads==reads;
                    put("chips",false);assert !PluginStyles.chips();break;
                case "clamp":
                    put("chips",true);put("chip_size",3);put("chip_height",999);assert PluginStyles.barHeight()==64;
                    put("chip_height",0);assert PluginStyles.barHeight()==36;
                    put("chip_height","bad");assert PluginStyles.barHeight()==48;
                    put("chip_spacing",3);put("chip_gap",999);assert PluginStyles.gap()==24;
                    put("chip_gap",-1);assert PluginStyles.gap()==0;
                    put("chips",false);assert PluginStyles.barHeight()==44;break;
                case "animations":
                    for(int anim=0;anim<=4;anim++)for(int speed:new int[]{0,50,100,200,999})for(int i=0;i<=100;i++){
                        float p=i/100f,x=PluginStyleRules.scaleX(anim,speed,p),y=PluginStyleRules.scaleY(anim,speed,p);
                        assert Float.isFinite(x)&&Float.isFinite(y)&&x>=.87f&&x<=1.13f&&y>=.87f&&y<=1.13f;
                        float wave=PluginStyleRules.wave(anim,speed,p);
                        assert x==PluginStyleRules.scaleXFromWave(anim,wave) && y==PluginStyleRules.scaleYFromWave(anim,wave);
                        if(i==0||i==100||anim==0)assert x==1&&y==1;
                    }
                    assert PluginStyleRules.unit(Float.NaN)==0;break;
                case "radii":
                    for(int shape=0;shape<8;shape++)for(int i=0;i<=100;i++){
                        float radius=PluginStyleRules.chipRadius(shape,i/100f,36,20,8);assert radius>=0&&radius<=18;
                    }
                    assert PluginStyleRules.chipRadius(4,0,36,20,8)==18;
                    assert PluginStyleRules.chipRadius(4,1,36,20,8)==11;
                    assert PluginStyleRules.chipRadius(7,0,36,11,8)==8;
                    assert PluginStyleRules.chipRadius(7,1,36,11,8)==11;break;
                default:throw new AssertionError(args[0]);
                }
            }
        }''',
    }
    for name in ('app/regram/appearance/PluginStyleRules.java', 'app/regram/appearance/PluginStyles.java', 'tw/nekomimi/nekogram/helpers/MainTabsHelper.java'):
        sources[name] = (JAVA/name).read_text()
    paths = []
    for name, text in sources.items():
        path = out/name;path.parent.mkdir(parents=True, exist_ok=True);path.write_text(text);paths.append(str(path))
    subprocess.run(['javac', '-d', str(out), *paths], check=True, capture_output=True, text=True)
    return out


@pytest.mark.parametrize('case', ['defaults', 'legacy', 'cache', 'clamp', 'animations', 'radii'])
def test_actual_chip_rules_and_stock_routes(classes, case):
    subprocess.run(['java', '-ea', '-cp', str(classes), 'StyleTest', case], check=True, capture_output=True, text=True, timeout=20)


def test_removed_features_have_no_implementation_or_call_sites():
    removed = ('WeexColors', 'WeexFont', 'NativeStylePresets', 'NativeFabStyle', 'NativePluginStylesActivity')
    sources = list(JAVA.rglob('*.java'))
    for name in removed:
        assert not any(path.stem == name for path in sources)
    for path in sources:
        text = path.read_text()
        for name in removed:
            assert name not in text, path
    assert not (ROOT/'TMessagesProj/src/main/assets/regram/weex_palette.json').exists()
    assert not (ROOT/'tools/import_weex_palette.py').exists()
    for locale in ('values', 'values-ru'):
        assert not (ROOT/f'TMessagesProj/src/main/res/{locale}/regram_plugin_styles.xml').exists()


def test_chips_keep_renderer_and_settings_inside_native_appearance():
    visual = (JAVA/'app/regram/settings/NativeVisualSettingsActivity.java').read_text()
    assert 'addRow("chipFolders", "RegramChipFolders")' in visual
    assert 'presentFragment(new ChipFoldersActivity())' in visual
    picker = (JAVA/'app/regram/settings/ChipFoldersActivity.java').read_text()
    for key in ('chips', 'chip_style', 'chip_md3', 'chip_active', 'chip_inactive', 'text_active', 'text_inactive',
                'chip_shape', 'radius_active', 'radius_outer', 'radius_inner', 'radius_alt_active', 'radius_alt_outer',
                'radius_custom_active', 'radius_inactive', 'chip_animation', 'chip_speed', 'chip_size', 'chip_height',
                'chip_spacing', 'chip_gap', 'chip_bottom', 'chip_list_gap', 'chip_divider'):
        assert f'"{key}"' in picker
    assert 'ColorPickerBottomSheet' in picker and 'FoldersPreviewCell' in picker
    assert 'ACTION_OPEN_DOCUMENT' not in picker
    folders = (JAVA/'org/telegram/ui/Components/FilterTabsView.java').read_text()
    for route in ('nativeFolderScope &&', 'nativeProgress(currentTab.id)', 'onChipSettingsRequested', 'setNativeFolderChips', 'nativeOriginalTypeface', 'canvas.restoreToCount(nativeSave)'):
        assert route in folders
    dialogs = (JAVA/'org/telegram/ui/DialogsActivity.java').read_text()
    assert 'new app.regram.settings.ChipFoldersActivity()' in dialogs
    for locale in ('values', 'values-ru'):
        root = ET.parse(ROOT/f'TMessagesProj/src/main/res/{locale}/regram_chip_folders.xml').getroot()
        assert {item.get('name') for item in root} == {'RegramChipFolders', 'RegramChipFoldersInfo'}
