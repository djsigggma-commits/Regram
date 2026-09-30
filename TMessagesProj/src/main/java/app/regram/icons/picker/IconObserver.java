package app.regram.icons.picker;

import android.app.Activity;
import android.os.Looper;

import org.telegram.ui.ActionBar.BaseFragment;
import org.telegram.ui.LaunchActivity;

import java.util.Collections;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.Set;
import java.util.WeakHashMap;

import app.regram.icons.IconPacksConfig;

/**
 * Наблюдатель за иконками, которые реально запрашивал текущий экран
 * (порт {@code com.exteragram.messenger.icons.ui.picker.IconObserver}).
 *
 * Пока включён режим редактирования пака, каждый запрос drawable из
 * {@link app.regram.icons.IconPackManager#getDrawable} регистрируется здесь и
 * привязывается к фрагменту, который сейчас на экране. Пикер показывает ровно эти иконки,
 * поэтому пользователь заменяет то, что видит, а не ищет по общему списку из тысяч имён.
 *
 * Ссылки на фрагменты слабые — уничтоженный экран уходит из карты сам.
 */
public class IconObserver {

    private static final WeakHashMap<BaseFragment, Set<Integer>> iconSources = new WeakHashMap<>();

    private IconObserver() {
    }

    private static volatile boolean suspended;

    /**
     * Временно отключает сбор: сам пикер тоже рисует иконки через те же Resources,
     * и без этого он засорял бы список собственными кнопками.
     */
    public static void setSuspended(boolean value) {
        suspended = value;
    }

    /** Регистрирует запрос иконки; вызывается на любом потоке, дёшево при выключенном режиме. */
    public static void log(int resId) {
        if (suspended || resId == 0 || !IconPacksConfig.isEditing()) {
            return;
        }
        // стек фрагментов читаем только с UI-потока; фоновые запросы drawable просто пропускаем
        if (Looper.myLooper() != Looper.getMainLooper()) {
            return;
        }
        BaseFragment fragment = LaunchActivity.getSafeLastFragment();
        if (fragment == null) {
            return;
        }
        Activity activity = fragment.getParentActivity();
        if (activity instanceof LaunchActivity) {
            IconPickerController.setActive((LaunchActivity) activity, true);
        }
        synchronized (iconSources) {
            Set<Integer> set = iconSources.get(fragment);
            if (set == null) {
                set = new LinkedHashSet<>();
                iconSources.put(fragment, set);
            }
            set.add(resId);
        }
    }

    /** Иконки, замеченные на текущем экране. */
    public static Set<Integer> getUsedIcons() {
        BaseFragment fragment = LaunchActivity.getSafeLastFragment();
        if (fragment == null) {
            return Collections.emptySet();
        }
        synchronized (iconSources) {
            Set<Integer> set = iconSources.get(fragment);
            return set == null ? Collections.emptySet() : new LinkedHashSet<>(set);
        }
    }

    /** Все иконки, замеченные за сессию редактирования. */
    public static Set<Integer> getAllIcons() {
        synchronized (iconSources) {
            Set<Integer> result = new LinkedHashSet<>();
            for (Set<Integer> set : iconSources.values()) {
                result.addAll(set);
            }
            return result;
        }
    }

    public static void removeSource(BaseFragment owner) {
        synchronized (iconSources) {
            iconSources.remove(owner);
        }
    }

    public static void clear() {
        synchronized (iconSources) {
            iconSources.clear();
        }
    }

    /** Копия множества — чтобы вызывающий не держал наш монитор. */
    static Set<Integer> snapshot(Set<Integer> source) {
        return new HashSet<>(source);
    }
}
