package app.regram.appearance;

import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.RectF;
import androidx.core.graphics.ColorUtils;
import org.telegram.messenger.AndroidUtilities;
import org.telegram.ui.ActionBar.Theme;

/** Per-tab native chip renderer. No hooks, retained pooled views, or shared Paint replacement. */
public final class NativeChipFolders {
    private final Paint paint=new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Path path=new Path();
    private final RectF rect=new RectF();
    private final float[] radii=new float[8];
    private static final float[] SCALES={.92f,1f,1.08f,1f};
    private static final int[] PADDINGS={25,26,30,26};
    private final Paint maskPaint=new Paint(Paint.ANTI_ALIAS_FLAG);
    private android.graphics.drawable.RippleDrawable foreground;
    private int rippleColor;
    private float pathLeftRadius = Float.NaN, pathRightRadius = Float.NaN;
    public void clearRipple(android.view.View view){if(view.getForeground()==foreground)view.setForeground(null);}
    public static float scale(){return SCALES[PluginStyles.number("chip_size",1,0,3)];}
    public static int padding(){return Math.round(PADDINGS[PluginStyles.number("chip_size",1,0,3)]*scale())+PluginStyles.gap();}
    private static int withAlpha(int color,float alpha){return (color&0xffffff)|(Math.round(PluginStyleRules.unit(alpha)*255)<<24);}
    private static int custom(String key,int fallback){int value=PluginStyles.number(key,0,Integer.MIN_VALUE,Integer.MAX_VALUE);return value==0?fallback:value;}
    public static int active(Theme.ResourcesProvider rp){int c=Theme.getColor(Theme.key_featuredStickers_addButton,rp);int style=PluginStyles.number("chip_style",0,0,2);return PluginStyles.flag("chip_md3",true)?c:custom("chip_active",withAlpha(c,style==1?.20f:style==2?.12f:.24f));}
    public static int label(Theme.ResourcesProvider rp,float progress,int inactiveKey) {
        int base=Theme.getColor(inactiveKey,rp);
        int selected=PluginStyles.flag("chip_md3",true)?Theme.getColor(Theme.key_featuredStickers_buttonText,rp):custom("text_active",Theme.getColor(Theme.key_windowBackgroundWhiteBlueText,rp));
        int idle=PluginStyles.flag("chip_md3",true)?base:custom("text_inactive",base);
        return ColorUtils.blendARGB(idle,selected,PluginStyleRules.unit(progress));
    }
    public void draw(android.view.View view,Canvas canvas,float width,float height,float progress,float previous,float next,boolean first,boolean last,boolean rtl,Theme.ResourcesProvider rp,int inactiveKey) {
        progress=PluginStyleRules.unit(progress);
        int style=PluginStyles.number("chip_style",0,0,2), shape=PluginStyles.number("chip_shape",5,0,7);
        int idleColor=QuickTheme.folderColor(Theme.getColor(inactiveKey,rp));
        int idle=style==0?withAlpha(idleColor,.11f):0;
        if(!PluginStyles.flag("chip_md3",true)&&style!=1)idle=custom("chip_inactive",idle);
        int selected=QuickTheme.folderColor(active(rp));
        float gap=AndroidUtilities.dpf2(PluginStyles.gap())/2;
        float h=Math.min(height,AndroidUtilities.dpf2(PluginStyles.barHeight()-12)), top=(height-h)/2;
        float half=h/AndroidUtilities.density/2;
        float ar=PluginStyles.number(shape==6?"radius_alt_active":shape==7?"radius_custom_active":"radius_active",shape==7?11:20,0,24);
        float outer=PluginStyles.number(shape==6?"radius_alt_outer":shape==7?"radius_inactive":"radius_outer",20,0,24);
        float inner=PluginStyles.number("radius_inner",8,0,24);
        float leftRadius,rightRadius,left=gap,right=width-gap,joinRight=0;
        if(shape==5) {
            leftRadius=PluginStyleRules.mix(Math.min(half,(rtl?last:first)?outer:inner),Math.min(half,ar),progress);
            rightRadius=PluginStyleRules.mix(Math.min(half,(rtl?first:last)?outer:inner),Math.min(half,ar),progress);
        }else if(shape==6) {
            float l=(rtl?last:first)?0:(1-PluginStyleRules.unit(rtl?next:previous))*(1-progress);
            float r=(rtl?first:last)?0:(1-PluginStyleRules.unit(rtl?previous:next))*(1-progress);
            left=gap*(1-l);right=width-gap*(1-r);joinRight=r;
            leftRadius=PluginStyleRules.mix(PluginStyleRules.mix(Math.min(half,outer),0,l),Math.min(half,ar),progress);
            rightRadius=PluginStyleRules.mix(PluginStyleRules.mix(Math.min(half,outer),0,r),Math.min(half,ar),progress);
        }else leftRadius=rightRadius=PluginStyleRules.chipRadius(shape,progress,h/AndroidUtilities.density,ar,outer);
        if(right<=left||h<=0)return;
        if (tw.nekomimi.nekogram.NekoConfig.regramQuickTheme.Bool()
                && tw.nekomimi.nekogram.NekoConfig.regramQuickFolderRadius.Int() >= 0) {
            leftRadius = rightRadius = Math.min(half, QuickTheme.folderRadius(20));
        }
        leftRadius=AndroidUtilities.dpf2(leftRadius);rightRadius=AndroidUtilities.dpf2(rightRadius);
        if (rect.left!=left || rect.top!=top || rect.right!=right || rect.bottom!=top+h
                || pathLeftRadius!=leftRadius || pathRightRadius!=rightRadius) {
            pathLeftRadius=leftRadius;pathRightRadius=rightRadius;
            for(int i=0;i<8;i++)radii[i]=i<2||i>=6?leftRadius:rightRadius;
            rect.set(left,top,right,top+h);path.rewind();path.addRoundRect(rect,radii,Path.Direction.CW);
        }
        int color=Theme.getColor(Theme.key_listSelector,rp);
        if(foreground==null){
            maskPaint.setColor(0xffffffff);
            android.graphics.drawable.Drawable mask=new android.graphics.drawable.Drawable(){
                @Override public void draw(Canvas canvas){canvas.drawPath(path,maskPaint);}
                @Override public void setAlpha(int alpha){maskPaint.setAlpha(alpha);}
                @Override public void setColorFilter(android.graphics.ColorFilter filter){maskPaint.setColorFilter(filter);}
                @Override public int getOpacity(){return android.graphics.PixelFormat.TRANSLUCENT;}
            };
            foreground=new android.graphics.drawable.RippleDrawable(android.content.res.ColorStateList.valueOf(color),null,mask);rippleColor=color;view.setForeground(foreground);
        }else if(color!=rippleColor){rippleColor=color;foreground.setColor(android.content.res.ColorStateList.valueOf(color));}
        if(view.getForeground()!=foreground)view.setForeground(foreground);
        float opacity = QuickTheme.foldersAlpha() / 255f;
        paint.setStyle(Paint.Style.FILL);paint.setColor(Theme.multAlpha(ColorUtils.blendARGB(idle,selected,progress),opacity));canvas.drawPath(path,paint);
        if(style==1&&progress<1){paint.setStyle(Paint.Style.STROKE);paint.setStrokeWidth(AndroidUtilities.dpf2(1));paint.setColor(withAlpha(idleColor,.3f*(1-progress)*opacity));canvas.drawPath(path,paint);}
        if(joinRight>.01f&&style!=1){paint.setStyle(Paint.Style.STROKE);paint.setStrokeWidth(AndroidUtilities.dpf2(1));paint.setColor(withAlpha(idleColor,joinRight*.16f*opacity));canvas.drawLine(right,top+h*.22f,right,top+h*.78f,paint);}
    }
}
