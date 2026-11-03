"""Executable bounded-work guards for the 13.1.1 performance pass, not FPS tests."""
from pathlib import Path
import re

from test_native_visual_settings import compile_run, method

ROOT = Path(__file__).resolve().parents[1]
JAVA = ROOT / "TMessagesProj/src/main/java"


def code(path):
    return (JAVA / "app/regram" / path).read_text()


def test_player_time_labels_reuse_strings_and_preserve_formatting(tmp_path):
    source = code("player/PlaybackTimeText.java")
    compile_run(tmp_path, {
        "app/regram/player/PlaybackTimeText.java": source,
        "app/regram/player/TimeTest.java": '''package app.regram.player;
import java.util.Locale;
public class TimeTest {
 static String reference(int s) {
  return s>=3600 ? String.format(Locale.US,"%d:%02d:%02d",s/3600,s/60%60,s%60)
    : String.format(Locale.US,"%d:%02d",s/60,s%60);
 }
 public static void main(String[] args) {
  PlaybackTimeText labels=new PlaybackTimeText();
  int[] seconds={Integer.MIN_VALUE,-61,-1,0,9,59,60,78,3599,3600,86399,Integer.MAX_VALUE};
  for(int s:seconds) {
   String a=labels.elapsed(s), b=labels.remaining(s);
   assert a.equals(reference(s)); assert b.equals("−"+reference(s));
   for(int i=0;i<10000;i++) {assert labels.elapsed(s)==a;assert labels.remaining(s)==b;}
  }
  assert labels.elapsed(78).equals("1:18"); // backward seek and track change
  assert labels.remaining(0).equals("−0:00");
  PlaybackTimeText other=new PlaybackTimeText();
  other.elapsed(9); assert labels.elapsed(78).equals("1:18");
 }
}'''
    }, "app.regram.player.TimeTest")
    update = method(code("player/PlayerSheet.java"), "private void updateTimes()")
    assert "timeText.elapsed(now)" in update
    assert "timeText.remaining(Math.max(0, duration - now))" in update
    # Comparing the actual view text still restores labels after preview/external changes.
    assert "TextUtils.equals(timeNow.getText(), a)" in update
    assert "bubble.setText(a)" in update


def test_queue_sync_preserves_order_and_skips_unchanged_notifications(tmp_path):
    source = code("player/PlayerQueueSheet.java")
    sync = method(source, "private boolean syncItems(boolean force)")
    assert "new ArrayList" not in sync and "Collections.reverse" not in sync
    compile_run(tmp_path, {"QueueTest.java": '''import java.util.*;
class MessageObject {}
class MediaController {
 static MediaController instance=new MediaController();
 final ArrayList<MessageObject> playlist=new ArrayList<>();
 static MediaController getInstance(){return instance;}
 ArrayList<MessageObject> getPlaylist(){return playlist;}
}
public class QueueTest {
 final ArrayList<MessageObject> items=new ArrayList<>();
 static class Adapter {int notifications; void notifyDataSetChanged(){notifications++;}}
 Adapter adapter=new Adapter(); boolean forward;
 boolean direct(){return forward;}
 SYNC
 void verify() {
  ArrayList<MessageObject> p=MediaController.instance.playlist;
  assert items.size()==p.size();
  for(int i=0;i<p.size();i++) assert items.get(i)==p.get(forward?i:p.size()-1-i);
 }
 public static void main(String[] args) {
  QueueTest q=new QueueTest(); ArrayList<MessageObject> p=MediaController.instance.playlist;
  for(int i=0;i<1000;i++)p.add(new MessageObject());
  q.forward=true; assert q.syncItems(false);q.verify();
  int notifications=q.adapter.notifications;
  for(int i=0;i<10000;i++)assert !q.syncItems(false);
  assert notifications==q.adapter.notifications;
  q.forward=false;assert q.syncItems(false);q.verify();
  assert !q.syncItems(false);assert q.syncItems(true);q.verify();
  Random random=new Random(42);
  for(int i=0;i<300;i++) {
   Collections.swap(p,random.nextInt(p.size()),random.nextInt(p.size()));
   q.forward=!q.forward;q.syncItems(false);q.verify();
   assert !q.syncItems(false);
  }
  p.clear();assert q.syncItems(false);q.verify();assert !q.syncItems(false);
 }
}'''.replace("SYNC", sync)}, "QueueTest")
    notification = method(source, "public void didReceivedNotification(")
    assert notification.count("currentItem()") == 1
    assert notification.index("currentItem()") < notification.index("list.getChildCount()")
    assert ".updateState(current)" in notification
    assert "message == playingItem" in method(source, "void updateState(MessageObject playingItem)")


def test_mention_username_scanner_matches_previous_parser(tmp_path):
    source = code("chats/MentionAvatars.java")
    parser = method(source, "public static String usernameFromUrl(")
    helper = method(source, "private static int usernameEnd(")
    prefixes = re.search(r"private static final String\[\] USERNAME_PREFIXES = \{.*?};", source, re.S).group()
    assert ".split(" not in parser and ".matches(" not in parser
    compile_run(tmp_path, {"UsernameTest.java": '''import java.util.*;
public class UsernameTest {
 __PREFIXES__
 PARSER
 HELPER
 static String reference(String url) {
  if(url==null)return null;
  String value=url.trim(),lower=value.toLowerCase(Locale.US);
  if(value.startsWith("@"))value=value.substring(1);
  else if(lower.startsWith("tg://resolve?domain="))value=value.substring(20).split("[&#]",2)[0];
  else {
   boolean found=false;
   for(String prefix:USERNAME_PREFIXES)if(lower.startsWith(prefix)) {
    value=value.substring(prefix.length()).split("[/?#]",2)[0];found=true;break;
   }
   if(!found)return null;
  }
  return value.matches("[A-Za-z0-9_]{1,32}")?value.toLowerCase(Locale.US):null;
 }
 static void verify(String url) {assert Objects.equals(reference(url),usernameFromUrl(url)):url;}
 public static void main(String[] args) {
  verify(null);verify("");verify("@");verify("https://t.me/");
  for(int n:new int[]{1,31,32,33,100})verify("@"+"A".repeat(n));
  String[] fixed={"HTTPS://T.ME/Foo/123?x=1","tg://resolve?domain=Foo&start=1",
   "https://t.me.evil/foo","https://t.me/+invite","@a b","https://telegram.me/Foo#x",
   "https://t.me/?q","tg://resolve?domain=&a","@İ","@Александр","  @Ab_C9  "};
  for(String s:fixed)verify(s);
  String chars="AbZ09_ /?#&+%éİА\\t\\n";
  String[] starts={"@","https://t.me/","HTTP://TELEGRAM.ME/","tg://resolve?domain=","https://evil/",""};
  Random random=new Random(811);
  for(int n=0;n<20000;n++) {
   StringBuilder s=new StringBuilder(starts[random.nextInt(starts.length)]);
   for(int i=random.nextInt(45);i>0;i--)s.append(chars.charAt(random.nextInt(chars.length())));
   verify(s.toString());
  }
 }
}'''.replace("__PREFIXES__", prefixes).replace("PARSER", parser).replace("HELPER", helper)}, "UsernameTest")


def test_gradient_cache_and_equalizer_visibility_with_actual_methods(tmp_path):
    lyrics = code("player/LyricsView.java")
    gradient = method(lyrics, "private LinearGradient fillGradient(")
    equalizer = method(code("player/PlayerQueueSheet.java"), "private class Equalizer extends View")
    compile_run(tmp_path, {"RenderingTest.java": '''
@interface NonNull {}
class Context {}
class View {
 static final int VISIBLE=0, GONE=8;
 boolean shown=true;int windowVisibility=VISIBLE,frames,invalidations;
 View(Context context){}
 int getWidth(){return 22;}int getHeight(){return 20;}
 boolean isShown(){return shown;}int getWindowVisibility(){return windowVisibility;}
 void invalidate(){invalidations++;}void postInvalidateOnAnimation(){frames++;}
 protected void onDraw(Canvas c){}
 protected void onVisibilityChanged(View v,int visibility){}
 protected void onWindowVisibilityChanged(int visibility){}
 protected void onDetachedFromWindow(){}
}
class Paint {static final int ANTI_ALIAS_FLAG=1;Paint(int flags){}void setColor(int c){}}
class RectF {void set(float a,float b,float c,float d){}}
class Canvas {int bars;void drawRoundRect(RectF r,float x,float y,Paint p){bars++;}}
class SystemClock {static long time=100;static long elapsedRealtime(){return time+=16;}}
class Shader {enum TileMode {CLAMP}}
class LinearGradient {
 static int builds;
 LinearGradient(float a,float b,float c,float d,int filled,int unfilled,Shader.TileMode mode){builds++;}
}
public class RenderingTest {
 static int dp(float f){return (int)Math.ceil(f);}
 static class Colors {int primary=123;}
 Colors colors=new Colors();
 private LinearGradient cachedFill;private int fillColor,unfilledColor;
 GRADIENT
 EQUALIZER
 public static void main(String[] args) {
  RenderingTest r=new RenderingTest();
  LinearGradient first=r.fillGradient(0xffabcdef,0x7fabcdef);
  for(int i=0;i<100000;i++)assert first==r.fillGradient(0xffabcdef,0x7fabcdef);
  assert LinearGradient.builds==1;
  assert first!=r.fillGradient(0xff123456,0x7fabcdef);assert LinearGradient.builds==2;
  r.fillGradient(0xff123456,0x7f123456);assert LinearGradient.builds==3;
  Equalizer e=r.new Equalizer(new Context());Canvas canvas=new Canvas();
  e.setPlaying(true);e.onDraw(canvas);assert e.frames==1&&canvas.bars==3;
  e.shown=false;e.onDraw(canvas);assert e.frames==1&&e.lastFrame==0;
  e.shown=true;e.windowVisibility=View.GONE;e.onDraw(canvas);assert e.frames==1&&e.lastFrame==0;
  e.windowVisibility=View.VISIBLE;e.onWindowVisibilityChanged(View.VISIBLE);
  e.onDraw(canvas);assert e.frames==2;
  e.setPlaying(false);e.onDraw(canvas);assert e.frames==2&&e.lastFrame==0;
  e.setPlaying(true);e.onDraw(canvas);e.onDetachedFromWindow();assert e.lastFrame==0;
 }
}'''.replace("GRADIENT", gradient).replace("EQUALIZER", equalizer)}, "RenderingTest")
    draw = method(lyrics, "protected void onDraw(@NonNull Canvas canvas)")
    assert "new LinearGradient" not in draw
    assert "fill.setLocalMatrix(fillMatrix)" in draw
    assert "isShown() && getWindowVisibility() == VISIBLE" in draw
    assert "if (visibility == VISIBLE) invalidate();" in lyrics


def test_section_shadow_configuration_is_reused_but_alpha_density_stay_live(tmp_path):
    source = code("appearance/SectionCardRecyclerView.java")
    compile_run(tmp_path, {
        "app/regram/appearance/SectionCardRecyclerView.java": source,
        "android/content/Context.java": "package android.content;public class Context {}",
        "android/graphics/RectF.java": "package android.graphics;public class RectF {}",
        "android/graphics/Paint.java": '''package android.graphics;public class Paint {
 public static final int ANTI_ALIAS_FLAG=1;public static int shadows;
 public Paint(int flags){}public void setColor(int c){}
 public void setShadowLayer(float r,float x,float y,int color){shadows++;}
}''',
        "android/graphics/Path.java": '''package android.graphics;public class Path {
 public enum Direction {CW}public void rewind(){}public void addRoundRect(RectF r,float[] f,Direction d){}
}''',
        "android/graphics/Canvas.java": '''package android.graphics;public class Canvas {
 public int draws;public void drawRoundRect(RectF r,float x,float y,Paint p){draws++;}
 public void drawPath(Path p,Paint paint){draws++;}
}''',
        "org/telegram/messenger/AndroidUtilities.java": '''package org.telegram.messenger;
public class AndroidUtilities {public static float density=1f;public static float dpf2(float x){return x*density;}}''',
        "org/telegram/ui/ActionBar/Theme.java": '''package org.telegram.ui.ActionBar;public class Theme {
 public static int key_windowBackgroundWhite=1;public static int getColor(int k,Object p){return 0xffffffff;}
 public static int multAlpha(int c,float a){return ((int)((c>>>24)*a)<<24)|(c&0xffffff);}
}''',
        "org/telegram/ui/Components/BlurredRecyclerView.java": '''package org.telegram.ui.Components;
import android.content.Context;import android.graphics.*;
public class BlurredRecyclerView {protected Object resourcesProvider;public int fallback;
 public BlurredRecyclerView(Context c){}
 public void drawBackgroundRect(Canvas c,RectF r,float a,float b,float alpha){fallback++;}
}''',
        "app/regram/appearance/AppearanceConfig.java": '''package app.regram.appearance;
public class AppearanceConfig {public static int radius=12;public static int sectionRadius(){return radius;}}''',
        "ShadowTest.java": '''import android.content.*;import android.graphics.*;
import app.regram.appearance.*;import org.telegram.messenger.AndroidUtilities;
public class ShadowTest {public static void main(String[] args){
 SectionCardRecyclerView view=new SectionCardRecyclerView(new Context());Canvas c=new Canvas();RectF r=new RectF();
 for(int i=0;i<10000;i++)view.drawBackgroundRect(c,r,12,8,1f);
 assert Paint.shadows==2;assert c.draws==20000;
 view.drawBackgroundRect(c,r,12,12,.5f);assert Paint.shadows==4;
 view.drawBackgroundRect(c,r,12,12,.5f);assert Paint.shadows==4;
 AndroidUtilities.density=2;view.drawBackgroundRect(c,r,12,12,.5f);assert Paint.shadows==6;
 view.setForceShadows(false);view.drawBackgroundRect(c,r,12,12,1);assert view.fallback==1;
 view.setForceShadows(true);AppearanceConfig.radius=0;view.drawBackgroundRect(c,r,12,12,1);assert view.fallback==2;
}}
'''
    }, "ShadowTest")
