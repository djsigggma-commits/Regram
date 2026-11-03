"""Host regression for actual navigation geometry; not a device/pixel test."""
from pathlib import Path
import shutil
import subprocess
import pytest

ROOT=Path(__file__).resolve().parents[1]
JAVA=ROOT/'TMessagesProj/src/main/java'


def test_actual_stock_navigation_height_radius_and_blur(tmp_path):
    if not shutil.which('javac'):pytest.skip('JDK needed')
    files={
      'android/graphics/RectF.java':'package android.graphics; public class RectF {public float top,bottom;public void set(float a,float b,float c,float d){top=b;bottom=d;}}',
      'android/graphics/drawable/Drawable.java':'package android.graphics.drawable;public class Drawable {}',
      'android/graphics/drawable/InsetDrawable.java':'package android.graphics.drawable;public class InsetDrawable extends Drawable {public InsetDrawable(Drawable d,int a,int b,int c,int e){}}',
      'android/graphics/drawable/ShapeDrawable.java':'package android.graphics.drawable;public class ShapeDrawable extends Drawable {public P getPaint(){return new P();}public static class P {public void setShadowLayer(float a,float b,float c,int d){}}}',
      'android/view/ViewGroup.java':'package android.view;public class ViewGroup {public static class LayoutParams {public int height;}}',
      'android/view/View.java':'''package android.view;public class View {
        public int w=400,h=800,b;public ViewGroup.LayoutParams lp=new ViewGroup.LayoutParams();
        public int getMeasuredWidth(){return w;}public int getMeasuredHeight(){return h;}
        public ViewGroup.LayoutParams getLayoutParams(){return lp;}public void setLayoutParams(ViewGroup.LayoutParams p){lp=p;}
        public int getPaddingBottom(){return b;}public int getPaddingTop(){return 0;}public int getPaddingLeft(){return 0;}public int getPaddingRight(){return 0;}
        public void setPadding(int l,int t,int r,int bottom){b=bottom;}
      }''',
      'android/widget/TextView.java':'package android.widget;public class TextView extends android.view.View {public void setIncludeFontPadding(boolean b){}public void setLetterSpacing(float f){}}',
      'me/vkryl/android/animator/BoolAnimator.java':'package me.vkryl.android.animator;public class BoolAnimator {public void setDuration(long v){}public void setInterpolator(Object o){}public void setValue(boolean a,boolean b){}}',
      'org/telegram/messenger/AndroidUtilities.java':'package org.telegram.messenger;public class AndroidUtilities {public static int dp(float v){return Math.round(v);}public static float dpf2(float v){return v;}public static float lerp(float a,float b,float f){return a+(b-a)*f;}}',
      'org/telegram/ui/ActionBar/Theme.java':'package org.telegram.ui.ActionBar;public class Theme {public interface ResourcesProvider {}public static final int key_windowBackgroundWhite=0;public static int getColor(int k,ResourcesProvider r){return 0;}public static int multAlpha(int c,float f){return c;}public static android.graphics.drawable.ShapeDrawable createCircleDrawable(int a,int b){return new android.graphics.drawable.ShapeDrawable();}public static android.graphics.drawable.ShapeDrawable createRoundRectDrawable(int a,int b){return createCircleDrawable(a,b);}}',
      'org/telegram/ui/Components/CubicBezierInterpolator.java':'package org.telegram.ui.Components;public class CubicBezierInterpolator {public static Object Emphasized,EmphasizedAccelerate;}',
      'org/telegram/ui/Components/LayoutHelper.java':'package org.telegram.ui.Components;public class LayoutHelper {public static int MATCH_PARENT=-1;public static android.view.ViewGroup.LayoutParams createFrame(int a,float b,int c,float d,float e,float f,float g){return new android.view.ViewGroup.LayoutParams();}}',
      'org/telegram/ui/Components/blur3/drawable/color/BlurredBackgroundProviderBuilder.java':'package org.telegram.ui.Components.blur3.drawable.color;public class BlurredBackgroundProviderBuilder {public BlurredBackgroundProviderBuilder setStrokeColorTop(int a,int b){return this;}public BlurredBackgroundProviderBuilder setStrokeColorBottom(int a,int b){return this;}public BlurredBackgroundProviderBuilder setStrokeWidth(float a,float b){return this;}}',
      'org/telegram/ui/MainTabsLayout.java':'package org.telegram.ui;public class MainTabsLayout extends android.view.View {public static boolean floating;public int max;public static boolean isBottomNavigationFloating(){return floating;}public void setMaxWidth(int w){max=w;}}',
      'app/regram/appearance/AppearanceConfig.java':'package app.regram.appearance;public class AppearanceConfig {public static boolean m3,ios;public static boolean newNavigationBarStyle(){return m3;}public static boolean iosNavigationBarStyle(){return ios;}}',
      'tw/nekomimi/nekogram/helpers/MainTabsHelper.java':'''package tw.nekomimi.nekogram.helpers;public class MainTabsHelper {
        public static int height=36,margin=4;public static int getMainTabsHeight(){return height;}
        public static int getMainTabsMargin(){return margin;}public static int getMainTabsHeightWithMargins(){return height+2*margin;}
        public static boolean isMainTabsHideTitleStyle(){return height==36;}public static int getTabsViewWidth(){return 320;}
      }''',
      'DockTest.java':'''import app.regram.appearance.*;import tw.nekomimi.nekogram.helpers.MainTabsHelper;import org.telegram.ui.MainTabsLayout;
      public class DockTest {public static void main(String[] args){
        MainTabsLayout layout=new MainTabsLayout();android.graphics.RectF rect=new android.graphics.RectF();
        for(int size:new int[]{36,48,56,60})for(int margin:new int[]{4,8}){
          MainTabsHelper.height=size;MainTabsHelper.margin=margin;
          int bar=size;assert MainTabsUiHelper.getGlassBarHeightDp()==bar;
          assert MainTabsUiHelper.getTabsViewHeightDp()==bar+2*margin;
          assert MainTabsUiHelper.getTabsViewHeight(30)==bar+2*margin;
          assert MainTabsUiHelper.getBackgroundRadius()==Math.round(bar/2f);
          MainTabsUiHelper.setBlurBounds(rect,layout,30);assert rect.bottom-rect.top==bar;
          assert MainTabsUiHelper.getTabsFabOffsetDp()==bar+margin;
          MainTabsUiHelper.applyTabsLayoutStyle(layout,320);assert layout.max==320;
        }
        AppearanceConfig.m3=true;
        assert MainTabsUiHelper.getTabsViewHeightDp()==64;
        assert MainTabsUiHelper.getTabsViewHeight(30)==94;
        AppearanceConfig.m3=false;AppearanceConfig.ios=true;
        MainTabsUiHelper.applyTabsLayoutStyle(layout,320);assert layout.max==0;
      }}'''
    }
    files['app/regram/appearance/MainTabsUiHelper.java']=(JAVA/'app/regram/appearance/MainTabsUiHelper.java').read_text()
    paths=[]
    for name,body in files.items():
        p=tmp_path/name;p.parent.mkdir(parents=True,exist_ok=True);p.write_text(body);paths.append(str(p))
    subprocess.run(['javac','-d',str(tmp_path),*paths],check=True,capture_output=True,text=True)
    subprocess.run(['java','-ea','-cp',str(tmp_path),'DockTest'],check=True,capture_output=True,text=True)


def test_bottom_folders_clip_draw_and_new_taps_without_mutating_shared_paint():
    dialogs=(JAVA/'org/telegram/ui/DialogsActivity.java').read_text()
    assert 'getMeasuredWidth(), getBottomFoldersDrawEnd())' in dialogs
    assert 'getBottomFoldersHeight() > 0 && !app.regram.appearance.QuickTheme.hideNavigationBackdrop()' in dialogs
    assert '? fragmentView.getHeight() : getBottomFoldersContentEnd()' in dialogs
    tabs_activity=(JAVA/'org/telegram/ui/MainTabsActivity.java').read_text()
    assert 'tabletLayout || app.regram.appearance.QuickTheme.hideNavigationBackdrop()' in tabs_activity
    assert 'canvas.drawRect(0, getBottomFoldersContentEnd(), getWidth(), getHeight(), bottomFoldersDockPaint)' in dialogs
    assert 'point[1] >= getBottomFoldersContentEnd()' in dialogs
    assert 'private final Paint bottomFoldersDockPaint = new Paint()' in dialogs
    tabs=(JAVA/'org/telegram/ui/MainTabsLayout.java').read_text()
    assert 'PluginStyles' not in tabs
    activity=(JAVA/'org/telegram/ui/MainTabsActivity.java').read_text()
    assert 'nativeSearchButton' not in activity


def test_removed_compact_panel_cannot_change_navigation():
    for path in ('org/telegram/ui/MainTabsActivity.java', 'org/telegram/ui/MainTabsLayout.java',
                 'app/regram/appearance/MainTabsUiHelper.java', 'tw/nekomimi/nekogram/helpers/MainTabsHelper.java'):
        text = (JAVA/path).read_text()
        assert 'PluginStyles' not in text
        assert 'nativeSearchButton' not in text
    # Keep the separate stock navigation settings; only the removed overrides disappear.
    helper = (JAVA/'app/regram/appearance/MainTabsUiHelper.java').read_text()
    assert 'AppearanceConfig.newNavigationBarStyle()' in helper
    assert 'AppearanceConfig.iosNavigationBarStyle()' in helper
