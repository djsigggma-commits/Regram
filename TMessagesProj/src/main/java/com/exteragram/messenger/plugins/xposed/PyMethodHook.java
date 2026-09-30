package com.exteragram.messenger.plugins.xposed;

import com.chaquo.python.PyObject;

import java.util.Collections;

/**
 * Шим {@code com.exteragram.messenger.plugins.xposed.PyMethodHook}.
 *
 * Настоящий конструктор принимает ещё и разобранные фильтры; напрямую из
 * плагина их не передают, поэтому здесь пустые списки — фильтрация в этом
 * случае и не нужна, хук зовётся всегда.
 */
public class PyMethodHook extends app.regram.plugins.xposed.PyMethodHook {

    public PyMethodHook(String pluginId, PyObject handler, int priority) {
        super(pluginId, handler, priority, Collections.emptyList(), Collections.emptyList());
    }

    public PyMethodHook(String pluginId, PyObject handler) {
        this(pluginId, handler, 50);
    }
}
