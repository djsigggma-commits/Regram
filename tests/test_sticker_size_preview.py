"""The size preview resolves the third Vaporeon sticker without installing the pack."""
from pathlib import Path

from test_native_visual_settings import compile_run, method

ROOT = Path(__file__).resolve().parents[1]
SOURCE = ROOT / 'TMessagesProj/src/main/java/tw/nekomimi/nekogram/ui/cells/StickerSizePreviewMessagesCell.java'


def test_preview_loads_exact_third_document_and_keeps_reply_and_account(tmp_path):
    source = SOURCE.read_text()
    loader = method(source, '    private void loadPreviewSticker()')
    constants = '\n'.join(line for line in source.splitlines() if 'private static final' in line and 'PREVIEW_STICKER_' in line)
    compile_run(tmp_path, {'PreviewTest.java': '''
import java.util.*;
import java.util.function.Consumer;
class TLRPC {
 static class Document {}
 static class TL_documentEmpty extends Document {}
 static class Media { Document document; }
 static class Message { Media media = new Media(); }
 static class TL_inputStickerSetShortName { String short_name; }
 static class Set { List<Document> documents = new ArrayList<>(); }
}
class MessageObject {
 TLRPC.Message messageOwner; MessageObject replyMessageObject;
 String customReplyName; boolean forceUpdate; int account;
 MessageObject(int a, TLRPC.Message m, boolean x, boolean y) { account=a; messageOwner=m; }
}
class MediaDataController {
 static MediaDataController instance = new MediaDataController();
 Consumer<TLRPC.Set> callback; int calls, account;
 static MediaDataController getInstance(int a) { instance.account=a; return instance; }
 void getStickerSet(TLRPC.TL_inputStickerSetShortName input, Object hash, boolean cacheOnly, Consumer<TLRPC.Set> cb) {
  assert input.short_name.equals("vaporeon_by_atb_by_fStikBot");
  assert hash==null && !cacheOnly;
  calls++; callback=cb;
 }
}
public class PreviewTest {
 final int currentAccount=2;
 MessageObject[] messageObjects = new MessageObject[2];
 boolean previewStickerLoading, previewStickerLoaded;
 int invalidations, layouts;
 PreviewTest() {
  for(int i=0;i<2;i++) messageObjects[i]=new MessageObject(2,new TLRPC.Message(),true,false);
  messageObjects[0].replyMessageObject=messageObjects[1];
  messageObjects[0].customReplyName="Duang";
 }
 void invalidate() { invalidations++; }
 void requestLayout() { layouts++; }
''' + constants + '\n' + loader + '''
 public static void main(String[] args) {
  PreviewTest p=new PreviewTest(); MediaDataController m=MediaDataController.instance;
  p.loadPreviewSticker(); p.loadPreviewSticker();
  assert m.calls==1 && m.account==2;
  m.callback.accept(null); assert !p.previewStickerLoading && !p.previewStickerLoaded;
  p.loadPreviewSticker(); TLRPC.Set shortPack=new TLRPC.Set();
  shortPack.documents.add(new TLRPC.Document()); m.callback.accept(shortPack);
  assert !p.previewStickerLoaded && p.layouts==0;
  p.loadPreviewSticker(); TLRPC.Set pack=new TLRPC.Set();
  for(int i=0;i<4;i++) pack.documents.add(new TLRPC.Document());
  MessageObject reply=p.messageObjects[0].replyMessageObject;
  m.callback.accept(pack);
  assert p.previewStickerLoaded && !p.previewStickerLoading;
  assert p.messageObjects[0].messageOwner.media.document==pack.documents.get(2);
  assert p.messageObjects[0].account==2;
  assert p.messageObjects[0].replyMessageObject==reply;
  assert p.messageObjects[0].customReplyName.equals("Duang");
  assert p.messageObjects[1].replyMessageObject==p.messageObjects[0];
  assert p.messageObjects[1].forceUpdate && p.layouts==1 && p.invalidations==1;
  int calls=m.calls; p.loadPreviewSticker(); assert m.calls==calls;
 }
}
'''}, 'PreviewTest')


def test_preview_removes_builtin_cat_and_loads_on_attach_not_every_invalidation():
    source = SOURCE.read_text()
    assert 'useCustomPhoto = true' not in source
    assert 'attributeSticker.alt = "🐱"' not in source
    assert 'loadPreviewSticker();' in method(source, '    protected void onAttachedToWindow()')
    assert 'loadPreviewSticker' not in method(source, '    public void invalidate()')
    assert source.count('UserConfig.selectedAccount') == 1
