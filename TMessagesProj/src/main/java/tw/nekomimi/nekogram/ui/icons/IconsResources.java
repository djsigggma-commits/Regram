package tw.nekomimi.nekogram.ui.icons;

import android.annotation.SuppressLint;
import android.content.res.Resources;
import android.graphics.drawable.Drawable;

import androidx.annotation.Nullable;

import app.regram.icons.IconPackManager;

import xyz.nextalone.nagram.NaConfig;

@SuppressLint("UseCompatLoadingForDrawables")
public class IconsResources extends Resources {

    public static final int ICON_REPLACE_SOLAR = 1;
    private int _iconsType = -1;

    public IconsResources(Resources resources) {
        super(resources.getAssets(), resources.getDisplayMetrics(), resources.getConfiguration());
    }

    @Override
    public Drawable getDrawable(int id) throws NotFoundException {
        return getDrawableForDensity(id, 0, null);
    }

    @Override
    public Drawable getDrawable(int id, @Nullable Theme theme) throws NotFoundException {
        return getDrawableForDensity(id, 0, theme);
    }

    @Nullable
    @Override
    public Drawable getDrawableForDensity(int id, int density, @Nullable Theme theme) {
        // openExtera: сначала спрашиваем установленные паки иконок
        Drawable fromPack = IconPackManager.getInstance().getDrawable(this, id, density, theme);
        if (fromPack != null) {
            return fromPack;
        }
        return getOriginalDrawableForDensity(id, density, theme);
    }

    @Nullable
    @Override
    public Drawable getDrawableForDensity(int id, int density) throws NotFoundException {
        return getDrawableForDensity(id, density, null);
    }

    /**
     * openExtera: оригинальный drawable (с учётом Solar-подмены, но без паков иконок).
     * Нужен менеджеру паков, чтобы узнать исходный размер иконки без рекурсии.
     */
    @Nullable
    public Drawable getOriginalDrawableForDensity(int id, int density, @Nullable Theme theme) {
        int converted = getConversion(id);
        // Только super.getDrawableForDensity: это терминальная реализация в Resources.
        // super.getDrawable(...) звать нельзя — он внутри вызывает виртуальный
        // getDrawableForDensity, попадает обратно в наш override и уходит в рекурсию.
        // density == 0 базовый Resources трактует как «без переопределения плотности».
        return super.getDrawableForDensity(converted, density, theme);
    }

    private int getConversion(int icon) {
        return getConversion(icon, -1);
    }

    private int getConversion(int icon, int forcedIconsType) {
        if (_iconsType == -1) {
            _iconsType = NaConfig.INSTANCE.getIconReplacements().Int();
        }

        int consideredIconsType = forcedIconsType == -1 ? _iconsType : forcedIconsType;

        if (consideredIconsType == ICON_REPLACE_SOLAR) {
            return SolarIcons.Companion.getConversion(icon);
        }

        return icon;
    }
}
