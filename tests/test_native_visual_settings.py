"""Native policy tests; Android drawing still needs device testing."""
from pathlib import Path
import re
import shutil
import subprocess
import xml.etree.ElementTree as ET

import pytest

ROOT = Path(__file__).resolve().parents[1]
JAVA = ROOT / 'TMessagesProj/src/main/java'


def method(source, signature):
    start = source.index(signature)
    brace = source.index('{', start)
    depth = 1
    end = brace + 1
    while depth:
        depth += (source[end] == '{') - (source[end] == '}')
        end += 1
    return source[start:end]


def compile_run(tmp_path, sources, main):
    javac, java = shutil.which('javac'), shutil.which('java')
    if not javac or not java:
        pytest.skip('JDK unavailable')
    files = []
    for name, text in sources.items():
        path = tmp_path / name
        path.parent.mkdir(parents=True, exist_ok=True)
        path.write_text(text)
        files.append(str(path))
    classes = tmp_path / 'classes'
    classes.mkdir()
    subprocess.run([javac, '-d', str(classes), *files], check=True, capture_output=True, text=True)
    subprocess.run([java, '-ea', '-cp', str(classes), main], check=True, capture_output=True, text=True)


def test_native_controls_and_translations_are_complete():
    config = (JAVA / 'tw/nekomimi/nekogram/NekoConfig.java').read_text()
    controls = dict(re.findall(r'ConfigItem (regram(?:Mono|AllLeft)\w*) = addConfig\("(Regram\w+)"', config))
    assert len(controls) == 12
    settings = (JAVA / 'app/regram/settings/NativeVisualSettingsActivity.java').read_text()
    for field in controls:
        assert 'NekoConfig.' + field in settings
    for master in ('regramMonoColor', 'regramAllLeft'):
        assert re.search(r'ConfigItem ' + master + r'.*configTypeBool, false\)', config)
    english = ET.parse(ROOT / 'TMessagesProj/src/main/res/values/regram_native_visual.xml').getroot()
    russian = ET.parse(ROOT / 'TMessagesProj/src/main/res/values-ru/regram_native_visual.xml').getroot()
    en = {e.get('name') for e in english}
    assert en == {e.get('name') for e in russian}
    assert set(re.findall(r'R.string.(\w+)', settings)) - {'Save', 'Cancel'} <= en
    route = (JAVA / 'app/regram/settings/OpenExteraAppearanceActivity.java').read_text()
    assert 'presentFragment(new NativeVisualSettingsActivity())' in route


def test_native_policies_have_no_hooks_or_plugin_runtime():
    for name in ('appearance/MonoColor', 'chats/AllLeft', 'settings/NativeVisualSettingsActivity'):
        source = (JAVA / ('app/regram/' + name + '.java')).read_text()
        for forbidden in ('XC_MethodHook', 'XposedBridge', 'InMemoryDexClassLoader', 'java.lang.reflect',
                          'PluginsController', 'PythonPluginsEngine'):
            assert forbidden not in source
    theme = (JAVA / 'org/telegram/ui/ActionBar/Theme.java').read_text()
    assert theme.count('app.regram.appearance.MonoColor.override(key)') == 2
    avatar = (JAVA / 'org/telegram/ui/Components/AvatarDrawable.java').read_text()
    assert 'hasAdvancedGradient && advancedGradient != null && !monoAvatar' in avatar
    receiver = (JAVA / 'org/telegram/messenger/ImageReceiver.java').read_text()
    assert 'holder.colorFilter = visualColorFilter()' in receiver
    assert 'if (restoreFilter) drawable.setColorFilter(previous)' in receiver
    assert 'regramPeerAvatar = false' in method(receiver, 'public void setImageBitmap(Drawable bitmap, boolean notify)')


def test_real_ownership_sender_identity_and_layout_caches_are_separate():
    model = (JAVA / 'org/telegram/messenger/MessageObject.java').read_text()
    ownership = method(model, 'public boolean isOutOwner()')
    assert 'AllLeft' not in ownership and 'regram' not in ownership
    assert 'messageOwner.out' in ownership
    assert 'generatedWithAllLeft != app.regram.chats.AllLeft.active(this)' in model
    assert 'isOut = messageObject.isOutOwnerForLayout()' in model
    cell = (JAVA / 'org/telegram/ui/Cells/ChatMessageCell.java').read_text()
    identity = method(cell, 'private void updateCurrentUserAndChat()')
    assert '.isOutOwnerForLayout()' not in identity
    assert 'currentMessageObject.isOutOwner()' in identity
    assert '!currentMessageObject.isOutOwner() && currentMessageObject.getDialogId() >= 0 && !media.todo.others_can_complete' in cell
    assert cell.count('.isOutOwnerForLayout()') > 300
    assert 'timeWidth += dp(20)' in cell and 'drawAllLeftStatus(canvas, alpha, timeLayout)' in cell
    chat = (JAVA / 'org/telegram/ui/ChatActivity.java').read_text()
    assert 'groupedMessages.ensurePresentationLayout()' in chat
    assert 'nextMessage.isOutOwner() == message.isOutOwner()' in chat
    assert 'prevMessage.isOutOwner() == message.isOutOwner()' in chat


def test_allleft_policy_with_actual_java_methods(tmp_path):
    policy = (JAVA / 'app/regram/chats/AllLeft.java').read_text()
    extracted = '\n'.join(method(policy, 'public static boolean ' + name) for name in ('active(', 'privateChat(', 'statusVisible('))
    model = (JAVA / 'org/telegram/messenger/MessageObject.java').read_text()
    layout = method(model, 'public boolean isOutOwnerForLayout()').replace('app.regram.chats.', '')
    source = '''
class Flag { boolean value; Flag(boolean v) { value=v; } boolean Bool() { return value; } }
class NekoConfig {
 static Flag regramAllLeft=new Flag(false), regramAllLeftPrivate=new Flag(false), regramAllLeftStatus=new Flag(true);
}
class UserConfig {
 int account; static UserConfig getInstance(int a) { UserConfig c=new UserConfig(); c.account=a; return c; }
 long getClientUserId() { return account==0 ? 100 : 200; }
}
class DialogObject {
 static boolean isUserDialog(long d) { return d>0 && d<(1L<<32); }
 static boolean isEncryptedDialog(long d) { return d>=(1L<<32); }
}
class MessageObject {
 static final int TYPE_PHONE_CALL=16;
 Object messageOwner=new Object(); int currentAccount, type;
 boolean isSaved, sponsored, preview, previewForward, sendPreview, isRepostPreview, quick, notime, outgoing=true;
 long dialog=300;
 boolean isSponsored() { return sponsored; } boolean isQuickReply() { return quick; }
 long getDialogId() { return dialog; } boolean isOutOwner() { return outgoing; }
 LAYOUT
}
class AllLeft { METHODS }
public class PolicyTest {
 public static void main(String[] args) {
  MessageObject m=new MessageObject();
  assert m.isOutOwnerForLayout(); assert !AllLeft.active(m);
  NekoConfig.regramAllLeft.value=true;
  assert !m.isOutOwnerForLayout(); assert m.isOutOwner(); assert AllLeft.privateChat(m);
  assert AllLeft.statusVisible(m); NekoConfig.regramAllLeftStatus.value=false; assert !AllLeft.statusVisible(m);
  NekoConfig.regramAllLeftStatus.value=true; m.notime=true; assert !AllLeft.statusVisible(m); m.notime=false;
  m.outgoing=false; assert !m.isOutOwnerForLayout(); assert !AllLeft.statusVisible(m); m.outgoing=true;
  m.dialog=-10; NekoConfig.regramAllLeftPrivate.value=true;
  assert !AllLeft.active(m); assert m.isOutOwnerForLayout();
  m.dialog=1L<<32; assert AllLeft.active(m); assert AllLeft.privateChat(m);
  m.dialog=100; assert !AllLeft.active(m); m.currentAccount=1; assert AllLeft.active(m);
  m.dialog=200; assert !AllLeft.active(m); m.dialog=300;
  m.isSaved=true; assert !AllLeft.active(m); m.isSaved=false;
  m.sponsored=true; assert !AllLeft.active(m); m.sponsored=false;
  m.previewForward=true; assert !AllLeft.active(m); m.previewForward=false;
  m.preview=true; assert !AllLeft.active(m); m.preview=false;
  m.sendPreview=true; assert !AllLeft.active(m); m.sendPreview=false;
  m.quick=true; assert !AllLeft.active(m); m.quick=false;
  assert !AllLeft.active(null); m.messageOwner=null; assert !AllLeft.active(m);
 }
}
'''.replace('LAYOUT', layout).replace('METHODS', extracted)
    compile_run(tmp_path, {'PolicyTest.java': source}, 'PolicyTest')


def test_monocolor_actual_java_palette_and_reversible_gates(tmp_path):
    source = (JAVA / 'app/regram/appearance/MonoColor.java').read_text()
    keys = sorted(set(re.findall(r'Theme\.(key_\w+)', source)))
    fields = '\n'.join('public static final int %s=%d;' % (key, i+1) for i, key in enumerate(keys))
    arrays = '\n'.join('public static final int[] %s={%s};' % (name, ','.join(str(start+i) for i in range(7)))
                       for name, start in [('keys_avatar_background',100),('keys_avatar_background2',110),('keys_avatar_nameInMessage',120)])
    flags = '\n'.join('public static Flag %s=new Flag(%s);' % (name, 'false' if name in ('regramMonoColor','regramMonoGradient') else 'true')
                      for name in sorted(set(re.findall(r'NekoConfig\.(regram\w+)\.Bool', source))))
    sources = {
        'app/regram/appearance/MonoColor.java': source,
        'android/graphics/Color.java': '''package android.graphics; public class Color {
 public static final int BLACK=0xff000000, WHITE=0xffffffff;
 public static int red(int c){return c>>>16&255;} public static int green(int c){return c>>>8&255;}
 public static int blue(int c){return c&255;} public static int rgb(int r,int g,int b){return 0xff000000|r<<16|g<<8|b;}
}''',
        'android/graphics/ColorFilter.java': 'package android.graphics; public class ColorFilter {}',
        'android/graphics/ColorMatrixColorFilter.java': 'package android.graphics; public class ColorMatrixColorFilter extends ColorFilter { public final float[] values; public ColorMatrixColorFilter(float[] v){values=v.clone();} }',
        'android/os/Build.java': 'package android.os; public class Build { public static class VERSION { public static int SDK_INT=30; } }',
        'org/telegram/tgnet/TLRPC.java': 'package org.telegram.tgnet; public class TLRPC { public static class User {public Object photo;} public static class Chat {public Object photo;} }',
        'tw/nekomimi/nekogram/helpers/MonetHelper.java': 'package tw.nekomimi.nekogram.helpers; public class MonetHelper {public static int getColor(String s){return 0xff112233;} }',
        'tw/nekomimi/nekogram/NekoConfig.java': '''package tw.nekomimi.nekogram; public class NekoConfig {
 public static class Flag {public boolean value; public Flag(boolean v){value=v;} public boolean Bool(){return value;} }
 public static class Number {public int value; public Number(int v){value=v;} public int Int(){return value;} }
 public static Number regramMonoSource=new Number(0), regramMonoCustomColor=new Number(0xff4faede);
 FLAGS
}'''.replace('FLAGS',flags),
        'org/telegram/ui/ActionBar/Theme.java': '''package org.telegram.ui.ActionBar; import app.regram.appearance.MonoColor;
public class Theme {
 FIELDS ARRAYS
 public static boolean dark;
 public static class ThemeInfo {public int currentAccentId; public boolean hasAccentColors(){return false;}
  public int getAccentColor(int i){return 0;} public boolean isMonet(){return false;} public boolean isDark(){return dark;} }
 public static ThemeInfo getActiveTheme(){return new ThemeInfo();} public static boolean isCurrentThemeDark(){return dark;}
 public static int getColor(int key){Integer c=MonoColor.override(key); return c==null?0xff4faede:c;}
}'''.replace('FIELDS',fields).replace('ARRAYS',arrays),
        'PaletteTest.java': '''import app.regram.appearance.MonoColor; import tw.nekomimi.nekogram.NekoConfig;
import org.telegram.ui.ActionBar.Theme; import android.graphics.*;
public class PaletteTest {
 static float brightness(int c){return (Color.red(c)*.2126f+Color.green(c)*.7152f+Color.blue(c)*.0722f)/255f;}
 public static void main(String[] args){
  assert MonoColor.override(Theme.key_chats_name)==null; assert MonoColor.avatarFilter()==null;
  NekoConfig.regramMonoColor.value=true; NekoConfig.regramMonoSource.value=6;
  NekoConfig.regramMonoCustomColor.value=0xffff0000;
  assert MonoColor.avatarColor(false)==0xffff0000;
  assert MonoColor.avatarColor(false)==MonoColor.avatarColor(true);
  assert MonoColor.peerColor(0)==MonoColor.peerColor(1); assert MonoColor.peerColor(4)==MonoColor.peerColor(5);
  ColorFilter filter=MonoColor.avatarFilter(); assert filter==MonoColor.avatarFilter();
  float[] matrix=((ColorMatrixColorFilter)filter).values; assert matrix[18]==1 && matrix[19]==0;
  assert matrix[0]==.213f && matrix[5]==0 && matrix[10]==0;
  NekoConfig.regramMonoGradient.value=true;
  assert MonoColor.avatarColor(false)!=MonoColor.avatarColor(true);
  assert MonoColor.peerColor(4)!=MonoColor.peerColor(5);
  NekoConfig.regramMonoAvatars.value=false; assert MonoColor.avatarFilter()==null;
  assert MonoColor.override(Theme.keys_avatar_background[0])==null;
  NekoConfig.regramMonoNames.value=false; assert MonoColor.override(Theme.keys_avatar_nameInMessage[0])==null;
  NekoConfig.regramMonoStories.value=false; assert MonoColor.override(Theme.key_stories_circle1)==null;
  NekoConfig.regramMonoDialogs.value=false; assert MonoColor.override(Theme.key_chats_name)==null;
  NekoConfig.regramMonoProfiles.value=false; assert !MonoColor.profiles();
  NekoConfig.regramMonoCustomColor.value=0xff000000; Theme.dark=true;
  assert brightness(MonoColor.peerColor(0))>=.7f;
  NekoConfig.regramMonoCustomColor.value=0xffffffff; Theme.dark=false;
  assert brightness(MonoColor.peerColor(0))<=.48f;
  NekoConfig.regramMonoSource.value=999; assert MonoColor.sourceColor()==0xff4faede;
  NekoConfig.regramMonoColor.value=false;
  assert MonoColor.override(Theme.key_chats_name)==null; assert Theme.getColor(Theme.key_chats_name)==0xff4faede;
  assert !MonoColor.avatars() && !MonoColor.profiles();
 }
}''',
    }
    compile_run(tmp_path, sources, 'PaletteTest')
