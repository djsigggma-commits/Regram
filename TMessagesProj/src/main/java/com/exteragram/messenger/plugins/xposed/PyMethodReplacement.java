package com.exteragram.messenger.plugins.xposed;

import com.chaquo.python.PyObject;

import java.util.Collections;

/** Шим {@code com.exteragram.messenger.plugins.xposed.PyMethodReplacement}. */
public class PyMethodReplacement extends app.regram.plugins.xposed.PyMethodReplacement {

    public PyMethodReplacement(String pluginId, PyObject handler, int priority) {
        super(pluginId, handler, priority, Collections.emptyList());
    }

    public PyMethodReplacement(String pluginId, PyObject handler) {
        this(pluginId, handler, 50);
    }
}
