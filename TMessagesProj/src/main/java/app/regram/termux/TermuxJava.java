package app.regram.termux;

import android.app.Activity;
import android.os.Build;
import android.view.View;
import org.json.JSONArray;
import org.json.JSONObject;
import java.lang.reflect.*;
import java.util.*;

/** Explicit RPC calls on public Java APIs; never patches or intercepts client methods. */
final class TermuxJava {
    private final TermuxBridge bridge;
    private final TermuxUi ui;
    private final Map<Long, Handle> handles = new java.util.concurrent.ConcurrentHashMap<>();
    private final java.util.concurrent.atomic.AtomicLong sequence = new java.util.concurrent.atomic.AtomicLong();
    private static final class Handle {
        final String owner;
        final Object value;
        Handle(String owner, Object value) { this.owner = owner; this.value = value; }
    }
    private static final class Typed {
        final Class<?> type;
        final Object value;
        Typed(Class<?> type, Object value) { this.type = type; this.value = value; }
    }
    TermuxJava(TermuxBridge bridge, TermuxUi ui) { this.bridge = bridge; this.ui = ui; }
    JSONObject info() throws Exception {
        Activity activity = bridge.activity();
        android.util.DisplayMetrics metrics = activity.getResources().getDisplayMetrics();
        return TermuxBridge.ok().put("density", metrics.density).put("width", metrics.widthPixels)
                .put("height", metrics.heightPixels).put("sdk", Build.VERSION.SDK_INT).put("package", activity.getPackageName());
    }
    JSONObject command(TermuxBridge.Session session, JSONObject msg) throws Exception {
        String owner = session.sid;
        JSONArray release = msg.optJSONArray("release");
        if (release != null) for (int i = 0; i < release.length(); i++) {
            Handle handle = handles.get(release.getLong(i));
            if (handle != null && handle.owner.equals(owner)) handles.remove(release.getLong(i));
        }
        Object result = null;
        String cmd = msg.optString("cmd");
        if (cmd.equals("j_cls")) result = Class.forName(msg.getString("name"));
        else if (cmd.equals("j_activity")) result = bridge.activity();
        else if (cmd.equals("j_listener")) return TermuxBridge.ok().put("r", listener(session, msg));
        else {
            Object target = get(owner, msg.getLong("h"));
            Class<?> type = target instanceof Class ? (Class<?>) target : target.getClass();
            Object receiver = target instanceof Class ? null : target;
            switch (cmd) {
                case "j_new": {
                    if (!(target instanceof Class)) throw new IllegalArgumentException("not a class");
                    Object[] args = args(owner, msg.optJSONArray("args"));
                    Constructor<?> constructor = (Constructor<?>) best(type.getConstructors(), null, args, false);
                    result = constructor.newInstance(convertAll(args, constructor.getParameterTypes())); break;
                }
                case "j_call": {
                    Object[] args = args(owner, msg.optJSONArray("args"));
                    Method method = (Method) best(type.getMethods(), msg.getString("name"), args, receiver == null);
                    // Public methods on non-public implementation classes need accessibility on Android.
                    if (!Modifier.isPublic(method.getDeclaringClass().getModifiers())) method.setAccessible(true);
                    result = method.invoke(receiver, convertAll(args, method.getParameterTypes())); break;
                }
                case "j_get": result = type.getField(msg.getString("name")).get(receiver); break;
                case "j_set": {
                    Field field = type.getField(msg.getString("name"));
                    field.set(receiver, convert(decode(owner, msg.opt("value"), 0), field.getType())); break;
                }
                case "j_str": result = String.valueOf(target); break;
                case "j_list": {
                    JSONArray list = new JSONArray();
                    if (target.getClass().isArray()) {
                        if (Array.getLength(target) > 4096) throw new IllegalArgumentException("list too large");
                        for (int i = 0; i < Array.getLength(target); i++) list.put(encode(owner, Array.get(target, i)));
                    } else if (target instanceof Iterable) {
                        for (Object item : (Iterable<?>) target) {
                            if (list.length() >= 4096) throw new IllegalArgumentException("list too large");
                            list.put(encode(owner, item));
                        }
                    } else throw new IllegalArgumentException("not iterable");
                    return TermuxBridge.ok().put("r", list);
                }
                case "j_add_view": ui.addJavaView(owner, (View) target, msg); break;
                case "j_remove_view": ui.removeJavaView(owner, (View) target); break;
                default: return TermuxBridge.error("unknown Java cmd");
            }
        }
        return TermuxBridge.ok().put("r", encode(owner, result));
    }
    private Object listener(TermuxBridge.Session session, JSONObject msg) throws Exception {
        Class<?> iface = Class.forName(msg.getString("iface"));
        if (!iface.isInterface()) throw new IllegalArgumentException("listener must be an interface");
        Set<String> names = new HashSet<>();
        JSONArray methods = msg.optJSONArray("methods");
        if (methods != null) for (int i = 0; i < methods.length(); i++) names.add(methods.getString(i));
        JSONObject returns = msg.optJSONObject("returns");
        final long id = sequence.incrementAndGet();
        Object listener = Proxy.newProxyInstance(iface.getClassLoader(), new Class<?>[]{iface}, (proxy, method, args) -> {
            if (method.getDeclaringClass() == Object.class) {
                switch (method.getName()) {
                    case "hashCode": return System.identityHashCode(proxy);
                    case "equals": return proxy == args[0];
                    default: return "Termux listener " + iface.getName();
                }
            }
            // Listener notifications must not write sockets synchronously on the Android UI.
            try {
                bridge.requireListener(session);
                if (names.contains(method.getName())) {
                    JSONArray encoded = new JSONArray();
                    if (args != null) for (Object arg : args) encoded.put(encode(session.sid, arg));
                    bridge.emit(session.sid, new JSONObject().put("event", "jcall").put("h", id)
                            .put("method", method.getName()).put("args", encoded));
                }
                if (returns != null && returns.has(method.getName()))
                    return convert(decode(session.sid, returns.opt(method.getName()), 0), method.getReturnType());
            } catch (Exception ignored) { }
            return defaultValue(method.getReturnType());
        });
        register(id, session.sid, listener);
        // Avoid assigning a second ID: callbacks use the reserved one.
        return new JSONObject().put("$h", id).put("t", iface.getName());
    }
    private Object get(String owner, long id) {
        Handle handle = handles.get(id);
        if (handle == null || !handle.owner.equals(owner)) throw new IllegalArgumentException("Java handle unavailable in this session");
        return handle.value;
    }
    private synchronized void register(long id, String owner, Object value) {
        if (handles.size() >= 4096) throw new IllegalStateException("too many Java handles");
        handles.put(id, new Handle(owner, value));
    }
    private Object encode(String owner, Object value) throws Exception {
        if (value == null) return JSONObject.NULL;
        if (value instanceof String || value instanceof Boolean || value instanceof Number) return value;
        if (value instanceof Character) return value.toString();
        long id = sequence.incrementAndGet(); register(id, owner, value);
        return new JSONObject().put("$h", id).put("t", value instanceof Class ? "class" : value.getClass().getName());
    }
    private Object[] args(String owner, JSONArray values) throws Exception {
        if (values == null) return new Object[0];
        if (values.length() > 64) throw new IllegalArgumentException("too many arguments");
        Object[] out = new Object[values.length()];
        for (int i = 0; i < out.length; i++) out[i] = decode(owner, values.get(i), 0);
        return out;
    }
    private Object decode(String owner, Object value, int depth) throws Exception {
        if (depth > 8) throw new IllegalArgumentException("argument nesting too deep");
        if (value == JSONObject.NULL) return null;
        if (value instanceof JSONObject) {
            JSONObject obj = (JSONObject) value;
            if (obj.has("$h")) return get(owner, obj.getLong("$h"));
            if (obj.has("$cast")) {
                Class<?> type = type(obj.getString("$cast"));
                return new Typed(type, convert(decode(owner, obj.opt("v"), depth + 1), type));
            }
            if (obj.has("$arr")) {
                Class<?> type = type(obj.getString("$arr"));
                JSONArray values = obj.getJSONArray("v");
                if (values.length() > 4096) throw new IllegalArgumentException("array too large");
                Object array = Array.newInstance(type, values.length());
                for (int i = 0; i < values.length(); i++) Array.set(array, i, convert(decode(owner, values.get(i), depth + 1), type));
                return array;
            }
        }
        if (value instanceof JSONArray) {
            JSONArray values = (JSONArray) value;
            if (values.length() > 4096) throw new IllegalArgumentException("array too large");
            Object[] array = new Object[values.length()];
            for (int i = 0; i < array.length; i++) array[i] = decode(owner, values.get(i), depth + 1);
            return array;
        }
        return value;
    }
    private static Class<?> type(String name) throws Exception {
        switch (name) {
            case "int": return int.class;
            case "long": return long.class;
            case "float": return float.class;
            case "double": return double.class;
            case "boolean": return boolean.class;
            case "short": return short.class;
            case "byte": return byte.class;
            case "char": return char.class;
            case "String": return String.class;
            default: return Class.forName(name);
        }
    }
    private static Class<?> box(Class<?> type) {
        if (!type.isPrimitive()) return type;
        if (type == int.class) return Integer.class;
        if (type == long.class) return Long.class;
        if (type == float.class) return Float.class;
        if (type == double.class) return Double.class;
        if (type == short.class) return Short.class;
        if (type == byte.class) return Byte.class;
        if (type == char.class) return Character.class;
        if (type == boolean.class) return Boolean.class;
        return Void.class;
    }
    private static int numericRank(Class<?> type) {
        type = box(type);
        if (type == Byte.class) return 0;
        if (type == Short.class) return 1;
        if (type == Integer.class) return 2;
        if (type == Long.class) return 3;
        if (type == Float.class) return 4;
        if (type == Double.class) return 5;
        return -1;
    }
    private static int score(Object value, Class<?> target) {
        Class<?> forced = value instanceof Typed ? ((Typed) value).type : null;
        if (forced != null) value = ((Typed) value).value;
        if (value == null) {
            if (target.isPrimitive()) return -1;
            if (forced == null) return 20;
            return target == forced ? 0 : target.isAssignableFrom(forced) ? 2 : -1;
        }
        if (forced != null && box(target) == box(forced)) return 0;
        if (forced != null && numericRank(forced) >= 0 && numericRank(target) >= numericRank(forced)) return 2 + numericRank(target) - numericRank(forced);
        if (forced != null && target.isPrimitive()) return -1;
        if (box(target) == value.getClass()) return 0;
        if (target.isInstance(value)) return target == Object.class ? 15 : 2;
        if (value instanceof Number && numericRank(target) >= 0) {
            int source = numericRank(value.getClass()), dest = numericRank(target);
            if (source > dest) return -1;
            return 3 + dest - source;
        }
        return -1;
    }
    private static Object convert(Object value, Class<?> target) {
        if (value instanceof Typed) value = ((Typed) value).value;
        if (value == null && !target.isPrimitive()) return null;
        if (box(target).isInstance(value)) return value;
        if (target == char.class || target == Character.class) {
            if (value instanceof String && ((String) value).length() == 1) return ((String) value).charAt(0);
        }
        if (value instanceof Number) {
            Number number = (Number) value;
            Class<?> boxed = box(target);
            if (boxed == Integer.class) return number.intValue();
            if (boxed == Long.class) return number.longValue();
            if (boxed == Float.class) return number.floatValue();
            if (boxed == Double.class) return number.doubleValue();
            if (boxed == Short.class) return number.shortValue();
            if (boxed == Byte.class) return number.byteValue();
        }
        if (value != null && target.isInstance(value)) return value;
        throw new IllegalArgumentException("cannot convert argument to " + target.getName());
    }
    private static Member best(Member[] members, String name, Object[] args, boolean requireStatic) {
        Member best = null; int bestScore = Integer.MAX_VALUE;
        for (Member member : members) {
            if (member instanceof Method && (!member.getName().equals(name) || (requireStatic && !Modifier.isStatic(member.getModifiers())))) continue;
            Class<?>[] types = member instanceof Method ? ((Method) member).getParameterTypes() : ((Constructor<?>) member).getParameterTypes();
            if (types.length != args.length) continue;
            int total = 0;
            for (int i = 0; i < args.length; i++) {
                int score = score(args[i], types[i]);
                if (score < 0) { total = Integer.MAX_VALUE; break; }
                total += score;
            }
            if (total < bestScore) { best = member; bestScore = total; }
        }
        if (best == null) throw new IllegalArgumentException("no matching public overload; use explicit casts");
        return best;
    }
    private static Object[] convertAll(Object[] args, Class<?>[] types) {
        Object[] converted = new Object[args.length];
        for (int i = 0; i < args.length; i++) converted[i] = convert(args[i], types[i]);
        return converted;
    }
    private static Object defaultValue(Class<?> type) {
        if (!type.isPrimitive() || type == void.class) return null;
        if (type == boolean.class) return false;
        if (type == char.class) return '\0';
        return convert(0, type);
    }
    void cleanupOwner(String owner) { handles.entrySet().removeIf(entry -> entry.getValue().owner.equals(owner)); }
    void clearAll() { handles.clear(); }
}
