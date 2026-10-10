from pathlib import Path
from test_native_visual_settings import compile_run

ROOT = Path(__file__).resolve().parents[1]


def test_actual_native_avatar_width_and_draw(tmp_path):
    source = (ROOT / 'TMessagesProj/src/main/java/app/regram/chats/MentionAvatarSpan.java').read_text()
    files = {
        'app/regram/chats/MentionAvatarSpan.java': source,
        'org/telegram/tgnet/TLObject.java': 'package org.telegram.tgnet; public class TLObject {}',
        'org/telegram/messenger/AndroidUtilities.java': 'package org.telegram.messenger; public class AndroidUtilities {public static int dp(float n){return (int)n;}}',
        'android/graphics/Paint.java': '''package android.graphics; public class Paint {
 public int color=7; public float unit=7.5f; public boolean underline;
 public void set(Paint p){color=p.color;unit=p.unit;underline=p.underline;}
 public int getColor(){return color;} public void setColor(int c){color=c;}
 public static class FontMetricsInt {public int ascent,descent,top,bottom;}
 public int getFontMetricsInt(FontMetricsInt f){f.ascent=-12;f.descent=4;f.top=-16;f.bottom=6;return 16;}
 public float measureText(CharSequence t,int s,int e){return (e-s)*unit;}
}''',
        'android/text/TextPaint.java': '''package android.text; public class TextPaint extends android.graphics.Paint {
 public int linkColor; public void set(TextPaint p){super.set(p);linkColor=p.linkColor;}
}''',
        'android/text/style/URLSpan.java': '''package android.text.style; public class URLSpan {
 public int calls; public URLSpan(String url){} public void updateDrawState(android.text.TextPaint p){calls++;p.setColor(p.linkColor);p.underline=true;}
}''',
        'android/graphics/Canvas.java': '''package android.graphics; public class Canvas {
 public float textX,avatarX; public String text; public Paint paint;
 public void drawText(CharSequence t,int s,int e,float x,float y,Paint p){textX=x;text=t.subSequence(s,e).toString();paint=p;}
}''',
        'org/telegram/ui/AvatarSpan.java': '''package org.telegram.ui; import android.graphics.*; import org.telegram.tgnet.TLObject;
public class AvatarSpan {
 public boolean needDrawShadow=true,loadOnly; public TLObject peer; final float size;
 public AvatarSpan(Object parent,int account,float size){this.size=size;}
 public void setLoadOnlyWhenAttached(boolean b){loadOnly=b;} public void setObject(TLObject p){peer=p;}
 public int getSize(Paint p,CharSequence t,int s,int e,Paint.FontMetricsInt fm){return (int)size;}
 public void draw(Canvas c,CharSequence t,int s,int e,float x,int top,int y,int bottom,Paint p){c.avatarX=x;}
}''',
        'RenderingTest.java': '''import app.regram.chats.MentionAvatarSpan; import android.graphics.*; import org.telegram.tgnet.TLObject;
public class RenderingTest {public static void main(String[] args){
 TLObject peer=new TLObject(); Paint paint=new Paint(); Canvas canvas=new Canvas();
 MentionAvatarSpan before=new MentionAvatarSpan(0,18,true,peer);
 assert before.loadOnly && before.peer==peer && !before.needDrawShadow;
 Paint.FontMetricsInt fm=new Paint.FontMetricsInt();
 assert before.getSize(paint,"@foo",0,1,fm)==29;
 assert fm.ascent<=-13 && fm.descent>=5 && fm.top<=fm.ascent && fm.bottom>=fm.descent;
 before.draw(canvas,"@foo",0,1,10,0,14,22,paint);
 assert canvas.text.equals("@") && canvas.textX==31 && canvas.avatarX==10 && canvas.paint==paint;
 MentionAvatarSpan after=new MentionAvatarSpan(0,18,false,peer);
 after.draw(canvas,"@foo",3,4,10,0,14,22,paint);
 assert canvas.text.equals("o") && canvas.textX==10 && canvas.avatarX==20.5f && canvas.paint==paint;
 assert after.getSize(paint,"@foo",3,4,null)==29;
 // A replacement run over a Cyrillic word must keep URL colour and typography.
 android.text.TextPaint base=new android.text.TextPaint(); base.linkColor=42;
 android.text.style.URLSpan url=new android.text.style.URLSpan("https://t.me/lab") {
  @Override public void updateDrawState(android.text.TextPaint p){super.updateDrawState(p);p.unit=9;}
 };
 String word="ЦИТРУСОВАЯ";
 MentionAvatarSpan linked=new MentionAvatarSpan(0,18,true,peer,url);
 assert linked.getSize(base,word,0,word.length(),null)==word.length()*9+21;
 linked.draw(canvas,word,0,word.length(),10,0,14,22,base);
 assert canvas.text.equals(word) && canvas.textX==31 && canvas.paint.getColor()==42;
 assert canvas.paint.underline && canvas.paint.unit==9;
 assert base.getColor()==7 && !base.underline && base.unit==7.5f; // shared layout paint unchanged
 base.linkColor=84; // theme change must not use a stale cached colour
 linked.draw(canvas,word,0,word.length(),10,0,14,22,base);
 assert canvas.paint.getColor()==84;
 MentionAvatarSpan linkedAfter=new MentionAvatarSpan(0,18,false,peer,url);
 linkedAfter.draw(canvas,word,0,word.length(),10,0,14,22,base);
 assert canvas.textX==10 && canvas.avatarX==10+word.length()*9+3;
 assert canvas.paint.getColor()==84 && url.calls>=4;
}}''',
    }
    compile_run(tmp_path, files, 'RenderingTest')
