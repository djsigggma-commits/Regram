package app.regram.plugins.ui.components.templates;

import android.content.Context;
import android.view.View;

import org.telegram.ui.ActionBar.ActionBar;
import org.telegram.ui.ActionBar.ActionBarMenu;
import org.telegram.ui.Components.UItem;
import org.telegram.ui.Components.UniversalAdapter;

import java.util.ArrayList;

/**
 * Шим экрана-шаблона exteraGram: плагин отдаёт делегата и получает готовый
 * фрагмент со списком {@link UItem}.
 *
 * Абстрактный {@code org.telegram.ui.Components.UniversalFragment} наследовать
 * из Python нельзя — Chaquopy умеет только {@code dynamic_proxy} по интерфейсу.
 * Отсюда и форма: конкретный класс здесь, интерфейс — плагину.
 *
 * Все методы делегата со значением по умолчанию: {@code dynamic_proxy}
 * реализует интерфейс целиком, и без default-реализаций плагин был бы обязан
 * описывать все девять, даже если рисует один список.
 */
public class UniversalFragment extends org.telegram.ui.Components.UniversalFragment {

    public interface UniversalFragmentDelegate {

        default View beforeCreateView() {
            return null;
        }

        default View afterCreateView(View view) {
            return view;
        }

        default CharSequence getTitle() {
            return null;
        }

        default void fillItems(ArrayList<UItem> items, UniversalAdapter adapter) {
        }

        default void onClick(UItem item, View view, int position, float x, float y) {
        }

        default boolean onLongClick(UItem item, View view, int position, float x, float y) {
            return false;
        }

        default void onMenuItemClick(int id) {
        }

        default void onFragmentCreate() {
        }

        default Boolean onBackPressed() {
            return null;
        }

        default void onFragmentDestroy() {
        }
    }

    private UniversalFragmentDelegate delegate;

    public UniversalFragment() {
        this(null);
    }

    public UniversalFragment(UniversalFragmentDelegate delegate) {
        this.delegate = delegate;
    }

    public UniversalFragmentDelegate getDelegate() {
        return delegate;
    }

    public void setDelegate(UniversalFragmentDelegate delegate) {
        this.delegate = delegate;
    }

    public ActionBarMenu getActionBarMenu() {
        return actionBar.createMenu();
    }

    public void setTitle(CharSequence title, boolean animated, long duration) {
        if (!animated) {
            actionBar.setTitle(title);
            return;
        }
        actionBar.setTitleAnimated(title, false, duration <= 0 ? 300 : duration);
    }

    @Override
    public View createView(Context context) {
        final UniversalFragmentDelegate d = delegate;
        if (d != null) {
            final View replacement = d.beforeCreateView();
            if (replacement != null) {
                return fragmentView = replacement;
            }
        }
        final View view = super.createView(context);
        actionBar.setActionBarMenuOnItemClick(new ActionBar.ActionBarMenuOnItemClick() {
            @Override
            public void onItemClick(int id) {
                if (id == -1) {
                    if (onBackPressed(true)) {
                        finishFragment();
                    }
                    return;
                }
                final UniversalFragmentDelegate current = getDelegate();
                if (current != null) {
                    current.onMenuItemClick(id);
                }
            }
        });
        if (d == null) {
            return view;
        }
        final View wrapped = d.afterCreateView(view);
        return wrapped == null ? view : (fragmentView = wrapped);
    }

    @Override
    public CharSequence getTitle() {
        return delegate == null ? null : delegate.getTitle();
    }

    @Override
    public void fillItems(ArrayList<UItem> items, UniversalAdapter adapter) {
        if (delegate != null) {
            delegate.fillItems(items, adapter);
        }
    }

    @Override
    public void onClick(UItem item, View view, int position, float x, float y) {
        if (delegate != null) {
            delegate.onClick(item, view, position, x, y);
        }
    }

    @Override
    public boolean onLongClick(UItem item, View view, int position, float x, float y) {
        return delegate != null && delegate.onLongClick(item, view, position, x, y);
    }

    @Override
    public boolean onFragmentCreate() {
        if (delegate != null) {
            delegate.onFragmentCreate();
        }
        return super.onFragmentCreate();
    }

    @Override
    public void onFragmentDestroy() {
        if (delegate != null) {
            delegate.onFragmentDestroy();
        }
        super.onFragmentDestroy();
    }

    @Override
    public boolean onBackPressed(boolean invoked) {
        final Boolean handled = delegate == null ? null : delegate.onBackPressed();
        return handled == null ? super.onBackPressed(invoked) : !handled;
    }
}
