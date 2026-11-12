"""Executable import/UI policy guards. Android font decoding and rendering require a device."""
from pathlib import Path
import xml.etree.ElementTree as ET

from test_native_visual_settings import compile_run, method

ROOT = Path(__file__).resolve().parents[1]
JAVA = ROOT / 'TMessagesProj/src/main/java'
FONTS = JAVA / 'app/regram/fonts'


def test_bounded_font_copy_and_display_names(tmp_path):
    compile_run(tmp_path, {'app/regram/fonts/FontFileImport.java': (FONTS/'FontFileImport.java').read_text(),
                          'ImportTest.java': '''
import app.regram.fonts.FontFileImport;
import java.io.*;
public class ImportTest {
 static void rejected(byte[] data) throws Exception {
  try {FontFileImport.copy(new ByteArrayInputStream(data),new ByteArrayOutputStream());throw new AssertionError();}
  catch(IOException expected){}
 }
 public static void main(String[] args) throws Exception {
  for(int magic:new int[]{0x00010000,0x4f54544f,0x74746366,0x74727565}) {
   byte[] data={(byte)(magic>>24),(byte)(magic>>16),(byte)(magic>>8),(byte)magic,1,2,3};
   ByteArrayOutputStream out=new ByteArrayOutputStream();
   FontFileImport.copy(new ByteArrayInputStream(data),out);
   assert java.util.Arrays.equals(data,out.toByteArray());
  }
  rejected(new byte[0]); rejected(new byte[]{0,1,0}); rejected(new byte[]{'w','O','F','F'});
  class Sink extends OutputStream {int count;public void write(int b){count++;}
   public void write(byte[] b,int off,int len){count+=len;}}
  byte[] large=new byte[FontFileImport.MAX_BYTES];large[1]=1;
  Sink sink=new Sink();FontFileImport.copy(new ByteArrayInputStream(large),sink);
  assert sink.count==FontFileImport.MAX_BYTES;
  Sink bounded=new Sink();
  try {FontFileImport.copy(new SequenceInputStream(new ByteArrayInputStream(large),new ByteArrayInputStream(new byte[]{0})),bounded);throw new AssertionError();}
  catch(IOException expected){} assert bounded.count<=FontFileImport.MAX_BYTES;
  assert FontFileImport.displayName("Example.OTF").equals("Example");
  assert FontFileImport.displayName(null).equals("Font");
  assert FontFileImport.displayName("a/b\\\\c\\n.ttf").indexOf('/')==-1;
  assert FontFileImport.displayName("x".repeat(100)).length()==80;
 }
}
'''}, 'ImportTest')


def test_real_ui_font_selection_is_a_process_snapshot_and_preserves_special_fonts(tmp_path):
    source = (FONTS/'ApplicationFonts.java').read_text()
    methods='\n'.join(method(source, signature) for signature in (
        'public static synchronized Typeface ui(', 'public static Typeface forAsset(',
        'public static synchronized Typeface replacement(', 'public static void applyUi('))
    compile_run(tmp_path, {'UiFontTest.java': '''
import java.io.*;import java.util.*;
class ApplicationLoader {static Object applicationContext=new Object();}
class Build {static class VERSION {static int SDK_INT=35;}}
class FileLog {static void e(Exception e){}}
class Typeface {
 static final int NORMAL=0,BOLD=1,ITALIC=2;
 static final Map<String,Typeface> families=new HashMap<>();
 static final Typeface DEFAULT=create("sans-serif",0),DEFAULT_BOLD=create("sans-serif",1),SANS_SERIF=DEFAULT,MONOSPACE=create("monospace",0);
 final String name;final int weight;final boolean italic;
 Typeface(String n,int w,boolean i){name=n;weight=w;italic=i;}
 static Typeface create(String family,int style){return families.computeIfAbsent(family+style,k->new Typeface(family,(style&1)!=0?700:family.equals("sans-serif-medium")?500:400,(style&2)!=0));}
 static Typeface create(Typeface base,int weight,boolean italic){return new Typeface(base.name,weight,italic);}
 static Typeface create(Typeface base,int style){return create(base,(style&1)!=0?700:400,(style&2)!=0);}
 static Typeface createFromFile(File file){if(file.getName().contains("bad"))throw new RuntimeException();return new Typeface("imported",400,false);}
 int getWeight(){return weight;}boolean isBold(){return weight>=700;}boolean isItalic(){return italic;}
}
class Paint {Typeface face;Typeface getTypeface(){return face;}void setTypeface(Typeface f){face=f;}}
public class UiFontTest {
 static boolean initialized;static Typeface active;static String selection="";
 static final HashMap<Integer,Typeface> styles=new HashMap<>();static Set<Typeface> frameworkUi;
 static String selected(){return selection;}
 static File file(String key){return key.isEmpty()?null:new File(key);}
''' + methods + '''
 static void reset(){initialized=false;active=null;styles.clear();frameworkUi=null;}
 public static void main(String[] args) throws Exception {
  ApplicationLoader.applicationContext=null; assert ui(400,false)==null && !initialized;
  ApplicationLoader.applicationContext=new Object();assert ui(400,false)==null && initialized;
  File good=File.createTempFile("font-", ".ttf");selection=good.getAbsolutePath();
  assert ui(400,false)==null; // setting changes do not partially restyle a running process
  reset();Typeface normal=ui(400,false);assert normal!=null && normal.name.equals("imported");
  assert ui(400,false)==normal;
  assert forAsset("fonts/rmedium.ttf").weight==500;
  assert forAsset("fonts/ritalic.ttf").italic;
  assert forAsset("fonts/rmono.ttf")==null && forAsset("fonts/nunito_extrabold.ttf")==null;
  assert forAsset("fonts/mw_bold.ttf")==null;
  assert replacement(null)==normal && replacement(Typeface.DEFAULT)==normal;
  assert replacement(Typeface.DEFAULT_BOLD).weight==700;
  assert replacement(Typeface.create("sans-serif-medium",0)).weight==500;
  assert replacement(Typeface.MONOSPACE)==null;
  assert replacement(Typeface.create("serif",0))==null;
  assert replacement(new Typeface("icons",400,false))==null;
  assert replacement(normal)==null; // don't reset an already-applied style on every layout
  Paint text=new Paint(),code=new Paint();code.face=Typeface.MONOSPACE;applyUi(text,code,null);
  assert text.face==normal && code.face==Typeface.MONOSPACE;
  selection="";assert ui(400,false)==normal;reset();assert ui(400,false)==null;
  selection=good.getAbsolutePath();reset();Build.VERSION.SDK_INT=24;
  assert ui(500,true).isBold() && ui(500,true).isItalic();
  selection=good.getAbsolutePath()+"missing";reset();assert ui(400,false)==null;
  good.delete();
 }
}
'''}, 'UiFontTest')


def test_catalog_order_visibility_system_toggle_and_nonempty_fallback(tmp_path):
    catalog = method((FONTS/'ApplicationFonts.java').read_text(), 'public static List<PaintTypeface> catalog(')
    compile_run(tmp_path, {'CatalogTest.java': '''
import java.util.*;
class PaintTypeface {
 final String key;PaintTypeface(String key){this.key=key;}String getKey(){return key;}
 static final PaintTypeface ROBOTO_MEDIUM=new PaintTypeface("roboto"),SERIF=new PaintTypeface("serif");
 static final List<PaintTypeface> BUILT_IN_FONTS=Arrays.asList(ROBOTO_MEDIUM,SERIF);
}
class FileLog {static void e(Exception e){}}
class JSONArray {
 String[] keys;
 JSONArray(String json){keys=json.isEmpty()?new String[0]:json.split(",");}
 int length(){return keys.length;}String optString(int i){return keys[i];}
}
public class CatalogTest {
 static boolean system=true;static String order="";static Set<String> hidden=new HashSet<>();
 static PaintTypeface custom=new PaintTypeface("custom"),platform=new PaintTypeface("platform");
 static boolean systemFonts(){return system;}
 static List<PaintTypeface> imported(){return Arrays.asList(custom);}
 static boolean hidden(String key){return hidden.contains(key);}
 static CatalogTest prefs(){return new CatalogTest();}String getString(String key,String def){return order;}
''' + catalog + '''
 public static void main(String[] args){
  List<PaintTypeface> all=Arrays.asList(PaintTypeface.ROBOTO_MEDIUM,PaintTypeface.SERIF,platform);
  assert catalog(all,true).size()==4;
  system=false;assert catalog(all,true).size()==3 && !catalog(all,true).contains(platform);
  hidden.add("roboto");assert !catalog(all,false).contains(PaintTypeface.ROBOTO_MEDIUM);
  assert catalog(all,true).contains(PaintTypeface.ROBOTO_MEDIUM); // settings still shows hidden entries
  order="custom,serif";List<PaintTypeface> result=catalog(all,true);
  assert result.get(0)==custom && result.get(1)==PaintTypeface.SERIF;
  order="";assert catalog(all,true).get(0)==PaintTypeface.ROBOTO_MEDIUM;
  hidden.add("serif");hidden.add("custom");assert catalog(all,false).size()==1;
  assert catalog(all,false).get(0)==PaintTypeface.ROBOTO_MEDIUM;
 }
}
'''}, 'CatalogTest')


def test_import_transaction_editor_resolution_and_global_wiring():
    source = (FONTS/'ApplicationFonts.java').read_text()
    imp = method(source, 'public static String importFont(')
    assert 'FontFileImport.copy(in, out)' in imp
    assert 'new Typeface.Builder(temp).build()' in imp and 'validated == Typeface.DEFAULT' in imp
    assert imp.index('validated == null') < imp.index('temp.renameTo(destination)') < imp.index('putString("imports"')
    assert 'finally' in imp and '!saved && destination != null' in imp
    assert 'UUID.fromString' in method(source, 'private static File file(')
    assert 'prefs().edit().remove("application").commit()' in method(source, 'public static synchronized void select(')
    editor = (JAVA/'org/telegram/ui/Components/Paint/PaintTypeface.java').read_text()
    find = method(editor, 'public static PaintTypeface find(')
    assert 'getAll()' in find and 'ApplicationFonts.imported()' in find and ' = get();' not in find
    assert 'editorAsset(AndroidUtilities.TYPEFACE_ROBOTO_MEDIUM)' in editor
    utilities = (JAVA/'org/telegram/messenger/AndroidUtilities.java').read_text()
    assert 'ApplicationFonts.forAsset(path)' in utilities and 'ApplicationFonts.ui(500, false)' in utilities
    theme = (JAVA/'org/telegram/ui/ActionBar/Theme.java').read_text()
    assert 'ApplicationFonts.applyUi(chat_msgTextPaint' in theme
    assert 'ApplicationFonts.applyUi(dialogs_messagePaint[0]' in theme
    for name in ('SimpleTextView','AlertDialog','BottomSheet'):
        assert 'app.regram.fonts.' in (JAVA/f'org/telegram/ui/ActionBar/{name}.java').read_text()
    windows = (FONTS/'FontWindows.java').read_text()
    assert 'regram_font_preview' in windows and 'addOnGlobalLayoutListener' in windows
    assert 'getDeclaredField' not in windows and 'OnPreDrawListener' not in windows


def test_fonts_settings_navigation_drag_import_and_localized_contract():
    settings = (JAVA/'app/regram/settings/FontsSettingsActivity.java').read_text()
    assert 'Intent.ACTION_OPEN_DOCUMENT' in settings
    assert 'Utilities.globalQueue.postRunnable' in settings
    assert 'ItemTouchHelper' in settings and 'ApplicationFonts.setOrder(fonts)' in settings
    assert 'ApplicationFonts.toggleHidden(font.getKey())' in settings
    assert 'AppRestartHelper.triggerRebirth' in settings
    assert 'ApplicationFonts.delete(font.getKey())' in settings
    assert 'name.setTag(R.id.regram_font_preview, true)' in settings
    for screen in ('OpenExteraAppearanceActivity','RegramFeaturesActivity'):
        assert 'presentFragment(new FontsSettingsActivity())' in (JAVA/f'app/regram/settings/{screen}.java').read_text()
    keys=[]
    for locale in ('values','values-ru'):
        tree=ET.parse(ROOT/f'TMessagesProj/src/main/res/{locale}/regram_fonts.xml').getroot()
        strings={node.get('name'):node.text for node in tree if node.tag=='string'}
        keys.append(set(strings))
        assert '20' in strings['RegramFontsImportInfo'] and '32' in strings['RegramFontsImportInfo']
    assert keys[0]==keys[1]
