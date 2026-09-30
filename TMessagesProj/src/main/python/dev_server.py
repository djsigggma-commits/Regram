"""TCP/JSON development server on 127.0.0.1:42690 — re:gram plugin SDK.

Protocol (PLUGINS-API.md §8.2): line-delimited UTF-8 JSON objects; ``"@"``
is the command name, ``"#"`` the client request id echoed back in the
response. Every response carries ``{"#": id, "ok": true|false}`` plus either
a result payload or an ``"error"`` string. Malformed lines get an error
response and the connection stays open.

Started once from extera_utils.plugin_loader.start_dev_server() (the engine
calls it in developer mode). Bind failures (port busy) disable the server
quietly — the plugin engine must keep working without it.
"""

import base64
import binascii
import json
import os
import secrets
import socketserver
import threading

HOST = "127.0.0.1"
PORT = 42690

_server = None
_failed = False
_start_lock = threading.Lock()

_debugger = {"listening": False, "host": None, "port": None, "platform": None}

# Токен сессии. Порт слушается на 127.0.0.1, но на Android это не граница:
# любое приложение на устройстве может подключиться и через write_plugin
# поставить свой плагин. Токен пишется в файл внутри каталога приложения и в
# лог, так что инструмент разработчика достаёт его через adb.
_token = None
TOKEN_FILE = ".devserver_token"
_FREE_COMMANDS = frozenset({"ping"})


def _ensure_token() -> str:
    global _token
    if _token is None:
        _token = secrets.token_hex(16)
    return _token


def _publish_token(token: str) -> None:
    try:
        import file_utils
        path = os.path.join(file_utils.get_plugins_dir(), TOKEN_FILE)
        with open(path, "w", encoding="utf-8") as handle:
            handle.write(token)
        _log(f"token written to {path}")
    except Exception as e:
        _log(f"cannot write token file: {e}")
    _log(f"token: {token}")


def _log(message):
    try:
        from android_utils import log
        log(f"[dev_server] {message}")
    except Exception:
        import sys
        print(f"[regram:dev_server] {message}", file=sys.stderr)


def _controller():
    from java import jclass
    return jclass("app.regram.plugins.PluginsController").getInstance()


def _incoming_plugins_dir() -> str:
    import file_utils
    base = file_utils.get_files_dir()
    if not base:
        raise RuntimeError("cannot resolve the app files directory")
    path = os.path.join(base, "incoming", "plugins")
    os.makedirs(path, exist_ok=True)
    return path


# Commands

def _cmd_ping(request):
    return {"pong": True}


def _plugin_json(p):
    return {
        "id": str(p.id) if p.id is not None else None,
        "name": str(p.name) if p.name is not None else None,
        "version": str(p.version) if p.version is not None else None,
        "author": str(p.author) if p.author is not None else None,
        "enabled": bool(p.enabled),
        "loaded": bool(p.loaded),
        "has_settings": bool(p.hasSettings),
        "error": str(p.loadError) if p.loadError is not None else None,
    }


def _cmd_get_plugins(request):
    controller = _controller()
    return {"plugins": [_plugin_json(p) for p in list(controller.getPluginsSnapshot())]}


def _require_plugin_id(request) -> str:
    plugin_id = request.get("plugin_id")
    if not plugin_id:
        raise ValueError("missing plugin_id")
    return str(plugin_id)


def _cmd_set_enabled(request, enabled: bool):
    plugin_id = _require_plugin_id(request)
    if not _controller().setPluginEnabled(plugin_id, enabled):
        return {"ok": False, "error": f"unknown plugin {plugin_id!r}"}
    return {}


def _cmd_reload_plugin(request):
    plugin_id = _require_plugin_id(request)
    controller = _controller()
    if controller.getPlugin(plugin_id) is None:
        return {"ok": False, "error": f"unknown plugin {plugin_id!r}"}
    controller.reloadPlugin(plugin_id)
    plugin = controller.getPlugin(plugin_id)
    error = plugin.loadError if plugin is not None else None
    if error:
        return {"ok": False, "error": str(error)}
    return {}


def _cmd_write_plugin(request):
    plugin_id = _require_plugin_id(request)
    content = request.get("content")
    if not isinstance(content, str):
        raise ValueError("missing content (base64-encoded plugin source)")
    try:
        data = base64.b64decode(content)
    except (binascii.Error, ValueError) as e:
        raise ValueError(f"content is not valid base64: {e}")

    filename = request.get("filename") or (plugin_id + ".py")
    filename = os.path.basename(str(filename))
    if not filename.endswith((".py", ".elyx", ".eaf", ".plugin")):
        filename += ".py"
    target = os.path.join(_incoming_plugins_dir(), filename)
    with open(target, "wb") as handle:
        handle.write(data)

    # Штатный загрузчик: PluginsController.installPlugin (async, UI-thread
    # callback) — wait for it here; the dev-server handler thread is ours.
    from java import dynamic_proxy, jclass

    InstallCallback = jclass(
        "app.regram.plugins.PluginsController$InstallCallback")
    File = jclass("java.io.File")
    done = threading.Event()
    outcome = {}

    class _Callback(dynamic_proxy(InstallCallback)):
        def onResult(self, ok, error, plugin):
            outcome["ok"] = bool(ok)
            outcome["error"] = str(error) if error is not None else None
            done.set()

    _controller().installPlugin(File(target), _Callback())
    if not done.wait(120):
        return {"ok": False, "error": "install timed out"}
    if not outcome.get("ok"):
        return {"ok": False, "error": outcome.get("error") or "install failed"}
    return {}


def _cmd_remove_plugin(request):
    plugin_id = _require_plugin_id(request)
    # Python-side cleanup first (unload + pip refcounts); the controller call
    # afterwards removes the file, prefs and registry entry.
    try:
        from extera_utils import plugin_loader
        plugin_loader.uninstall_plugin(plugin_id)
    except Exception as e:
        _log(f"python-side uninstall of {plugin_id!r} failed: {e}")
    if not _controller().uninstallPlugin(plugin_id):
        return {"ok": False, "error": f"unknown plugin {plugin_id!r}"}
    return {}


def _cmd_start_debugger(request):
    host = str(request.get("host") or "127.0.0.1")
    port = int(request.get("port") or 5678)
    platform = str(request.get("platform") or "vscode")
    if _debugger["listening"]:
        return {"ok": True, "debugger": dict(_debugger),
                "note": "debugger already listening"}
    try:
        import debugpy
    except ImportError:
        return {"ok": False, "error": "debugpy is not available"}
    try:
        debugpy.listen((host, port))
    except Exception as e:
        return {"ok": False, "error": f"debugpy.listen failed: {e}"}
    _debugger.update({"listening": True, "host": host, "port": port,
                      "platform": platform})
    _log(f"debugpy listening on {host}:{port} ({platform})")
    return {"debugger": dict(_debugger)}


def _cmd_stop_debugger(request):
    try:
        import debugpy
    except ImportError:
        return {"ok": False, "error": "debugpy is not available"}
    stop = getattr(debugpy, "stop_listening", None)
    if stop is None:
        return {"ok": False,
                "error": "stop_debugger is unsupported by the installed debugpy "
                         "(no stop_listening API); restart the app to release the port"}
    try:
        stop()
    except Exception as e:
        return {"ok": False, "error": f"debugpy stop failed: {e}"}
    _debugger.update({"listening": False, "host": None, "port": None,
                      "platform": None})
    return {}


def _cmd_elyx(request):
    """elyx_* commands delegate to elyx_runtime (owned by another agent)."""
    try:
        import elyx_runtime
    except ImportError:
        return {"ok": False, "error": "Elyx runtime unavailable"}
    handler = getattr(elyx_runtime, "handle_dev_command", None)
    if handler is None:
        return {"ok": False,
                "error": "Elyx runtime has no handle_dev_command entry point"}
    result = handler(request)
    return dict(result) if isinstance(result, dict) else {}


_COMMANDS = {
    "ping": _cmd_ping,
    "get_plugins": _cmd_get_plugins,
    "enable_plugin": lambda request: _cmd_set_enabled(request, True),
    "disable_plugin": lambda request: _cmd_set_enabled(request, False),
    "reload_plugin": _cmd_reload_plugin,
    "write_plugin": _cmd_write_plugin,
    "remove_plugin": _cmd_remove_plugin,
    "start_debugger": _cmd_start_debugger,
    "stop_debugger": _cmd_stop_debugger,
}


def dispatch(request: dict) -> dict:
    """Execute one request object; always returns a response dict."""
    request_id = request.get("#")
    command = request.get("@")
    response = {"#": request_id, "ok": True}
    if not isinstance(command, str) or not command:
        return {**response, "ok": False, "error": "missing \"@\" command"}
    if command not in _FREE_COMMANDS:
        supplied = request.get("token")
        expected = _ensure_token()
        if not isinstance(supplied, str) \
                or not secrets.compare_digest(supplied, expected):
            return {**response, "ok": False, "error": "bad or missing token"}
    try:
        if command.startswith("elyx_") or command == "get_elyx_plugins":
            payload = _cmd_elyx(request)
        else:
            handler = _COMMANDS.get(command)
            if handler is None:
                return {**response, "ok": False,
                        "error": f"unknown command {command!r}"}
            payload = handler(request)
        # A payload may override "ok" to False (command-level failure).
        response.update(payload or {})
    except Exception as e:
        return {"#": request_id, "ok": False,
                "error": f"{type(e).__name__}: {e}"}
    return response


# Server plumbing

class _Handler(socketserver.StreamRequestHandler):
    def handle(self):
        while True:
            try:
                line = self.rfile.readline()
            except Exception:
                return
            if not line:
                return  # client went away
            line = line.strip()
            if not line:
                continue
            try:
                request = json.loads(line.decode("utf-8"))
                if not isinstance(request, dict):
                    raise ValueError("request must be a JSON object")
            except Exception as e:
                self._respond({"#": None, "ok": False,
                               "error": f"malformed request: {e}"})
                continue
            self._respond(dispatch(request))

    def _respond(self, obj: dict):
        try:
            self.wfile.write(json.dumps(obj, ensure_ascii=False).encode("utf-8") + b"\n")
            self.wfile.flush()
        except Exception:
            raise ConnectionAbortedError("client disconnected")


class _Server(socketserver.ThreadingTCPServer):
    daemon_threads = True
    allow_reuse_address = True


def start() -> bool:
    """Start the dev server once; False when unavailable (bind failure)."""
    global _server, _failed
    with _start_lock:
        if _server is not None:
            return True
        if _failed:
            return False
        try:
            server = _Server((HOST, PORT), _Handler)
        except Exception as e:
            _failed = True
            _log(f"cannot bind {HOST}:{PORT} — dev server disabled: {e}")
            return False
        thread = threading.Thread(target=server.serve_forever,
                                  name="extera-dev-server", daemon=True)
        thread.start()
        _server = server
        _publish_token(_ensure_token())
        _log(f"listening on {HOST}:{PORT}")
        return True


def stop() -> None:
    global _server
    with _start_lock:
        server, _server = _server, None
    if server is not None:
        try:
            server.shutdown()
            server.server_close()
        except Exception:
            pass
