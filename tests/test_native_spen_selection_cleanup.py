"""Run the real helper cleanup methods on a minimal host lifecycle model."""
from pathlib import Path
import shutil
import subprocess
import pytest

ROOT = Path(__file__).resolve().parents[1]


def method(source, signature):
    start = source.index(signature)
    i = source.index('{', start) + 1
    depth = 1
    while depth:
        depth += (source[i] == '{') - (source[i] == '}')
        i += 1
    return source[start:i]


def test_real_selection_cleanup_cancels_scroll_and_dismisses_magnifier(tmp_path):
    if not shutil.which('javac') or not shutil.which('java'):
        pytest.skip('JDK unavailable')
    source = (ROOT / 'TMessagesProj/src/main/java/org/telegram/ui/Cells/TextSelectionHelper.java').read_text()
    actual = method(source, 'public void clear(boolean instant)') + '\n' + method(source, 'public void finishOneTouchSelection()')
    java = '''
        import java.util.*;
        public class CleanupTest {
            static class AndroidUtilities {
                static Set<Runnable> pending = new HashSet<>();
                static void cancelRunOnUIThread(Runnable r) { pending.remove(r); }
                static void runOnUIThread(Runnable r) { pending.add(r); }
            }
            static class View { static final int GONE=8; }
            static class Overlay {
                int visibility; boolean exclusions;
                void setVisibility(int value) { visibility=value; }
                void clearGestureExclusionRects() { exclusions=false; }
                void invalidate() {}
            }
            static class Callback { boolean selected; void onStateChanged(boolean v) { selected=v; } }
            static class Helper {
                boolean scrolling=true, movingHandle=true, movingDirectionSettling=true, isOneTouch=true;
                boolean tryCapture=true, magnifier=true, actions=true;
                Object selectedView=new Object(), selectedCellEditDate=new Object();
                int selectionStart=0, selectionEnd=4, selectedCellId=10;
                int capturedX=0, capturedY=0, maybeTextX=0, maybeTextY=0;
                float handleViewProgress=1, movingOffsetX=1, movingOffsetY=1;
                Runnable scrollRunnable=()->{}, startSelectionRunnable=()->{}, showActionsRunnable=()->{};
                Overlay textSelectionOverlay=new Overlay(); Callback callback=new Callback();
                void onExitSelectionMode(boolean instant) {}
                void hideMagnifier() { magnifier=false; }
                void hideActions() { actions=false; }
                void invalidate() {}
                boolean isInSelectionMode() { return selectedView!=null; }
    ''' + actual + '''
            }
            public static void main(String[] args) {
                Helper clear=new Helper();
                AndroidUtilities.runOnUIThread(clear.scrollRunnable);
                AndroidUtilities.runOnUIThread(clear.startSelectionRunnable);
                clear.clear(false);
                assert !clear.scrolling && !clear.movingHandle && !clear.magnifier && !clear.actions;
                assert clear.selectedView==null && clear.selectionStart==-1 && clear.selectionEnd==-1;
                assert !AndroidUtilities.pending.contains(clear.scrollRunnable);
                assert !AndroidUtilities.pending.contains(clear.startSelectionRunnable);
                assert clear.textSelectionOverlay.visibility==View.GONE;
                for(boolean selected: new boolean[]{true,false}) {
                    Helper finish=new Helper();
                    if(!selected) finish.selectedView=null;
                    AndroidUtilities.runOnUIThread(finish.scrollRunnable);
                    finish.finishOneTouchSelection();
                    assert !finish.scrolling && !finish.magnifier;
                    assert !AndroidUtilities.pending.contains(finish.scrollRunnable);
                    if(selected) {
                        assert !finish.movingHandle && !finish.isOneTouch && !finish.movingDirectionSettling;
                        assert AndroidUtilities.pending.contains(finish.showActionsRunnable);
                    }
                }
            }
        }
    '''
    path = tmp_path / 'CleanupTest.java'
    path.write_text(java)
    subprocess.run(['javac', '-d', str(tmp_path), str(path)], check=True, capture_output=True, text=True)
    subprocess.run(['java', '-ea', '-cp', str(tmp_path), 'CleanupTest'], check=True, capture_output=True, text=True)
    chat = (ROOT / 'TMessagesProj/src/main/java/org/telegram/ui/ChatActivity.java').read_text()
    assert 'if (penGesture == 3 && textSelectionHelper != null) textSelectionHelper.clear();' in chat
