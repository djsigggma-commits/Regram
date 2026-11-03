"""Built-in, opt-in Termux exec helper; not a plugin and installs no hooks.

The bridge/UI/protocol are Java. CPython is used only for explicitly requested
in-process Python scripts. These scripts are trusted code, NOT sandboxed.
"""
import builtins
import json
import traceback


def run(code, bridge, session):
    # Never redirect process-global stdout: other plugins share this interpreter.
    output = []
    remaining = 65536

    def captured_print(*args, sep=" ", end="\n", file=None, flush=False):
        nonlocal remaining
        if file is not None:
            return builtins.print(*args, sep=sep, end=end, file=file, flush=flush)
        value = sep.join(str(arg) for arg in args) + end
        if remaining > 0:
            output.append(value[:remaining])
            remaining -= len(output[-1])

    def command(cmd, **kwargs):
        result = json.loads(str(bridge.executeLocal(session, json.dumps(dict(kwargs, cmd=cmd)))))
        if not result.get("ok"):
            raise RuntimeError(result.get("error", "bridge command failed"))
        return result

    import java
    namespace = {
        "__name__": "__termux__",
        "print": captured_print,
        "jclass": java.jclass,
        "ui": lambda fn: bridge.pythonUi(session, fn),
        "fragment": bridge.fragment,
        "activity": lambda: java.jclass("org.telegram.ui.LaunchActivity").instance,
        "show": lambda text, **kw: command("show", text=str(text), **kw),
        "append": lambda text: command("append", text=str(text)),
        "clear": lambda: command("clear"),
        "notify": lambda text, kind="info": command("notify", text=str(text), kind=kind),
    }
    for name in ("jint", "jfloat", "jdouble", "jlong", "jboolean", "jshort", "jbyte", "jchar", "jarray"):
        if hasattr(java, name):
            namespace[name] = getattr(java, name)
    try:
        exec(compile(code, "<termux>", "exec"), namespace)
        return json.dumps({"ok": True, "output": "".join(output)})
    except Exception:
        captured_print(traceback.format_exc(), end="")
        return json.dumps({"ok": False, "error": "exception", "output": "".join(output)})
