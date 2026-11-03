"""Exercise actual TermuxUi with a small host-side Android view model (not device rendering)."""
import os
from pathlib import Path
import shutil
import subprocess

import pytest
from test_termux_bridge import ROOT, JAVA, ASSETS, STUBS


UI_STUBS = {
    "android/view/ViewParent.java": '''package android.view;public interface ViewParent {void requestDisallowInterceptTouchEvent(boolean value);}''',
    "android/view/View.java": '''package android.view;
public class View implements ViewParent {
 public interface OnClickListener{void onClick(View v);}public interface OnTouchListener{boolean onTouch(View v,MotionEvent e);}
 public static final int VISIBLE=0,GONE=8;public ViewParent parent;public float x,y,alpha=1;public boolean enabled=true;
 public int visibility,width=1000,height=2000;public OnClickListener click;public OnTouchListener touch;
 public View(android.content.Context c){}public ViewParent getParent(){return parent;}
 public void setOnClickListener(OnClickListener l){click=l;}public void setOnTouchListener(OnTouchListener l){touch=l;}
 public boolean performClick(){if(enabled && click!=null)click.onClick(this);return true;}
 public void requestDisallowInterceptTouchEvent(boolean value){}public void setX(float v){x=v;}public void setY(float v){y=v;}
 public float getX(){return x;}public float getY(){return y;}public int getWidth(){return width;}public int getHeight(){return height;}
 public void setPadding(int a,int b,int c,int d){}public void setBackground(Object b){}public void setElevation(float e){}
 public void setEnabled(boolean e){enabled=e;}public void setAlpha(float a){alpha=a;}public void setVisibility(int v){visibility=v;}
 public void measure(int w,int h){width=w;}public int getMeasuredHeight(){return 100;}
 public static class MeasureSpec{public static final int EXACTLY=1,UNSPECIFIED=0;public static int makeMeasureSpec(int s,int m){return s;}}
}''',
    "android/view/ViewGroup.java": '''package android.view;public class ViewGroup extends View {
 public java.util.List<View> children=new java.util.ArrayList<>();public ViewGroup(android.content.Context c){super(c);}
 public void addView(View v){children.add(v);v.parent=this;}public void addView(View v,Object lp){addView(v);}
 public void removeView(View v){children.remove(v);v.parent=null;}public int getChildCount(){return children.size();}public View getChildAt(int i){return children.get(i);}}
''',
    "android/view/Window.java": '''package android.view;public class Window {public ViewGroup decor=new android.widget.FrameLayout(new android.content.Context());public View getDecorView(){return decor;}}''',
    "android/view/MotionEvent.java": '''package android.view;public class MotionEvent {public static final int ACTION_DOWN=0,ACTION_MOVE=2,ACTION_UP=1;public int getActionMasked(){return 0;}public float getRawX(){return 0;}public float getRawY(){return 0;}}''',
    "android/view/Gravity.java": '''package android.view;public class Gravity {public static final int CENTER=17,TOP=48,BOTTOM=80,RIGHT=5,LEFT=3,START=8388611,END=8388613,CENTER_HORIZONTAL=1,CENTER_VERTICAL=16;}''',
    "android/widget/FrameLayout.java": '''package android.widget;public class FrameLayout extends android.view.ViewGroup {
 public FrameLayout(android.content.Context c){super(c);}public static class LayoutParams {
 public LayoutParams(int w,int h){}public LayoutParams(int w,int h,int g){}public void setMargins(int a,int b,int c,int d){}}}''',
    "android/widget/LinearLayout.java": '''package android.widget;public class LinearLayout extends android.view.ViewGroup {
 public static final int VERTICAL=1;public LinearLayout(android.content.Context c){super(c);}public void setOrientation(int o){}public void setGravity(int g){}
 public static class LayoutParams{public int topMargin;public LayoutParams(int w,int h){}public LayoutParams(int w,int h,float weight){}}}''',
    "android/widget/ScrollView.java": '''package android.widget;public class ScrollView extends android.view.ViewGroup{public ScrollView(android.content.Context c){super(c);}}''',
    "android/widget/TextView.java": '''package android.widget;public class TextView extends android.view.View {public CharSequence text="",hint="";
 public TextView(android.content.Context c){super(c);}public void setText(CharSequence t){text=t;}public CharSequence getText(){return text;}
 public void setTextColor(int c){}public void setTextSize(float s){}public void setTypeface(Object t){}public void setMaxWidth(int w){}public void setGravity(int g){}
 public void setHint(CharSequence h){hint=h;}public void setHintTextColor(int c){}}
''',
    "android/widget/EditText.java": '''package android.widget;public class EditText extends TextView {public EditText(android.content.Context c){super(c);}public void setSingleLine(){}}''',
    "android/widget/Switch.java": '''package android.widget;public class Switch extends TextView {
 public interface OnCheckedChangeListener {void onCheckedChanged(Switch view,boolean value);}public boolean checked;private OnCheckedChangeListener listener;
 public Switch(android.content.Context c){super(c);}public void setChecked(boolean v){checked=v;if(listener!=null)listener.onCheckedChanged(this,v);}
 public void setOnCheckedChangeListener(OnCheckedChangeListener l){listener=l;}}''',
    "android/widget/SeekBar.java": '''package android.widget;public class SeekBar extends android.view.View {
 public interface OnSeekBarChangeListener{void onProgressChanged(SeekBar b,int p,boolean user);void onStartTrackingTouch(SeekBar b);void onStopTrackingTouch(SeekBar b);}
 private int max,progress;private OnSeekBarChangeListener listener;public SeekBar(android.content.Context c){super(c);}
 public void setMax(int v){max=v;}public int getMax(){return max;}public int getProgress(){return progress;}
 public void setProgress(int v){progress=v;if(listener!=null)listener.onProgressChanged(this,v,false);}
 public void simulateDrag(int v){progress=v;listener.onProgressChanged(this,v,true);listener.onStopTrackingTouch(this);}
 public void setOnSeekBarChangeListener(OnSeekBarChangeListener l){listener=l;}}
''',
    "android/graphics/Color.java": '''package android.graphics;public class Color {public static final int WHITE=-1;public static int parseColor(String c){if(!c.startsWith("#"))throw new IllegalArgumentException();return (int)Long.parseLong(c.substring(1),16);}}''',
    "android/graphics/Typeface.java": '''package android.graphics;public class Typeface {public static final Typeface MONOSPACE=new Typeface();}''',
    "android/graphics/drawable/GradientDrawable.java": '''package android.graphics.drawable;public class GradientDrawable {public void setColor(int c){}public void setCornerRadius(float r){}}''',
    "android/app/Activity.java": '''package android.app;public class Activity extends android.content.Context {
 public android.view.Window window=new android.view.Window();public android.view.Window getWindow(){return window;}
 public boolean isFinishing(){return false;}public boolean isDestroyed(){return false;}
 public android.content.res.Resources getResources(){return new android.content.res.Resources();}}''',
    "org/telegram/messenger/AndroidUtilities.java": '''package org.telegram.messenger;public class AndroidUtilities {
 public static float density=1;public static int dp(float v){return (int)v;}public static String clipboard;
 public static final java.util.List<Runnable> delayed=new java.util.ArrayList<>();
 public static void runOnUIThread(Runnable r){r.run();}public static void runOnUIThread(Runnable r,long delay){delayed.add(r);}
 public static void addToClipboard(String s){clipboard=s;}}''',
    "org/telegram/messenger/R.java": '''package org.telegram.messenger;public class R {public static class raw{public static int info=1,done=2;}}''',
    "org/telegram/ui/Components/BulletinFactory.java": '''package org.telegram.ui.Components;public class BulletinFactory {public static BulletinFactory of(Object f){return new BulletinFactory();}public BulletinFactory createSimpleBulletin(int i,String t){return this;}public BulletinFactory createErrorBulletin(String t){return this;}public void show(){}}''',
}


def test_actual_native_panel_events_updates_isolation_and_cleanup(tmp_path):
    javac, java = shutil.which("javac"), shutil.which("java")
    jars = list((Path(os.environ.get("GRADLE_USER_HOME", Path.home() / ".gradle")) / "caches/modules-2/files-2.1/org.json/json").glob("*/*/*.jar"))
    if not java or not javac or not jars:
        pytest.skip("host JDK and cached org.json dependency required")
    files = dict(STUBS)
    files.update(UI_STUBS)
    for path in JAVA.glob("*.java"):
        files["app/regram/termux/" + path.name] = path.read_text()
    files["app/regram/termux/UiTest.java"] = r'''package app.regram.termux;
import java.net.*;import java.io.*;import org.json.*;import android.view.*;import android.widget.*;
public class UiTest {
 static JSONObject msg(String cmd)throws Exception{return new JSONObject().put("cmd",cmd);}
 public static void main(String[] args)throws Exception {
  TermuxBridge bridge=TermuxBridge.getInstance();int port;try(ServerSocket r=new ServerSocket(0)){port=r.getLocalPort();}
  TermuxBridge.settings().edit().putString("port",String.valueOf(port)).putBoolean("enabled",true).commit();
  android.app.Activity activity=new android.app.Activity();bridge.attach(activity);
  java.lang.reflect.Field field=TermuxBridge.class.getDeclaredField("ui");field.setAccessible(true);TermuxUi ui=(TermuxUi)field.get(bridge);
  TermuxBridge.Session owner=bridge.new Session(new Socket(),0);owner.sid="owner";
  TermuxBridge.Session foreign=bridge.new Session(new Socket(),0);foreign.sid="foreign";
  ViewGroup decor=(ViewGroup)activity.getWindow().getDecorView();
  ui.command(owner,msg("show").put("text","first").put("timeout",1));assert decor.getChildCount()==1;
  ui.command(owner,msg("show").put("text","second"));org.telegram.messenger.AndroidUtilities.delayed.get(0).run();
  assert decor.getChildCount()==1 && ((TextView)decor.getChildAt(0)).getText().equals("second");
  ui.command(owner,msg("append").put("text","third\nfourth").put("max_lines",2));assert ((TextView)decor.getChildAt(0)).getText().equals("third\nfourth");
  decor.getChildAt(0).performClick();assert decor.getChildCount()==0;
  ui.command(owner,msg("ball"));assert decor.getChildCount()==1;ui.command(owner,msg("ball").put("off",true));assert decor.getChildCount()==0;
  JSONArray items=new JSONArray().put(new JSONObject().put("type","button").put("id","b").put("text","Click"))
   .put(new JSONObject().put("type","toggle").put("id","t"))
   .put(new JSONObject().put("type","slider").put("id","s").put("text","Count").put("min",10).put("max",30).put("step",2))
   .put(new JSONObject().put("type","input").put("id","i"))
   .put(new JSONObject().put("type","row").put("items",new JSONArray().put(new JSONObject().put("type","text").put("id","r").put("text","row"))));
  ui.command(owner,msg("panel").put("id","p").put("items",items));assert ui.panelCount()==1;
  ViewGroup root=(ViewGroup)decor.getChildAt(0);ViewGroup body=(ViewGroup)((ViewGroup)root.getChildAt(1)).getChildAt(0);
  Switch toggle=(Switch)body.getChildAt(1);SeekBar slider=(SeekBar)((ViewGroup)body.getChildAt(2)).getChildAt(1);
  EditText input=(EditText)((ViewGroup)body.getChildAt(3)).getChildAt(0);
  try(Socket events=new Socket("127.0.0.1",port)) {
   events.setSoTimeout(3000);PrintWriter w=new PrintWriter(events.getOutputStream(),true);BufferedReader read=new BufferedReader(new InputStreamReader(events.getInputStream()));
   w.println(msg("subscribe").put("token",TermuxBridge.settings().getString("token","")).put("sid","owner"));assert new JSONObject(read.readLine()).getBoolean("ok");
   body.getChildAt(0).performClick();assert new JSONObject(read.readLine()).getString("event").equals("click");
   toggle.setChecked(true);assert new JSONObject(read.readLine()).getBoolean("value");
   slider.simulateDrag(3);JSONObject slide=new JSONObject(read.readLine());assert slide.getString("event").equals("slide") && slide.getInt("value")==16;
   read.readLine(); // final slide event on release
   input.setText("hello");((ViewGroup)body.getChildAt(3)).getChildAt(1).performClick();assert new JSONObject(read.readLine()).getString("value").equals("hello");assert input.getText().length()==0;
   ui.command(owner,msg("set").put("panel","p").put("id","t").put("value",false));assert !toggle.checked;
   ui.command(owner,msg("set").put("panel","p").put("id","s").put("value",22));assert slider.getProgress()==6;
   events.setSoTimeout(100);try{read.readLine();throw new AssertionError("programmatic update emitted event");}catch(SocketTimeoutException expected){}events.setSoTimeout(3000);
   ui.command(owner,msg("set").put("panel","p").put("id","i").put("enabled",false));assert !input.enabled && !((ViewGroup)body.getChildAt(3)).getChildAt(1).enabled;
   try{ui.command(foreign,msg("set").put("panel","p").put("id","t").put("value",true));throw new AssertionError();}catch(IllegalArgumentException expected){}
   ui.command(foreign,msg("panel").put("id","p").put("keep",true));assert ui.panelCount()==2;
   ((ViewGroup)root.getChildAt(0)).getChildAt(1).performClick();assert new JSONObject(read.readLine()).getString("event").equals("close");assert ui.panelCount()==1;
  }
  ui.cleanupOwner("foreign");assert ui.panelCount()==1;ui.clearAll();assert decor.getChildCount()==0;
  // A rejected oversized spec must not attach a partial view or replace an existing panel.
  JSONArray tooMany=new JSONArray();for(int i=0;i<129;i++)tooMany.put(new JSONObject().put("text","x"));
  try{ui.command(owner,msg("panel").put("id","huge").put("items",tooMany));throw new AssertionError();}catch(IllegalArgumentException expected){}
  assert decor.getChildCount()==0;
  ui.command(owner,msg("panel").put("id","locked"));bridge.locked();assert decor.getChildCount()==0;
  bridge.detach(activity);
 }
}'''
    paths = []
    for relative, source in files.items():
        path = tmp_path / relative
        path.parent.mkdir(parents=True, exist_ok=True)
        path.write_text(source)
        paths.append(str(path))
    compiled = subprocess.run([javac, "-encoding", "UTF-8", "-cp", str(jars[0]), "-d", str(tmp_path), *paths], capture_output=True, text=True)
    assert compiled.returncode == 0, compiled.stderr
    run = subprocess.run([java, "-ea", f"-Dassets={ASSETS.parent}", "-cp", str(tmp_path) + os.pathsep + str(jars[0]), "app.regram.termux.UiTest"], capture_output=True, text=True, timeout=30)
    assert run.returncode == 0, run.stdout + run.stderr
