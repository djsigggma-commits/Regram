package app.regram.plugins.models;

import android.content.Context;
import android.view.View;
import android.widget.FrameLayout;

import androidx.recyclerview.widget.RecyclerView;

import com.chaquo.python.PyObject;

import org.telegram.messenger.AndroidUtilities;
import org.telegram.messenger.FileLog;
import org.telegram.ui.ActionBar.Theme;
import org.telegram.ui.Components.LayoutHelper;
import org.telegram.ui.Components.RecyclerListView;
import org.telegram.ui.Components.UItem;
import org.telegram.ui.Components.UniversalAdapter;
import org.telegram.ui.Components.UniversalRecyclerView;

public class PluginItemFactory extends UItem.UItemFactory<FrameLayout> {

    private static volatile PluginItemFactory instance;

    public static PluginItemFactory getInstance() {
        if (instance == null) {
            synchronized (PluginItemFactory.class) {
                if (instance == null) {
                    instance = new PluginItemFactory();
                    UItem.UItemFactory.setup(instance);
                }
            }
        }
        return instance;
    }

    public static UItem create(PyObject pyFactory, PyObject args) {
        getInstance();
        UItem item = UItem.ofFactory(PluginItemFactory.class);
        item.object = pyFactory;
        item.object2 = args;
        item.enabled = flag(pyFactory, "is_clickable", false);
        return item;
    }

    private static PyObject pyOf(UItem item) {
        return item != null && item.object instanceof PyObject ? (PyObject) item.object : null;
    }

    /**
     * Колбэк питоновской фабрики, если она его объявила. Общий {@code equals} и
     * {@code contentsEquals} у всего класса один, поэтому решение отдаётся
     * строке: без этого {@code SimpleSettingFactory(equals=...)} принимался, но
     * никогда не вызывался, и список перерисовывался целиком на каждое
     * изменение настроек.
     */
    private static Boolean pyFlag(PyObject py, String name, Object... args) {
        PyObject fn = callable(py, name);
        if (fn == null) {
            return null;
        }
        try {
            PyObject value = fn.call(args);
            return value == null ? null : value.toJava(Boolean.class);
        } catch (Throwable t) {
            FileLog.e("PluginItemFactory: " + name + " failed", t);
            return null;
        }
    }

    /** PyObject не отдаёт hasAttr(), поэтому наличие колбэка проверяется через get(). */
    private static PyObject callable(PyObject py, String name) {
        if (py == null) {
            return null;
        }
        try {
            return py.get(name);
        } catch (Throwable t) {
            return null;
        }
    }

    private static boolean flag(PyObject py, String name, boolean fallback) {
        if (py == null) {
            return fallback;
        }
        try {
            PyObject value = py.get(name);
            if (value == null) {
                return fallback;
            }
            Boolean converted = value.toJava(Boolean.class);
            return converted == null ? fallback : converted;
        } catch (Throwable t) {
            return fallback;
        }
    }

    @Override
    public FrameLayout createView(Context context, RecyclerListView listView, int currentAccount,
                                  int classGuid, Theme.ResourcesProvider resourcesProvider) {
        FrameLayout container = new FrameLayout(context);
        container.setLayoutParams(new RecyclerView.LayoutParams(
                RecyclerView.LayoutParams.MATCH_PARENT,
                RecyclerView.LayoutParams.WRAP_CONTENT));
        return container;
    }

    @Override
    public void bindView(View view, UItem item, boolean divider, UniversalAdapter adapter,
                         UniversalRecyclerView listView) {
        FrameLayout container = (FrameLayout) view;
        PyObject py = item != null && item.object instanceof PyObject
                ? (PyObject) item.object : null;
        if (py == null) {
            container.removeAllViews();
            return;
        }
        View child = null;
        try {
            PyObject built = py.callAttr("build_view", container.getContext(), divider,
                    item.object2);
            child = built == null ? null : built.toJava(View.class);
        } catch (Throwable t) {
            FileLog.e("PluginItemFactory: build_view failed", t);
        }
        if (container.getChildCount() == 1 && container.getChildAt(0) == child) {
            return;
        }
        container.removeAllViews();
        if (child == null) {
            return;
        }
        AndroidUtilities.removeFromParent(child);
        container.addView(child, LayoutHelper.createFrame(
                LayoutHelper.MATCH_PARENT, LayoutHelper.WRAP_CONTENT));
    }

    @Override
    public void attachedView(RecyclerListView listView, View view, UItem item) {
        PyObject fn = callable(pyOf(item), "attached_view");
        if (fn == null) {
            return;
        }
        try {
            fn.call(view);
        } catch (Throwable t) {
            FileLog.e("PluginItemFactory: attached_view failed", t);
        }
    }

    @Override
    public boolean isShadow() {
        return false;
    }

    @Override
    public boolean isClickable() {
        return true;
    }

    @Override
    public boolean equals(UItem a, UItem b) {
        if (a == b) {
            return true;
        }
        PyObject py = pyOf(a);
        Boolean custom = pyFlag(py, "equals", a, b);
        return custom != null ? custom : a.id == b.id && a.object == b.object;
    }

    @Override
    public boolean contentsEquals(UItem a, UItem b) {
        Boolean custom = pyFlag(pyOf(a), "content_equals", a, b);
        return custom != null ? custom : false;
    }
}
