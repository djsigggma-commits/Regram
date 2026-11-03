"""Execute the actual profile palette worker against owned-bitmap/thread-boundary doubles."""
from pathlib import Path

from test_native_visual_settings import compile_run, method

ROOT = Path(__file__).resolve().parents[1]


def test_profile_palette_releases_owned_bitmap_on_success_failure_and_stale_work(tmp_path):
    source = (ROOT / "TMessagesProj/src/main/java/app/regram/components/ProfileMusicCard.java").read_text()
    worker = method(source, "private void extractColors(Bitmap bitmap)")
    assert "Utilities.globalQueue" not in source
    assert "private volatile long imageToken;" in source
    assert "COLORS_IO.execute(" in worker
    assert "finally" in worker and "copy.recycle();" in worker
    compile_run(tmp_path, {"PaletteLifecycleTest.java": '''import java.util.*;
class Bitmap {
 enum Config {ARGB_8888}
 boolean recycled;static Bitmap lastCopy;static int copies;
 boolean isRecycled(){return recycled;}
 Bitmap copy(Config config,boolean mutable){copies++;return lastCopy=new Bitmap();}
 void recycle(){assert !recycled;recycled=true;}
}
class WorkQueue {
 final ArrayDeque<Runnable> work=new ArrayDeque<>();
 void execute(Runnable r){work.add(r);}void run(){work.remove().run();}
}
class Palette {
 static int analyses;static boolean fail,noSwatch;
 static Palette from(Bitmap b){assert !b.recycled;return new Palette();}
 Palette generate(){analyses++;if(fail)throw new IllegalStateException("test");return this;}
 static class Swatch {int getRgb(){return 0xff123456;}}
 Swatch getDarkVibrantSwatch(){return noSwatch?null:new Swatch();}
 Swatch getMutedSwatch(){return null;}Swatch getDarkMutedSwatch(){return null;}Swatch getDominantSwatch(){return null;}
}
class Color {static final int WHITE=0xffffffff,BLACK=0xff000000;}
class ColorUtils {
 static double calculateContrast(int a,int b){return 12;}
 static int blendARGB(int a,int b,float t){return a;}
 static void colorToHSL(int c,float[] hsl){hsl[2]=.4f;}
}
class AndroidUtilities {
 static WorkQueue ui=new WorkQueue();
 static void runOnUIThread(Runnable r){ui.execute(r);}
 static int getDominantColor(Bitmap b){assert !b.recycled;return 0xff123456;}
}
public class PaletteLifecycleTest {
 static WorkQueue COLORS_IO=new WorkQueue();
 private volatile long imageToken=1;private long paletteToken=-1;
 boolean hasCover;int backgroundColor,accentColor,applied;
 void applyColors(){applied++;}static int adjustHsl(int c,float l){return c;}
 WORKER
 public static void main(String[] args){
  PaletteLifecycleTest card=new PaletteLifecycleTest();Bitmap original=new Bitmap();
  card.extractColors(null);assert COLORS_IO.work.isEmpty();
  card.extractColors(original);Bitmap owned=Bitmap.lastCopy;
  assert owned!=original&&!owned.recycled&&!original.recycled;
  assert Palette.analyses==0;COLORS_IO.run();assert owned.recycled&&!original.recycled;
  assert card.applied==0;AndroidUtilities.ui.run();assert card.applied==1&&card.hasCover;
  int copies=Bitmap.copies;card.extractColors(original);assert Bitmap.copies==copies;

  card.imageToken++;card.extractColors(original);owned=Bitmap.lastCopy;
  int analyses=Palette.analyses;card.imageToken++;COLORS_IO.run();
  assert Palette.analyses==analyses&&owned.recycled&&!original.recycled;
  assert AndroidUtilities.ui.work.isEmpty()&&card.applied==1;

  card.extractColors(original);owned=Bitmap.lastCopy;COLORS_IO.run();
  card.imageToken++;AndroidUtilities.ui.run();assert owned.recycled&&card.applied==1;

  Palette.fail=true;card.extractColors(original);owned=Bitmap.lastCopy;COLORS_IO.run();
  assert owned.recycled&&!original.recycled&&AndroidUtilities.ui.work.isEmpty();
  Palette.fail=false;Palette.noSwatch=true;card.imageToken++;
  card.extractColors(original);owned=Bitmap.lastCopy;COLORS_IO.run();AndroidUtilities.ui.run();
  assert owned.recycled&&card.applied==2&&!original.recycled;
  Bitmap recycled=new Bitmap();recycled.recycle();copies=Bitmap.copies;
  card.imageToken++;card.extractColors(recycled);assert Bitmap.copies==copies&&COLORS_IO.work.isEmpty();
 }
}'''.replace("WORKER", worker)}, "PaletteLifecycleTest")
