package app.regram.plugins.xposed;

import java.lang.reflect.Member;
import java.lang.reflect.Method;

/** Narrow binary-shim hook aliases. Never redirect arbitrary plugin methods or constructors. */
public final class PluginHookTargets {
    private PluginHookTargets() {}
    private static volatile Method pillLayoutSave;

    public static Member resolve(Member member) {
        if (!(member instanceof Method)) return member;
        Method method = (Method) member;
        if (method.getDeclaringClass() != com.exteragram.messenger.pillstack.core.PillStackConfig.class
                || !method.getName().equals("savePillsLayout") || method.getParameterTypes().length != 0
                || method.getReturnType() != void.class) return member;
        Method target = pillLayoutSave;
        if (target == null) {
            try {
                target = app.regram.pillstack.PillStackConfig.class.getDeclaredMethod("savePillsLayout");
                pillLayoutSave = target;
            } catch (NoSuchMethodException e) {
                return member;
            }
        }
        return target;
    }
}
