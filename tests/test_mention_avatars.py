from pathlib import Path
import re
from test_native_visual_settings import compile_run

ROOT = Path(__file__).resolve().parents[1]
JAVA = ROOT / 'TMessagesProj/src/main/java'


def test_mention_native_calls_lifecycle_and_persistence():
    helper = (JAVA / 'app/regram/chats/MentionAvatars.java').read_text()
    for token in ('XposedBridge', 'XC_MethodHook', 'InMemoryDexClassLoader', 'PluginsController',
                  'sendRequest', 'UserLookup', 'UserConfig.selectedAccount', '.insert(', '.delete('):
        assert token not in helper
    assert 'MessagesController.getInstance(account)' in helper
    assert 'span.isSpoiler() || span.isMono()' in helper
    assert 'MAX_MENTIONS = 40' in helper
    model = (JAVA / 'org/telegram/messenger/MessageObject.java').read_text()
    assert 'MentionAvatars.apply(messageText, currentAccount)' in model
    assert 'MentionAvatars.apply(caption, currentAccount)' in model
    assert 'mentionAvatarSettings != mentionSignature' in model
    assert 'mentionAvatarCaptionSettings != mentionSignature' in model
    assert 'mentionAvatarCaptionSettings = app.regram.chats.MentionAvatars.signature()' in model
    cell = (JAVA / 'org/telegram/ui/Cells/ChatMessageCell.java').read_text()
    assert 'bindMentionAvatars(null);' in cell and 'bindMentionAvatars(this);' in cell
    assert 'boundMentionAvatars' in cell and 'span.clearParent(this)' in cell
    avatar_base = (JAVA / 'org/telegram/ui/AvatarSpan.java').read_text()
    assert 'if (parent == owner) setParent(null)' in avatar_base
    span = (JAVA / 'app/regram/chats/MentionAvatarSpan.java').read_text()
    assert 'setLoadOnlyWhenAttached(true)' in span
    assert 'canvas.drawText(text, start, end' in span
    config = (JAVA / 'tw/nekomimi/nekogram/NekoConfig.java').read_text()
    fields = re.findall(r'ConfigItem (regramMention\w+) =', config)
    assert len(fields) == 5
    settings = (JAVA / 'app/regram/settings/NativeVisualSettingsActivity.java').read_text()
    for field in fields:
        assert 'NekoConfig.' + field in settings


def test_actual_mention_policy_spans_and_url_parser(tmp_path):
    source = (JAVA / 'app/regram/chats/MentionAvatars.java').read_text()
    files = {
        'app/regram/chats/MentionAvatars.java': source,
        'android/view/View.java': 'package android.view; public class View {}',
        'android/text/Spanned.java': '''package android.text; public interface Spanned extends CharSequence {
 int SPAN_EXCLUSIVE_EXCLUSIVE=33; <T> T[] getSpans(int a,int b,Class<T> type);
 int getSpanStart(Object o); int getSpanEnd(Object o); int getSpanFlags(Object o);
}''',
        'android/text/SpannableStringBuilder.java': '''package android.text;
import java.util.*; import java.lang.reflect.Array;
public class SpannableStringBuilder implements Spanned {
 final String text; final List<Object[]> spans=new ArrayList<>();
 public SpannableStringBuilder(CharSequence s){text=s.toString(); if(s instanceof Spanned){Spanned p=(Spanned)s;
  for(Object o:p.getSpans(0,p.length(),Object.class))setSpan(o,p.getSpanStart(o),p.getSpanEnd(o),p.getSpanFlags(o));}}
 public void setSpan(Object o,int a,int b,int f){removeSpan(o); spans.add(new Object[]{o,a,b,f});}
 public void removeSpan(Object o){spans.removeIf(x->x[0]==o);}
 public int length(){return text.length();} public char charAt(int i){return text.charAt(i);}
 public CharSequence subSequence(int a,int b){return text.substring(a,b);} public String toString(){return text;}
 public <T> T[] getSpans(int a,int b,Class<T> type){List<T> r=new ArrayList<>();
  for(Object[] x:spans)if(type.isInstance(x[0])&&(int)x[1]<b&&(int)x[2]>a)r.add(type.cast(x[0]));
  return r.toArray((T[])Array.newInstance(type,r.size()));}
 int field(Object o,int n){for(Object[] x:spans)if(x[0]==o)return (int)x[n];return -1;}
 public int getSpanStart(Object o){return field(o,1);} public int getSpanEnd(Object o){return field(o,2);}
 public int getSpanFlags(Object o){return field(o,3);}
}''',
        'android/text/style/ReplacementSpan.java': 'package android.text.style; public class ReplacementSpan {}',
        'android/text/style/URLSpan.java': 'package android.text.style; public class URLSpan {final String url; public URLSpan(String u){url=u;} public String getURL(){return url;}}',
        'org/telegram/tgnet/TLObject.java': 'package org.telegram.tgnet; public class TLObject {}',
        'org/telegram/tgnet/TLRPC.java': 'package org.telegram.tgnet; public class TLRPC {public static class User extends TLObject {public boolean bot;} public static class Chat extends TLObject {}}',
        'org/telegram/ui/Components/URLSpanNoUnderline.java': 'package org.telegram.ui.Components; import android.text.style.URLSpan; import org.telegram.tgnet.TLObject; public class URLSpanNoUnderline extends URLSpan {public TextStyleSpan.TextStyleRun style; public URLSpanNoUnderline(String u){super(u);} public TLObject getObject(){return null;} public TextStyleSpan.TextStyleRun getTextStyleRun(){return style;}}',
        'org/telegram/ui/Components/URLSpanBrowser.java': 'package org.telegram.ui.Components; public class URLSpanBrowser extends android.text.style.URLSpan {public URLSpanBrowser(String u){super(u);} public TextStyleSpan.TextStyleRun getStyle(){return null;}}',
        'org/telegram/ui/Components/URLSpanReplacement.java': 'package org.telegram.ui.Components; public class URLSpanReplacement extends android.text.style.URLSpan {public URLSpanReplacement(String u){super(u);} public TextStyleSpan.TextStyleRun getTextStyleRun(){return null;}}',
        'org/telegram/ui/Components/URLSpanUserMention.java': 'package org.telegram.ui.Components; public class URLSpanUserMention extends URLSpanNoUnderline {public URLSpanUserMention(String u){super(u);}}',
        'org/telegram/ui/Components/TextStyleSpan.java': 'package org.telegram.ui.Components; public class TextStyleSpan {public static final int FLAG_STYLE_SPOILER=1, FLAG_STYLE_MONO=2, FLAG_STYLE_CODE=4; public static class TextStyleRun {public int flags;} boolean spoiler,mono; public TextStyleSpan(boolean s,boolean m){spoiler=s;mono=m;} public boolean isSpoiler(){return spoiler;} public boolean isMono(){return mono;}}',
        'app/regram/chats/MentionAvatarSpan.java': '''package app.regram.chats; import android.view.View; import org.telegram.tgnet.TLObject;
public class MentionAvatarSpan extends android.text.style.ReplacementSpan {
 public final TLObject peer; public final float size; public final boolean before; public View parent;
 public MentionAvatarSpan(int account,float size,boolean before,TLObject peer){this.size=size;this.before=before;this.peer=peer;}
 public void setParent(View v){parent=v;}
}''',
        'tw/nekomimi/nekogram/NekoConfig.java': '''package tw.nekomimi.nekogram; public class NekoConfig {
 public static class Flag{public boolean value; public Flag(boolean v){value=v;} public boolean Bool(){return value;}}
 public static class Number{public int value; public Number(int v){value=v;} public int Int(){return value;}}
 public static Flag regramMentionAvatars=new Flag(false),regramMentionChats=new Flag(true),regramMentionBots=new Flag(true);
 public static Number regramMentionSize=new Number(1),regramMentionPosition=new Number(0);
}''',
        'org/telegram/messenger/MessagesController.java': '''package org.telegram.messenger; import org.telegram.tgnet.*; import java.util.*;
public class MessagesController {
 static final MessagesController[] instances={new MessagesController(),new MessagesController()};
 public Map<String,TLObject> peers=new HashMap<>(); public TLRPC.User user;
 public static MessagesController getInstance(int a){return instances[a];}
 public TLObject getUserOrChat(String s){return peers.get(s);} public TLRPC.User getUser(long id){return id==123?user:null;}
}''',
        'MentionTest.java': '''import android.text.*; import android.text.style.*; import org.telegram.tgnet.*;
import org.telegram.messenger.MessagesController; import org.telegram.ui.Components.*;
import app.regram.chats.*; import tw.nekomimi.nekogram.NekoConfig;
public class MentionTest {
 static SpannableStringBuilder mention(String url){SpannableStringBuilder s=new SpannableStringBuilder("hi @foo!");s.setSpan(new URLSpan(url),3,7,33);return s;}
 static MentionAvatarSpan[] avatars(CharSequence s){return ((Spanned)s).getSpans(0,s.length(),MentionAvatarSpan.class);}
 public static void main(String[] args){
  assert MentionAvatars.usernameFromUrl("@Foo").equals("foo");
  assert MentionAvatars.usernameFromUrl("HTTPS://T.ME/Foo/123?x=1").equals("foo");
  assert MentionAvatars.usernameFromUrl("tg://resolve?domain=Foo&start=123").equals("foo");
  assert MentionAvatars.usernameFromUrl("https://telegram.me/Foo#x").equals("foo");
  for(String u:new String[]{"https://t.me.evil/foo","https://evil/t.me/foo","https://t.me/+invite","@a b","@","https://t.me/"})assert MentionAvatars.usernameFromUrl(u)==null:u;
  SpannableStringBuilder original=mention("@foo"); assert MentionAvatars.apply(original,0)==original;
  TLRPC.User user=new TLRPC.User(); MessagesController.getInstance(0).peers.put("foo",user);
  NekoConfig.regramMentionAvatars.value=true;
  CharSequence decorated=MentionAvatars.apply(original,0); assert decorated.toString().equals(original.toString());
  assert avatars(decorated).length==1; MentionAvatarSpan avatar=avatars(decorated)[0];
  assert ((Spanned)decorated).getSpanStart(avatar)==3 && ((Spanned)decorated).getSpanEnd(avatar)==4;
  URLSpan clickable=((Spanned)decorated).getSpans(0,decorated.length(),URLSpan.class)[0];
  assert ((Spanned)decorated).getSpanStart(clickable)==3 && ((Spanned)decorated).getSpanEnd(clickable)==7;
  SpannableStringBuilder longLink=new SpannableStringBuilder("https://t.me/foo/"+"a".repeat(500));
  longLink.setSpan(new URLSpan("https://t.me/foo/123"),0,longLink.length(),33);
  MentionAvatarSpan edge=avatars(MentionAvatars.apply(longLink,0))[0];
  assert ((Spanned)MentionAvatars.apply(longLink,0)).getSpans(0,longLink.length(),URLSpan.class).length==1;
  Spanned longDecorated=(Spanned)MentionAvatars.apply(longLink,0); edge=avatars(longDecorated)[0];
  assert longDecorated.getSpanEnd(edge)-longDecorated.getSpanStart(edge)==1; // Rest of URL remains breakable.
  assert avatar.before && avatar.size==16 && avatar.peer==user;
  assert avatars(MentionAvatars.apply(decorated,0)).length==1;
  assert avatars(MentionAvatars.apply(original,1)).length==0; // No cross-account fallback.
  int sig=MentionAvatars.signature(); NekoConfig.regramMentionPosition.value=1; NekoConfig.regramMentionSize.value=2;
  assert sig!=MentionAvatars.signature(); avatar=avatars(MentionAvatars.apply(original,0))[0];
  assert !avatar.before && avatar.size==18;
  user.bot=true; NekoConfig.regramMentionBots.value=false; assert avatars(MentionAvatars.apply(original,0)).length==0;
  user.bot=false; TLRPC.Chat chat=new TLRPC.Chat(); MessagesController.getInstance(0).peers.put("foo",chat);
  NekoConfig.regramMentionChats.value=false; assert avatars(MentionAvatars.apply(original,0)).length==0;
  MessagesController.getInstance(0).peers.put("foo",user);
  original.setSpan(new TextStyleSpan(true,false),3,7,33); assert avatars(MentionAvatars.apply(original,0)).length==0;
  original=mention("@foo"); original.setSpan(new TextStyleSpan(false,true),3,7,33); assert avatars(MentionAvatars.apply(original,0)).length==0;
  original=mention("@foo"); URLSpanNoUnderline styled=new URLSpanNoUnderline("@foo");
  styled.style=new TextStyleSpan.TextStyleRun(); styled.style.flags=TextStyleSpan.FLAG_STYLE_SPOILER;
  original=new SpannableStringBuilder("@foo"); original.setSpan(styled,0,4,33);
  assert avatars(MentionAvatars.apply(original,0)).length==0;
  styled.style.flags=TextStyleSpan.FLAG_STYLE_CODE; assert avatars(MentionAvatars.apply(original,0)).length==0;
  original=mention("@foo"); original.setSpan(new ReplacementSpan(),3,7,33); assert avatars(MentionAvatars.apply(original,0)).length==0;
  MessagesController.getInstance(0).user=user;
  original=new SpannableStringBuilder("Alice"); original.setSpan(new URLSpanUserMention("123"),0,5,33);
  assert avatars(MentionAvatars.apply(original,0)).length==1;
  original=new SpannableStringBuilder("@foo ".repeat(60)); for(int i=0;i<60;i++)original.setSpan(new URLSpan("@foo"),i*5,i*5+4,33);
  assert avatars(MentionAvatars.apply(original,0)).length==40;
  NekoConfig.regramMentionAvatars.value=false; CharSequence plain=MentionAvatars.apply(decorated,0);
  assert plain.toString().equals(decorated.toString()) && avatars(plain).length==0;
  assert ((Spanned)plain).getSpans(0,plain.length(),URLSpan.class).length==1;
  assert MentionAvatars.signature()==0;
 }
}''',
    }
    compile_run(tmp_path, files, 'MentionTest')
