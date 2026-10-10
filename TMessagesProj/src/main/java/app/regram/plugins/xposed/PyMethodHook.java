package app.regram.plugins.xposed;

import com.chaquo.python.PyObject;
import org.telegram.ui.ActionBar.ActionBar;
import org.telegram.messenger.BuildConfig;

import java.util.Collections;
import java.util.List;

import de.robv.android.xposed.XC_MethodHook;

/**
 * before/after-хук: вызывает Python-методы {@code before_hooked_method(param)} и
 * {@code after_hooked_method(param)}. Отсутствующий метод и пустой метод, унаследованный
 * от MethodHook, не вызываются: это решается один раз при регистрации. MethodHookParam передаётся в Python как есть,
 * Chaquopy оборачивает его в прокси (param.thisObject, param.args, param.getResult(),
 * param.setResult(...) доступны из Python).
 *
 * Фильтры считаются на Java-стороне ДО входа в Python: "before"-фильтры гейтят
 * before_hooked_method, "after"-фильтры — after_hooked_method.
 */
public class PyMethodHook extends XC_MethodHook {

    private final String pluginId;
    private final ThreadLocal<java.util.IdentityHashMap<MethodHookParam, ActionBar.UnreadImageView>> badgeDraws = new ThreadLocal<>();
    private final PyObject handler;
    private final PyObject beforeCall;
    private final PyObject afterCall;
    private final HookStats beforeStats;
    private final HookStats afterStats;
    private final List<HookFilter> beforeFilters;
    private final List<HookFilter> afterFilters;

    public PyMethodHook(String pluginId, PyObject handler) {
        this(pluginId, handler, PRIORITY_DEFAULT, true, true);
    }

    public PyMethodHook(String pluginId, PyObject handler, int priority) {
        this(pluginId, handler, priority, true, true);
    }

    public PyMethodHook(String pluginId, PyObject handler, boolean before, boolean after) {
        this(pluginId, handler, PRIORITY_DEFAULT, before, after);
    }

    public PyMethodHook(String pluginId, PyObject handler, int priority,
                 boolean before, boolean after) {
        this(pluginId, handler, priority, before, after,
                Collections.emptyList(), Collections.emptyList());
    }

    public PyMethodHook(String pluginId, PyObject handler, int priority,
                 List<HookFilter> beforeFilters, List<HookFilter> afterFilters) {
        this(pluginId, handler, priority, true, true, beforeFilters, afterFilters);
    }

    private PyMethodHook(String pluginId, PyObject handler, int priority,
                 boolean before, boolean after,
                 List<HookFilter> beforeFilters, List<HookFilter> afterFilters) {
        super(priority);
        this.pluginId = pluginId;
        this.handler = handler;
        final int beforeId = BuildConfig.DEBUG_TOOLS ? HookStats.nextId() : -1;
        final int afterId = BuildConfig.DEBUG_TOOLS ? HookStats.nextId() : -1;
        this.beforeCall = before && handler != null
                ? XposedHooks.bindHook(handler, "before_hooked_method", beforeId) : null;
        this.afterCall = after && handler != null
                ? XposedHooks.bindHook(handler, "after_hooked_method", afterId) : null;
        this.beforeStats = BuildConfig.DEBUG_TOOLS && beforeCall != null ? new HookStats(beforeId, pluginId, "before") : null;
        this.afterStats = BuildConfig.DEBUG_TOOLS && afterCall != null ? new HookStats(afterId, pluginId, "after") : null;
        this.beforeFilters = beforeFilters;
        this.afterFilters = afterFilters;
    }

    @Override
    protected void beforeHookedMethod(MethodHookParam param) {
        if (afterCall != null && param.thisObject instanceof ActionBar.UnreadImageView
                && param.method != null && "onDraw".equals(param.method.getName())
                && HookFilter.evaluateAll(afterFilters, param, false)
                && ownsUnreadBadge(param.thisObject)) {
            ActionBar.UnreadImageView view = (ActionBar.UnreadImageView) param.thisObject;
            java.util.IdentityHashMap<MethodHookParam, ActionBar.UnreadImageView> draws = badgeDraws.get();
            if (draws == null) {
                draws = new java.util.IdentityHashMap<>();
                badgeDraws.set(draws);
            }
            draws.put(param, view);
            view.beginPluginUnreadBadge();
        }
        if (beforeCall != null && HookFilter.evaluateAll(beforeFilters, param, false)) {
            XposedHooks.callPython(pluginId, beforeCall, param, beforeStats);
            HookNumbers.coerce(param);
        }
    }

    @Override
    protected void afterHookedMethod(MethodHookParam param) {
        try {
            if (afterCall != null && HookFilter.evaluateAll(afterFilters, param, true)) {
                XposedHooks.callPython(pluginId, afterCall, param, afterStats);
                HookNumbers.coerce(param);
            }
        } finally {
            java.util.IdentityHashMap<MethodHookParam, ActionBar.UnreadImageView> draws = badgeDraws.get();
            if (draws != null) {
                ActionBar.UnreadImageView view = draws.remove(param);
                if (view != null) {
                    view.endPluginUnreadBadge();
                }
                if (draws.isEmpty()) {
                    badgeDraws.remove();
                }
            }
        }
    }

    void addTarget(java.lang.reflect.Member member) {
        if (beforeStats != null) {
            beforeStats.addTarget(member);
        }
        if (afterStats != null) {
            afterStats.addTarget(member);
        }
    }

    private PyObject badgePlugin;
    private boolean badgePluginResolved;

    private boolean ownsUnreadBadge(Object view) {
        try {
            if (!badgePluginResolved) {
                badgePlugin = handler.get("plugin");
                badgePluginResolved = true;
            }
            PyObject plugin = badgePlugin;
            if (plugin == null) {
                return false;
            }
            PyObject target = plugin.get("current_back_button");
            PyObject count = plugin.get("unread_count");
            return target != null && target.toJava(Object.class) == view
                    && count != null && count.toInt() > 0;
        } catch (RuntimeException ignored) {
            return false;
        }
    }
}
