"""Retained chip renderer on host API doubles: work counts, not device timing."""
from pathlib import Path
import importlib.util
import shutil
import subprocess
import zipfile
import pytest

ROOT=Path(__file__).resolve().parents[1]
JAVA=ROOT/'TMessagesProj/src/main/java'


@pytest.fixture(scope='module')
def classes(tmp_path_factory):
    if not shutil.which('javac'):pytest.skip('JDK required')
    out=tmp_path_factory.mktemp('style-work-counts')
    sources={
      'android/graphics/Paint.java':'''package android.graphics;public class Paint {public static int ANTI_ALIAS_FLAG=1;public enum Style {FILL,STROKE}public Paint(){}public Paint(int i){}public void setColor(int c){}public void setAlpha(int a){}public void setColorFilter(ColorFilter f){}public void setStyle(Style s){}public void setStrokeWidth(float f){}}''',
      'android/graphics/ColorFilter.java':'package android.graphics;public class ColorFilter {}',
      'android/graphics/PixelFormat.java':'package android.graphics;public class PixelFormat {public static int TRANSLUCENT=0;}',
      'android/graphics/Path.java':'package android.graphics;public class Path {public static int builds;public enum Direction {CW}public void rewind(){}public void addRoundRect(RectF r,float[] a,Direction d){builds++;}}',
      'android/graphics/RectF.java':'package android.graphics;public class RectF {public float left,top,right,bottom;public void set(float l,float t,float r,float b){left=l;top=t;right=r;bottom=b;}}',
      'android/graphics/Canvas.java':'package android.graphics;public class Canvas {public void drawPath(Path p,Paint b){}public void drawLine(float a,float b,float c,float d,Paint p){}}',
      'android/graphics/drawable/Drawable.java':'package android.graphics.drawable;public abstract class Drawable {public abstract void draw(android.graphics.Canvas c);public abstract void setAlpha(int a);public abstract void setColorFilter(android.graphics.ColorFilter f);public abstract int getOpacity();}',
      'android/graphics/drawable/RippleDrawable.java':'''package android.graphics.drawable;public class RippleDrawable extends Drawable {public static int creates;public RippleDrawable(android.content.res.ColorStateList c,Drawable d,Drawable m){creates++;}public void setColor(android.content.res.ColorStateList c){}public void draw(android.graphics.Canvas c){}public void setAlpha(int a){}public void setColorFilter(android.graphics.ColorFilter f){}public int getOpacity(){return 0;}}''',
      'android/content/res/ColorStateList.java':'package android.content.res;public class ColorStateList {public static ColorStateList valueOf(int c){return new ColorStateList();}}',
      'android/view/View.java':'package android.view;public class View {private android.graphics.drawable.Drawable foreground;public android.graphics.drawable.Drawable getForeground(){return foreground;}public void setForeground(android.graphics.drawable.Drawable d){foreground=d;}}',
      'androidx/core/graphics/ColorUtils.java':'''package androidx.core.graphics;public class ColorUtils {public static int blendARGB(int a,int b,float f){return f==0?a:f==1?b:(int)(a+(b-(long)a)*f);}}''',
      'org/telegram/messenger/AndroidUtilities.java':'package org.telegram.messenger;public class AndroidUtilities {public static float density=1;public static float dpf2(float f){return f*density;}}',
      'org/telegram/ui/ActionBar/Theme.java':'''package org.telegram.ui.ActionBar;public class Theme {public interface ResourcesProvider {}public static boolean night;public static int key_divider=-8,key_featuredStickers_addButton=1,key_featuredStickers_buttonText=2,key_windowBackgroundWhiteBlueText=3,key_listSelector=4;public static Object getActiveTheme(){return Theme.class;}public static boolean isCurrentThemeDark(){return night;}public static int getColor(int key,ResourcesProvider rp){return 0xff447788+key;}public static int multAlpha(int color,float alpha){return (color&0xffffff)|(Math.round((color>>>24)*alpha)<<24);}}''',
      'app/regram/appearance/QuickTheme.java':'package app.regram.appearance;public class QuickTheme {public static int folderColor(int c){return c;}public static int foldersAlpha(){return 255;}public static int folderRadius(int r){return r;}}',
      'tw/nekomimi/nekogram/NekoConfig.java':'package tw.nekomimi.nekogram;public class NekoConfig {public static Item regramQuickTheme=new Item(),regramQuickFolderRadius=new Item();public static class Item {public boolean Bool(){return false;}public int Int(){return -1;}}}',
      'app/regram/appearance/PluginStyles.java':'''package app.regram.appearance;public class PluginStyles {
        public static java.util.Map<String,Object> map=new java.util.HashMap<>();
        public static boolean flag(String k,boolean d){return map.get(k) instanceof Boolean?(Boolean)map.get(k):d;}
        public static int number(String k,int d,int a,int b){return PluginStyleRules.clamp(map.get(k) instanceof Integer?(Integer)map.get(k):d,a,b);}
        public static int gap(){return number("gap",6,0,24);}public static int barHeight(){return 48;}
      }''',
      'WorkTest.java':'''import app.regram.appearance.*;import android.graphics.*;import org.telegram.ui.ActionBar.*;
      public class WorkTest {
        static void draw(NativeChipFolders chip,android.view.View view,float width,float progress,boolean rtl){chip.draw(view,new Canvas(),width,48,progress,0,0,true,false,rtl,null,123);}
        public static void main(String[] args)throws Exception{
          {
            NativeChipFolders chip=new NativeChipFolders();android.view.View view=new android.view.View();
            draw(chip,view,100,0,false);assert Path.builds==1;
            for(int i=0;i<10000;i++)draw(chip,view,100,0,false);assert Path.builds==1;
            draw(chip,view,120,0,false);assert Path.builds==2;
            draw(chip,view,120,1,false);assert Path.builds==3;
            draw(chip,view,120,1,true);assert Path.builds==3;
            PluginStyles.map.put("radius_active",8);draw(chip,view,120,1,true);assert Path.builds==4;
            android.graphics.drawable.Drawable ripple=view.getForeground();assert android.graphics.drawable.RippleDrawable.creates==1;
            chip.clearRipple(view);assert view.getForeground()==null;
            draw(chip,view,120,1,true);assert view.getForeground()==ripple&&Path.builds==4;
            AndroidDensity();draw(chip,view,120,1,true);assert Path.builds==5;
          }
        }
        static void AndroidDensity(){org.telegram.messenger.AndroidUtilities.density=2;}
      }'''
    }
    for name in ('NativeChipFolders','PluginStyleRules'):
        sources[f'app/regram/appearance/{name}.java']=(JAVA/f'app/regram/appearance/{name}.java').read_text()
    paths=[]
    for rel,body in sources.items():
        path=out/rel;path.parent.mkdir(parents=True,exist_ok=True);path.write_text(body);paths.append(str(path))
    subprocess.run(['javac','-d',str(out),*paths],check=True,capture_output=True,text=True)
    return str(out)


def test_actual_chip_bounded_work_counts(classes):
    subprocess.run(['java','-ea','-cp',classes,'WorkTest'],check=True,capture_output=True,text=True,timeout=20)


def test_apk_size_audit_is_read_only_and_duplicates_are_not_deletions(tmp_path):
    spec=importlib.util.spec_from_file_location('audit_size',ROOT/'tools/audit_apk_size.py')
    mod=importlib.util.module_from_spec(spec);spec.loader.exec_module(mod)
    apk=tmp_path/'fixture.apk'
    with zipfile.ZipFile(apk,'w') as out:
        out.writestr('classes.dex',b'dex data')
        out.writestr('res/one.png',b'image data')
        out.writestr('res/two.png',b'image data')
        out.writestr('assets/empty',b'')
    before=apk.read_bytes();report=mod.audit(apk)
    assert apk.read_bytes()==before
    assert report['groups']['dex']['expanded_bytes']==8
    assert report['duplicates'][0]['paths']==['res/one.png','res/two.png']
    assert 'NOT safe deletions' in report['warning']


def test_chip_hot_path_uses_single_wave():
    helper=(JAVA/'tw/nekomimi/nekogram/helpers/MainTabsHelper.java').read_text()
    assert 'PluginStyles' not in helper
    folders=(JAVA/'org/telegram/ui/Components/FilterTabsView.java').read_text()
    assert 'tabs.get(index) != currentTab' in folders  # reorder safety fallback
    assert 'scaleXFromWave(animation, wave)' in folders
    assert 'scaleYFromWave(animation, wave)' in folders
