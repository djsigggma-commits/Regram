from pathlib import Path
import re
from test_native_visual_settings import compile_run

ROOT = Path(__file__).resolve().parents[1]
JAVA = ROOT / 'TMessagesProj/src/main/java'


def test_actual_player_color_policy(tmp_path):
    files = {
        'app/regram/player/PlayerColorOverride.java': (JAVA / 'app/regram/player/PlayerColorOverride.java').read_text(),
        'tw/nekomimi/nekogram/NekoConfig.java': '''package tw.nekomimi.nekogram; public class NekoConfig {
 public static class Item {boolean b;int i;String s;public boolean Bool(){return b;}public int Int(){return i;}public String String(){return s;}}
 public static Item regramPlayerColor=new Item(),regramPlayerColorPreset=new Item(),regramPlayerColorHex=new Item();
 public static void set(boolean b,int i,String s){regramPlayerColor.b=b;regramPlayerColorPreset.i=i;regramPlayerColorHex.s=s;}
}''',
        'PlayerTest.java': '''import app.regram.player.PlayerColorOverride;import tw.nekomimi.nekogram.NekoConfig;
public class PlayerTest {public static void main(String[] args){
 assert PlayerColorOverride.parseHex(" #abc ")==0xffaabbcc;
 assert PlayerColorOverride.parseHex("E91E63")==0xffe91e63;
 for(String s:new String[]{"","#1234","#12345678","xyz","-12345"})assert PlayerColorOverride.parseHex(s)==null;
 NekoConfig.set(false,2,"#fff");assert PlayerColorOverride.apply(123)==123;
 int[] presets={0,0xfff44336,0xffe91e63,0xff9c27b0,0xff3f51b5,0xff2196f3,0xff00bcd4,0xff4caf50,0xffcddc39,0xffffc107,0xffff9800,0xff795548,0xff808080};
 for(int i=1;i<presets.length;i++){NekoConfig.set(true,i,"bad");assert PlayerColorOverride.apply(123)==presets[i];}
 NekoConfig.set(true,0,"#123");assert PlayerColorOverride.apply(123)==0xff112233;
 NekoConfig.set(true,-1,"#456");assert PlayerColorOverride.apply(123)==0xff445566;
 NekoConfig.set(true,0,"invalid");assert PlayerColorOverride.apply(123)==123;
 NekoConfig.set(true,0,"#123");assert PlayerColorOverride.apply(123)==0xff112233;
 NekoConfig.set(false,0,"#123");assert PlayerColorOverride.apply(123)==123;
}}''',
    }
    compile_run(tmp_path, files, 'PlayerTest')


def test_actual_dex_pill_contract_and_nested_transactions(tmp_path):
    registry = (JAVA / 'app/regram/pillstack/PillRegistry.java').read_text()
    files = {
        'app/regram/pillstack/PillRegistry.java': registry,
        'com/exteragram/messenger/pillstack/core/PillRegistry.java': (JAVA / 'com/exteragram/messenger/pillstack/core/PillRegistry.java').read_text(),
        'com/exteragram/messenger/pillstack/core/PillStackConfig.java': (JAVA / 'com/exteragram/messenger/pillstack/core/PillStackConfig.java').read_text(),
        'com/exteragram/messenger/pillstack/ui/pills/BasePill.java': (JAVA / 'com/exteragram/messenger/pillstack/ui/pills/BasePill.java').read_text(),
        'app/regram/plugins/xposed/PluginHookTargets.java': (JAVA / 'app/regram/plugins/xposed/PluginHookTargets.java').read_text(),
        'android/content/Context.java': 'package android.content;public class Context {}',
        'androidx/annotation/Keep.java': 'package androidx.annotation;public @interface Keep {}',
        'org/telegram/ui/ActionBar/Theme.java': 'package org.telegram.ui.ActionBar;public class Theme {public static class ResourcesProvider {}}',
        'org/telegram/messenger/FileLog.java': 'package org.telegram.messenger;public class FileLog {public static void e(Exception e){}}',
        'org/telegram/messenger/LocaleController.java': 'package org.telegram.messenger;public class LocaleController {public static String getString(int id){return "pill";}}',
        'app/regram/pillstack/PillStackEvents.java': 'package app.regram.pillstack;public class PillStackEvents {public static int updates;public static void notifyLayoutChanged(){updates++;}}',
        'app/regram/pillstack/PillStackConfig.java': '''package app.regram.pillstack;import java.util.ArrayList;
public class PillStackConfig {public static boolean loaded;public static int saves,sanitizes;
 static final ArrayList<Integer> active=new ArrayList<>(),hidden=new ArrayList<>();
 public static class Item{public int Int(){return 0;}}public static Item lastActivePillId=new Item();
 public static boolean isConfigLoaded(){return loaded;}public static void loadConfig(boolean force){loaded=true;}
 public static ArrayList<Integer> getActivePills(){return active;}public static ArrayList<Integer> getHiddenPills(){return hidden;}
 public static void setPillActive(int id,boolean b){active.add(id);}public static void sanitizePills(){sanitizes++;}
 public static void savePillsLayout(){saves++;}
}''',
        'app/regram/pillstack/pills/BasePill.java': '''package app.regram.pillstack.pills;import android.content.Context;import org.telegram.ui.ActionBar.Theme;
public abstract class BasePill {final Context ctx;public BasePill(Context c,Theme.ResourcesProvider r){ctx=c;}
 public Context getContext(){return ctx;}public void addView(Object v){}
 public abstract int getPillId();public abstract long getRefreshInterval();public abstract void onUpdateData(boolean b);
 public abstract void onPillClicked();public abstract boolean onPillLongClicked();public abstract void updateColors();
 public void onPillSelected(){}public void onPillUnselected(){}public void onStackVisibilityChanged(boolean b){}
}''',
        'PillTest.java': '''import java.lang.reflect.*;import android.content.Context;import org.telegram.ui.ActionBar.Theme;
import com.exteragram.messenger.pillstack.core.PillRegistry;import app.regram.plugins.xposed.PluginHookTargets;
public class PillTest {
 public static class PluginPill extends com.exteragram.messenger.pillstack.ui.pills.BasePill {
  PluginPill(){super(new Context(),null);}public int getPillId(){return 100;}public long getRefreshInterval(){return 10;}
  public void onUpdateData(boolean b){}public void onPillClicked(){}public boolean onPillLongClicked(){return false;}public void updateColors(){}
 }
 public static void unrelated(){}
 public static void main(String[] args)throws Exception {
  assert PillRegistry.PillCreator.class.isInterface();PluginPill view=new PluginPill();
  PillRegistry.PillCreator factory=(c,r)->view;
  PillRegistry.PillInfo info=new PillRegistry.PillInfo(100,"plugin",1,2,3,factory);
  assert info.creator()==factory && info.id()==100;
  PillRegistry.isRegistered(100);app.regram.pillstack.PillStackConfig.loaded=true;
  app.regram.pillstack.PillStackEvents.updates=0;app.regram.pillstack.PillStackConfig.sanitizes=0;
  PillRegistry.beginTransaction();PillRegistry.beginTransaction();PillRegistry.register(info);
  PillRegistry.endTransaction();assert app.regram.pillstack.PillStackEvents.updates==0;
  PillRegistry.endTransaction();assert app.regram.pillstack.PillStackEvents.updates==1;
  assert app.regram.pillstack.PillStackConfig.sanitizes==1;
  assert app.regram.pillstack.PillRegistry.createPill(100,new Context(),null)==view;
  assert PillRegistry.getPillInfo(100).creator().create(new Context(),null)==view;
  PillRegistry.beginTransaction();PillRegistry.endTransaction();PillRegistry.endTransaction();
  assert app.regram.pillstack.PillStackEvents.updates==1;
  assert com.exteragram.messenger.pillstack.core.PillStackConfig.activePills==app.regram.pillstack.PillStackConfig.getActivePills();
  assert com.exteragram.messenger.pillstack.core.PillStackConfig.hiddenPills==app.regram.pillstack.PillStackConfig.getHiddenPills();
  com.exteragram.messenger.pillstack.core.PillStackConfig.activePills.add(100);
  assert app.regram.pillstack.PillStackConfig.getActivePills().contains(100);
  com.exteragram.messenger.pillstack.core.PillStackConfig.savePillsLayout();assert app.regram.pillstack.PillStackConfig.saves==1;
  Method shim=com.exteragram.messenger.pillstack.core.PillStackConfig.class.getDeclaredMethod("savePillsLayout");
  Member target=PluginHookTargets.resolve(shim);assert target.getDeclaringClass()==app.regram.pillstack.PillStackConfig.class;
  Method unrelated=PillTest.class.getDeclaredMethod("unrelated");assert PluginHookTargets.resolve(unrelated)==unrelated;
  Constructor<?> constructor=PluginPill.class.getDeclaredConstructor();assert PluginHookTargets.resolve(constructor)==constructor;
  PillRegistry.unregister(100);assert !PillRegistry.isRegistered(100);
 }
}''',
    }
    r = 'package org.telegram.messenger;public class R {'
    for kind in ('string', 'drawable'):
        r += 'public static class '+kind+' {' + ''.join('public static int '+name+';' for name in set(re.findall(r'R\.'+kind+r'\.(\w+)', registry))) + '}'
    files['org/telegram/messenger/R.java'] = r + '}'
    colors = set(re.findall(r'IconBackgroundColors\.(\w+)\.top', registry))
    files['org/telegram/ui/Components/IconBackgroundColors.java'] = 'package org.telegram.ui.Components;public enum IconBackgroundColors {'+','.join(colors)+';public int top,bottom;}'
    types = set(re.findall(r'PillType\.(\w+)\.id', registry))
    files['app/regram/pillstack/PillType.java'] = 'package app.regram.pillstack;public enum PillType {'+','.join(types)+';public int id=ordinal();}'
    for name in re.findall(r'import app.regram.pillstack.pills.(\w+);', registry):
        if name == 'BasePill':
            continue
        files['app/regram/pillstack/pills/'+name+'.java'] = 'package app.regram.pillstack.pills;import android.content.Context;import org.telegram.ui.ActionBar.Theme;public class '+name+' extends BasePill {public '+name+'(Context c,Theme.ResourcesProvider r){super(c,r);}public int getPillId(){return 0;}public long getRefreshInterval(){return 0;}public void onUpdateData(boolean b){}public void onPillClicked(){}public boolean onPillLongClicked(){return false;}public void updateColors(){}}'
    compile_run(tmp_path, files, 'PillTest')


def test_native_integration_and_narrow_hook_redirect():
    colors = (JAVA / 'app/regram/player/PlayerColors.java').read_text()
    assert colors.index('seed = PlayerColorOverride.apply(seed)') < colors.index('long key =')
    assert colors.count('PlayerColorOverride.currentColor()') == 3
    gate = (JAVA / 'app/regram/plugins/xposed/HookGate.java').read_text()
    assert 'param.args[0] = target;' in gate and 'prewarm(target)' in gate
    helper = (JAVA / 'app/regram/player/PlayerColorOverride.java').read_text()
    assert 'Xposed' not in helper and 'BasePlugin' not in helper
