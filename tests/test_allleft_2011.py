"""AllLeft 2.0.11: execute the native Java policy/renderer, not the plugin DEX."""
from pathlib import Path
import shutil
import subprocess
import pytest

ROOT = Path(__file__).resolve().parents[1]
JAVA = ROOT / 'TMessagesProj/src/main/java'


@pytest.fixture(scope='module')
def compiled(tmp_path_factory):
    if not shutil.which('javac') or not shutil.which('java'):
        pytest.skip('JDK unavailable')
    tmp = tmp_path_factory.mktemp('allleft2011')
    files = {
        'android/graphics/ColorFilter.java': 'package android.graphics; public class ColorFilter {}',
        'android/graphics/PorterDuff.java': 'package android.graphics; public class PorterDuff { public enum Mode { MULTIPLY } }',
        'android/graphics/PorterDuffColorFilter.java': 'package android.graphics; public class PorterDuffColorFilter extends ColorFilter { public PorterDuffColorFilter(int c, PorterDuff.Mode m) {} }',
        'android/graphics/Rect.java': '''package android.graphics; public class Rect {
            public int left,top,right,bottom; public Rect(int l,int t,int r,int b){left=l;top=t;right=r;bottom=b;}
        }''',
        'android/graphics/Canvas.java': '''package android.graphics; public class Canvas {
            public java.util.List<String> calls=new java.util.ArrayList<>();
            public java.util.List<Rect> bounds=new java.util.ArrayList<>();
            public java.util.List<Integer> alphas=new java.util.ArrayList<>();
        }''',
        'android/graphics/drawable/Drawable.java': '''package android.graphics.drawable;
            import android.graphics.*;
            public class Drawable {
                public String name; public int width=12,height=10,alpha=173; public boolean fail;
                public ColorFilter filter=new ColorFilter(); private Rect bounds=new Rect(3,4,8,9);
                public Drawable(String n){name=n;} public Rect getBounds(){return bounds;}
                public int getAlpha(){return alpha;} public void setAlpha(int a){alpha=a;}
                public ColorFilter getColorFilter(){return filter;} public void setColorFilter(ColorFilter f){filter=f;}
                public void setBounds(int l,int t,int r,int b){bounds=new Rect(l,t,r,b);}
                public int getIntrinsicWidth(){return width;} public int getIntrinsicHeight(){return height;}
                public void draw(Canvas c){
                    c.calls.add(name); c.bounds.add(new Rect(bounds.left,bounds.top,bounds.right,bounds.bottom));
                    c.alphas.add(alpha); if(fail)throw new IllegalStateException("draw failure");
                }
            }''',
        'org/telegram/messenger/AndroidUtilities.java': '''package org.telegram.messenger;
            public class AndroidUtilities { public static int dp(float v){return (int)Math.ceil(v);} }''',
        'org/telegram/messenger/DialogObject.java': '''package org.telegram.messenger; public class DialogObject {
            public static boolean isEncryptedDialog(long d){return d>0 && (d>>>32)!=0;}
            public static boolean isUserDialog(long d){return d>0 && !isEncryptedDialog(d);}
        }''',
        'org/telegram/messenger/UserConfig.java': '''package org.telegram.messenger; public class UserConfig {
            private final int account; private UserConfig(int a){account=a;}
            public static UserConfig getInstance(int a){return new UserConfig(a);}
            public long getClientUserId(){return account==0 ? 100 : 200;}
        }''',
        'org/telegram/messenger/MessagesController.java': '''package org.telegram.messenger; public class MessagesController {
            static MessagesController[] accounts={new MessagesController(),new MessagesController()};
            public java.util.Map<Long,Integer> dialogs_read_outbox_max=new java.util.HashMap<>();
            public static MessagesController getInstance(int a){return accounts[a];}
        }''',
        'org/telegram/messenger/MessageObject.java': '''package org.telegram.messenger; public class MessageObject {
            public static final int TYPE_PHONE_CALL=16;
            public Object messageOwner=new Object(); public int currentAccount,type,id=10; public long dialog=300;
            public boolean isSaved,preview,previewForward,sendPreview,isRepostPreview,notime,scheduled;
            public boolean quick,sponsored,error,sending,editing,sent=true,unread=true,out=true;
            public long getDialogId(){return dialog;} public int getId(){return id;}
            public boolean isOutOwner(){return out;} public boolean isSponsored(){return sponsored;}
            public boolean isQuickReply(){return quick;} public boolean isSendError(){return error;}
            public boolean isSending(){return sending;} public boolean isEditing(){return editing;}
            public boolean isSent(){return sent;} public boolean isUnread(){return unread;}
        }''',
        'org/telegram/ui/ActionBar/Theme.java': '''package org.telegram.ui.ActionBar;
            import android.graphics.drawable.Drawable; public class Theme {
                public static final int key_chat_mediaSentCheck=1,key_chat_inTimeText=2,key_chat_sentErrorIcon=3;
                public static Drawable chat_msgErrorDrawable=new Drawable("error"),chat_msgClockDrawable=new Drawable("sharedClock"),
                    chat_msgMediaCheckDrawable=new Drawable("media"),chat_msgOutCheckReadDrawable=new Drawable("read"),
                    chat_msgMediaHalfCheckDrawable=new Drawable("mediaHalf"),chat_msgOutHalfCheckDrawable=new Drawable("half"),
                    chat_msgOutCheckDrawable=new Drawable("sent");
            }''',
        'org/telegram/ui/Cells/ChatMessageCell.java': '''package org.telegram.ui.Cells; public class ChatMessageCell {
            public boolean media; public int invalidations; public boolean shouldDrawTimeOnMedia(){return media;}
            public int getThemedColor(int key){return key;} public void invalidate(){invalidations++;}
        }''',
        'org/telegram/ui/Components/MsgClockDrawable.java': '''package org.telegram.ui.Components;
            public class MsgClockDrawable extends android.graphics.drawable.Drawable {
                public int color; public MsgClockDrawable(){super("privateClock");} public void setColor(int c){color=c;}
            }''',
        'tw/nekomimi/nekogram/NekoConfig.java': '''package tw.nekomimi.nekogram; public class NekoConfig {
            public static class Flag{ public boolean value; Flag(boolean v){value=v;} public boolean Bool(){return value;} }
            public static Flag regramAllLeft=new Flag(true),regramAllLeftPrivate=new Flag(false),regramAllLeftStatus=new Flag(true);
        }''',
        'AllLeftTest.java': '''
            import app.regram.chats.AllLeft;
            import android.graphics.*;
            import android.graphics.drawable.Drawable;
            import org.telegram.messenger.*;
            import org.telegram.ui.ActionBar.Theme;
            import org.telegram.ui.Cells.ChatMessageCell;
            import tw.nekomimi.nekogram.NekoConfig;
            public class AllLeftTest {
                static void draw(Canvas c, ChatMessageCell cell, MessageObject m){AllLeft.drawStatus(c,cell,m,1,20,40,80,20);}
                static void unchanged(Drawable d, ColorFilter f){
                    assert d.alpha==173 && d.filter==f;
                    assert d.getBounds().left==3 && d.getBounds().top==4 && d.getBounds().right==8 && d.getBounds().bottom==9;
                }
                public static void main(String[] args){
                    String test=args[0]; MessageObject m=new MessageObject();
                    ChatMessageCell cell=new ChatMessageCell(); Canvas c=new Canvas();
                    if(test.equals("read")){
                        m.unread=false; assert AllLeft.status(m)==1; // unread=false alone is not a cloud read receipt
                        MessagesController.getInstance(0).dialogs_read_outbox_max.put(m.dialog,9);
                        assert AllLeft.status(m)==1;
                        MessagesController.getInstance(0).dialogs_read_outbox_max.put(m.dialog,10);
                        assert AllLeft.status(m)==2;
                        m.currentAccount=1; assert AllLeft.status(m)==1;
                        m.id=0; assert AllLeft.status(m)==1; m.id=-5; assert AllLeft.status(m)==1;
                    }else if(test.equals("secret")){
                        m.dialog=1L<<32; m.id=-9;
                        assert AllLeft.status(m)==1; m.unread=false; assert AllLeft.status(m)==2;
                        m.scheduled=true; assert AllLeft.status(m)==1;
                    }else if(test.equals("pending")){
                        m.sending=true; assert AllLeft.status(m)==0; draw(c,cell,m);
                        assert c.calls.toString().equals("[privateClock]") && cell.invalidations==1;
                        m.sending=false; m.editing=true; assert AllLeft.status(m)==0;
                        m.error=true; assert AllLeft.status(m)==3;
                        m.notime=true; assert AllLeft.status(m)==-1;
                        m.notime=false; m.error=false; m.editing=false; m.sent=false; assert AllLeft.status(m)==-1;
                    }else if(test.equals("settings")){
                        assert AllLeft.statusVisible(m);
                        NekoConfig.regramAllLeftStatus.value=false; draw(c,cell,m); assert c.calls.isEmpty();
                        NekoConfig.regramAllLeftStatus.value=true; m.out=false; draw(c,cell,m); assert c.calls.isEmpty();
                        m.out=true; m.dialog=100; assert !AllLeft.active(m);
                        m.currentAccount=1; assert AllLeft.active(m); m.dialog=200; assert !AllLeft.active(m);
                        m.dialog=-300; NekoConfig.regramAllLeftPrivate.value=true; assert !AllLeft.active(m);
                        m.dialog=1L<<32; assert AllLeft.active(m);
                        m.isSaved=true; assert !AllLeft.active(m);
                    }else if(test.equals("pinned")){
                        assert AllLeft.pinnedBottomProgress(m,.25f)==1f;
                        m.out=false; assert AllLeft.pinnedBottomProgress(m,.25f)==.25f;
                        m.out=true; NekoConfig.regramAllLeft.value=false;
                        assert AllLeft.pinnedBottomProgress(m,.25f)==.25f;
                        NekoConfig.regramAllLeft.value=true; m.isSaved=true;
                        assert AllLeft.pinnedBottomProgress(m,.25f)==.25f;
                    }else if(test.equals("geometry")){
                        Drawable d=Theme.chat_msgOutCheckDrawable; ColorFilter f=d.filter;
                        draw(c,cell,m); unchanged(d,f);
                        assert c.calls.toString().equals("[sent]");
                        assert c.bounds.get(0).left==88 && c.bounds.get(0).right==100 && c.bounds.get(0).top==45;
                        cell.media=true; c=new Canvas(); draw(c,cell,m);
                        assert c.calls.toString().equals("[media]") && c.bounds.get(0).top==48;
                        MessagesController.getInstance(0).dialogs_read_outbox_max.put(m.dialog,10);
                        c=new Canvas(); draw(c,cell,m);
                        assert c.calls.toString().equals("[media, mediaHalf]");
                        assert c.bounds.get(0).right==95 && c.bounds.get(1).right==100;
                    }else if(test.equals("restore")){
                        Drawable d=Theme.chat_msgOutCheckDrawable; ColorFilter f=d.filter; d.fail=true;
                        try{draw(c,cell,m);throw new AssertionError();}catch(IllegalStateException expected){}
                        unchanged(d,f); d.fail=false; d.width=0; d.height=-1; c=new Canvas(); draw(c,cell,m);
                        assert c.bounds.get(0).right-c.bounds.get(0).left==11;
                        assert c.bounds.get(0).bottom-c.bounds.get(0).top==11;
                        c=new Canvas(); AllLeft.drawStatus(c,cell,m,0,20,40,80,20); assert c.calls.isEmpty();
                    }else throw new AssertionError(test);
                    assert m.messageOwner!=null; // policy/rendering never replaces the owner/model
                }
            }
        ''',
    }
    files['app/regram/chats/AllLeft.java'] = (JAVA / 'app/regram/chats/AllLeft.java').read_text()
    paths = []
    for name, text in files.items():
        path = tmp / name
        path.parent.mkdir(parents=True, exist_ok=True)
        path.write_text(text)
        paths.append(str(path))
    subprocess.run(['javac', '-d', str(tmp), *paths], check=True, capture_output=True, text=True)
    return tmp


@pytest.mark.parametrize('case', ['read', 'secret', 'pending', 'settings', 'pinned', 'geometry', 'restore'])
def test_actual_native_policy_and_drawing(compiled, case):
    subprocess.run(['java', '-ea', '-cp', str(compiled), 'AllLeftTest', case], check=True, capture_output=True, text=True)


def test_ui_uses_layout_policy_without_mutating_model_or_width():
    cell = (JAVA / 'org/telegram/ui/Cells/ChatMessageCell.java').read_text()
    assert cell.count('transitionParams.changePinnedBottomProgress') == 1
    assert cell.count('pinnedBottomProgressForLayout()') >= 13
    region = cell[cell.index('private void drawTimeInternal('):cell.index('private final ArrayList<app.regram.chats.MentionAvatarSpan>')]
    assert 'final float allLeftTimeWidth = timeWidth' in region
    assert 'timeWidth -= dp(20)' in region and 'this.timeWidth -=' not in region
    assert 'additionalX += this.timeWidth' not in region
    assert 'timeX + allLeftTimeWidth' in region
    assert region.index('drawTimeX = timeTitleTimeX + additionalX;') < region.index('if (transitionParams.animateEditedEnter &&')
    assert 'right - drawTimeX' in cell
    guard = cell[cell.index('private void drawClockOrErrorLayout('):cell.index('public float getTimeX()')]
    assert 'AllLeft.active(currentMessageObject) && currentMessageObject.isOutOwner()) return;' in guard
    enter = (JAVA / 'org/telegram/ui/TextMessageEnterTransition.java').read_text()
    assert '.isOutOwner()' not in enter and enter.count('.isOutOwnerForLayout()') == 5
    policy = (JAVA / 'app/regram/chats/AllLeft.java').read_text()
    for forbidden in ('java.lang.reflect', 'XposedBridge', 'ThreadLocal', 'DexClassLoader', 'PluginsController', 'messageOwner.out ='):
        assert forbidden not in policy
    assert 'Theme.chat_msgClockDrawable' not in policy # private clock, shared theme color untouched
    for lang in ('values', 'values-ru'):
        assert 'AllLeft 2.0.11' in (ROOT / f'TMessagesProj/src/main/res/{lang}/regram_native_visual.xml').read_text()
