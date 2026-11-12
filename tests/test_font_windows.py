from pathlib import Path
from test_native_visual_settings import compile_run, method

ROOT=Path(__file__).resolve().parents[1]


def test_real_window_binding_reuses_and_removes_observers_preserving_previews(tmp_path):
    source=(ROOT/'TMessagesProj/src/main/java/app/regram/fonts/FontWindows.java').read_text()
    actual='\n'.join(method(source, signature) for signature in (
        'public static void install(View root)', 'private static final class Binding', 'private static void apply(View view)'))
    compile_run(tmp_path, {
        'android/graphics/Typeface.java':'package android.graphics; public class Typeface {}',
        'WindowTest.java': '''
import java.util.*;import android.graphics.Typeface;
class R {static class id {static final int regram_font_window=1,regram_font_preview=2;}}
class ApplicationFonts {
 static boolean enabled;
 static final Typeface DEFAULT=new Typeface(),UI=new Typeface(),CODE=new Typeface();
 static Typeface ui(int weight,boolean italic){return enabled?UI:null;}
 static Typeface replacement(Typeface current){return current==DEFAULT?UI:null;}
}
class ViewTreeObserver {
 interface OnGlobalLayoutListener {void onGlobalLayout();}
 List<OnGlobalLayoutListener> listeners=new ArrayList<>();
 void addOnGlobalLayoutListener(OnGlobalLayoutListener l){listeners.add(l);}
 void removeOnGlobalLayoutListener(OnGlobalLayoutListener l){listeners.remove(l);}
 boolean alive=true;boolean isAlive(){return alive;}
 void layout(){for(OnGlobalLayoutListener l:new ArrayList<>(listeners))l.onGlobalLayout();}
}
class View {
 interface OnAttachStateChangeListener {void onViewAttachedToWindow(View v);void onViewDetachedFromWindow(View v);}
 Map<Integer,Object> tags=new HashMap<>();List<OnAttachStateChangeListener> attach=new ArrayList<>();
 ViewTreeObserver observer=new ViewTreeObserver();
 Object getTag(int id){return tags.get(id);}void setTag(int id,Object value){tags.put(id,value);}
 ViewTreeObserver getViewTreeObserver(){return observer;}
 void addOnAttachStateChangeListener(OnAttachStateChangeListener l){attach.add(l);}
 void detach(){for(OnAttachStateChangeListener l:attach)l.onViewDetachedFromWindow(this);observer=new ViewTreeObserver();}
 void attach(){for(OnAttachStateChangeListener l:attach)l.onViewAttachedToWindow(this);}
}
class ViewGroup extends View {
 List<View> children=new ArrayList<>();int getChildCount(){return children.size();}View getChildAt(int i){return children.get(i);}
}
class TextView extends View {
 Typeface font=ApplicationFonts.DEFAULT;int changes;
 Typeface getTypeface(){return font;}void setTypeface(Typeface f){font=f;changes++;}
}
class EditTextOutline extends TextView {}
class PaintTextOptionsView extends ViewGroup {}
public class WindowTest {
''' + actual + '''
 public static void main(String[] args){
  ViewGroup root=new ViewGroup();TextView regular=new TextView(),code=new TextView(),preview=new TextView();
  code.font=ApplicationFonts.CODE;preview.setTag(R.id.regram_font_preview,true);
  EditTextOutline media=new EditTextOutline();
  PaintTextOptionsView options=new PaintTextOptionsView();TextView option=new TextView();options.children.add(option);
  Collections.addAll(root.children,regular,code,preview,media,options);
  install(root);assert root.observer.listeners.isEmpty() && regular.changes==0;
  ApplicationFonts.enabled=true;install(root);
  assert root.observer.listeners.size()==1 && root.attach.size()==1;
  assert regular.font==ApplicationFonts.UI && regular.changes==1;
  assert code.font==ApplicationFonts.CODE && preview.changes==0 && media.changes==0 && option.changes==0;
  ViewTreeObserver floating=root.observer;root.observer=new ViewTreeObserver();
  root.observer.listeners.addAll(floating.listeners);floating.alive=false;root.attach(); // Android merges floating observers
  install(root);root.observer.layout();assert root.observer.listeners.size()==1 && regular.changes==1;
  TextView added=new TextView();root.children.add(added);root.observer.layout();assert added.font==ApplicationFonts.UI;
  ViewTreeObserver old=root.observer;root.detach();assert old.listeners.isEmpty();
  root.attach();assert root.observer.listeners.size()==1;
  install(root);assert root.observer.listeners.size()==1 && root.attach.size()==1;
  TextView later=new TextView();root.children.add(later);root.observer.layout();assert later.font==ApplicationFonts.UI;
 }
}
'''},'WindowTest')
