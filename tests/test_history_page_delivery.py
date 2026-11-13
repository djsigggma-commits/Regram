"""History must not disappear while auxiliary reply/story requests are pending."""
from pathlib import Path

from test_native_visual_settings import compile_run, method

ROOT = Path(__file__).resolve().parents[1]
JAVA = ROOT / "TMessagesProj/src/main/java"


def test_history_delivery_is_bounded_and_exactly_once(tmp_path):
    helper = "app/regram/chats/HistoryPageDelivery.java"
    compile_run(tmp_path, {
        helper: (JAVA / helper).read_text(),
        "HistoryDeliveryTest.java": """
import app.regram.chats.HistoryPageDelivery;
import java.util.ArrayList;
public class HistoryDeliveryTest {
 static class Queue {
  ArrayList<Runnable> pending = new ArrayList<>();
  int pages, cancels;
  HistoryPageDelivery start() {
   HistoryPageDelivery d = new HistoryPageDelivery(() -> pages++, r -> {
    cancels++; pending.remove(r);
   });
   pending.add(d);
   return d;
  }
  void timeout() { for (Runnable r : new ArrayList<>(pending)) r.run(); }
 }
 public static void main(String[] args) {
  assert HistoryPageDelivery.AUXILIARY_WAIT_MS > 0;
  assert HistoryPageDelivery.AUXILIARY_WAIT_MS <= 1000;
  Queue fast = new Queue();
  HistoryPageDelivery a = fast.start();
  a.run(); // replies available immediately, no extra delay
  assert fast.pages == 1 && fast.pending.isEmpty();
  fast.timeout(); a.run();
  assert fast.pages == 1 && fast.cancels == 1;

  Queue stalled = new Queue();
  HistoryPageDelivery b = stalled.start();
  assert stalled.pages == 0;
  stalled.timeout(); // no auxiliary callback ever arrives
  assert stalled.pages == 1 && stalled.pending.isEmpty();
  for (int i=0; i<100; i++) b.run(); // late/duplicate callback
  assert stalled.pages == 1 && stalled.cancels == 1;

  Queue paged = new Queue();
  HistoryPageDelivery first = paged.start(), second = paged.start();
  second.run(); // auxiliary requests finish out of order
  paged.timeout(); first.run(); second.run();
  assert paged.pages == 2 && paged.cancels == 2;
  assert paged.pending.isEmpty();
 }
}
""",
    }, "HistoryDeliveryTest")


def test_loaded_history_installs_timeout_before_starting_auxiliary_requests():
    source = (JAVA / "org/telegram/messenger/MessagesController.java").read_text()
    body = method(source, "public void processLoadedMessages(")
    start = body.index("app.regram.chats.HistoryPageDelivery delivery")
    delivery = body[start:body.index("} else {\n                getNotificationCenter()", start)]
    assert "NotificationCenter.messagesDidLoadWithoutProcess" in delivery
    assert "NotificationCenter.messagesDidLoad," in delivery
    assert "AndroidUtilities::cancelRunOnUIThread" in delivery
    assert delivery.index("AndroidUtilities.runOnUIThread(delivery,") < delivery.index("loadReplyMessagesForMessages(")
    assert "() -> AndroidUtilities.runOnUIThread(delivery)" in delivery
    media = (JAVA / "org/telegram/messenger/MediaDataController.java").read_text()
    broadcast = method(media, "private void broadcastReplyMessages(")
    assert "NotificationCenter.replyMessagesDidLoad" in broadcast
    chat = (JAVA / "org/telegram/ui/ChatActivity.java").read_text()
    assert "id == NotificationCenter.replyMessagesDidLoad" in chat


def test_real_controller_delivery_path_survives_stalls_errors_and_late_callbacks(tmp_path):
    source = (JAVA / "org/telegram/messenger/MessagesController.java").read_text()
    body = method(source, "public void processLoadedMessages(")
    start = body.index("            if (!DialogObject.isEncryptedDialog(dialogId) && mode != ChatActivity.MODE_QUICK_REPLIES)")
    end = body.index("            if (!messagesToReload.isEmpty())", start)
    actual = body[start:end]
    helper = "app/regram/chats/HistoryPageDelivery.java"
    compile_run(tmp_path, {
        helper: (JAVA / helper).read_text(),
        "ControllerTest.java": r'''
import java.util.*;
class AndroidUtilities {
 static List<Runnable> delayed = new ArrayList<>();
 static void runOnUIThread(Runnable r) { r.run(); }
 static void runOnUIThread(Runnable r, long delay) { delayed.add(r); }
 static void cancelRunOnUIThread(Runnable r) { delayed.remove(r); }
 static void tick() { for (Runnable r : new ArrayList<>(delayed)) r.run(); }
}
class DialogObject { static boolean isEncryptedDialog(long id) { return id == 99; } }
class ChatActivity { static final int MODE_QUICK_REPLIES = 5; }
class FileLog { static void e(Exception e) {} }
class Timer {
 static class Task {}
 static Task start(Object l, String s) { return new Task(); }
 static void done(Task t) {}
}
class NotificationCenter {
 static final int messagesDidLoad=1, messagesDidLoadWithoutProcess=2;
 int pages; int id; Object[] args;
 void postNotificationName(int id, Object... args) { pages++; this.id=id; this.args=args; }
}
class Media {
 int behavior; Runnable callback;
 void loadReplyMessagesForMessages(Object objects, long did, int mode, long thread,
                                  Runnable cb, int guid, Object logger) {
  callback=cb;
  if (behavior==1) cb.run();
  if (behavior==2) throw new IllegalStateException("optional reply data failure");
 }
}
public class ControllerTest {
 Media media = new Media(); NotificationCenter nc = new NotificationCenter();
 Media getMediaDataController() { return media; }
 NotificationCenter getNotificationCenter() { return nc; }
 void deliver(long dialogId, int mode, boolean needProcess) {
  int first_unread_final=7, classGuid=12, resCount=20, last_message_id=80;
  int count=20, unread_count=3, last_date=123, load_type=0, loadIndex=4, max_id=70, mentionsCount=2;
  int deliveredLastMessageId=80;
  boolean isCache=true, isEnd=false;
  long threadMessageId=0;
  Object loaderLogger=null; ArrayList<Integer> objects=new ArrayList<>(Arrays.asList(70,69,68));
''' + actual + r'''
 }
 public static void main(String[] args) {
  ControllerTest stalled=new ControllerTest(); stalled.deliver(-123,0,true);
  assert stalled.nc.pages==0;
  AndroidUtilities.tick(); assert stalled.nc.pages==1;
  assert stalled.nc.id==NotificationCenter.messagesDidLoad;
  assert ((ArrayList<?>)stalled.nc.args[2]).size()==3;
  assert stalled.nc.args[3].equals(true); // retain cache identity and load index
  assert stalled.nc.args[11].equals(4);
  stalled.media.callback.run(); AndroidUtilities.tick(); assert stalled.nc.pages==1;

  ControllerTest fast=new ControllerTest(); fast.media.behavior=1; fast.deliver(-123,0,true);
  assert fast.nc.pages==1 && AndroidUtilities.delayed.isEmpty();
  fast.media.callback.run(); assert fast.nc.pages==1;

  ControllerTest failed=new ControllerTest(); failed.media.behavior=2; failed.deliver(-123,0,true);
  assert failed.nc.pages==1 && AndroidUtilities.delayed.isEmpty();

  ControllerTest unprocessed=new ControllerTest(); unprocessed.deliver(-123,0,false);
  AndroidUtilities.tick(); assert unprocessed.nc.pages==1;
  assert unprocessed.nc.id==NotificationCenter.messagesDidLoadWithoutProcess;
  unprocessed.media.callback.run(); assert unprocessed.nc.pages==1;

  ControllerTest secret=new ControllerTest(); secret.deliver(99,0,true);
  assert secret.nc.pages==1 && secret.media.callback==null;
  ControllerTest quick=new ControllerTest(); quick.deliver(-123,5,true);
  assert quick.nc.pages==1 && quick.media.callback==null;
 }
}
''',
    }, "ControllerTest")
