"""Dev-server command handler for Elyx live-sync (PLUGINS-ELYX.md §11).

The dev server (owned by another agent) frames JSON commands over TCP; this
module implements the Elyx actions:

    elyx_ping            -> {"ok": True, "elyx": True, "version": ...}
    get_elyx_plugins     -> {"ok": True, "plugins": [<metainfo dict>, ...]}
    elyx_compare_folder  -> compare client file hashes with the installed
                            (extracted) plugin tree
    elyx_changes         -> apply created/modified/deleted/moved entries to the
                            installed tree, then unload + reload the plugin

Protocol notes (from the spec): change entries look like
    {"#": "modified", "path": "main.py", "is_directory": false,
     "content": "<base64 file bytes>"}
with event types created | modified | deleted | moved; `moved` entries use
`path` + `dest_path`; paths are relative to the plugin root (the extracted
archive directory of the current content version).

elyx_compare_folder input:  {"plugin_id": ..., "hashes": {relpath: sha256-hex}}
elyx_compare_folder output: {"ok": True, "created_or_modified": [...],
                             "deleted": [...]}
    created_or_modified — client paths the server is missing or whose hash
                          differs (client should upload them);
    deleted             — server paths the client no longer has.

Reload goes through extera_utils.plugin_loader (which owns the plugin
registry and lifecycle) so hooks/menus/settings rebuild exactly like a normal
reload; when the plugin is not registered there (e.g. host runs), the modules
are evicted so the next enable loads fresh code.
"""

from __future__ import annotations

import base64
import hashlib
import importlib
import os
import shutil
from typing import Any, Dict, List, Optional

from .errors import ElyxArchiveError
from .loader import _teardown, get_state
from .namespace import evict_modules


# Path safety

def _safe_join(root: str, relative: str) -> str:
    """Join a client-relative path onto *root*, rejecting traversal."""
    if not isinstance(relative, str) or not relative:
        raise ElyxArchiveError(f"bad relative path {relative!r}")
    normalized = relative.replace("\\", "/")
    if normalized.startswith("/") or (len(normalized) > 1 and normalized[1] == ":"):
        raise ElyxArchiveError(f"absolute path not allowed: {relative!r}")
    if any(part == ".." for part in normalized.split("/")):
        raise ElyxArchiveError(f"path traversal not allowed: {relative!r}")
    return os.path.join(root, *normalized.split("/"))


def _file_sha256(path: str) -> str:
    digest = hashlib.sha256()
    with open(path, "rb") as handle:
        for chunk in iter(lambda: handle.read(65536), b""):
            digest.update(chunk)
    return digest.hexdigest()


def _plugin_root(plugin_id: str) -> str:
    state = get_state(plugin_id)
    if state is None:
        raise KeyError(
            f"Elyx plugin {plugin_id!r} is not loaded (install and enable it "
            "at least once before live-sync)"
        )
    return state.extract_dir


# Commands

def _cmd_ping(_params: Dict[str, Any]) -> Dict[str, Any]:
    from . import __version__  # lazy: the package is fully initialized by now

    return {"ok": True, "elyx": True, "version": __version__}


def _cmd_get_elyx_plugins(_params: Dict[str, Any]) -> Dict[str, Any]:
    from .loader import _states

    plugins = []
    for plugin_id, state in _states.items():
        entry = dict(state.metainfo)
        entry["archive_path"] = state.archive_path
        entry["extract_dir"] = state.extract_dir
        plugins.append(entry)
    return {"ok": True, "plugins": plugins}


def _cmd_compare_folder(params: Dict[str, Any]) -> Dict[str, Any]:
    plugin_id = params.get("plugin_id")
    hashes = params.get("hashes") or {}
    if not isinstance(hashes, dict):
        return {"ok": False, "error": "hashes must be a {relpath: sha256} mapping"}
    try:
        root = _plugin_root(str(plugin_id))
    except KeyError as e:
        return {"ok": False, "error": str(e)}

    client = {str(path).replace("\\", "/"): str(digest) for path, digest in hashes.items()}
    outdated: List[str] = []
    for relative, client_hash in client.items():
        try:
            server_path = _safe_join(root, relative)
        except ElyxArchiveError:
            continue
        if not os.path.isfile(server_path) or _file_sha256(server_path) != client_hash:
            outdated.append(relative)

    extra: List[str] = []
    for dirpath, _dirnames, filenames in os.walk(root):
        for filename in filenames:
            full = os.path.join(dirpath, filename)
            relative = os.path.relpath(full, root).replace(os.sep, "/")
            if relative not in client:
                extra.append(relative)

    return {"ok": True, "plugin_id": plugin_id,
            "created_or_modified": sorted(outdated), "deleted": sorted(extra)}


def _apply_change(root: str, change: Dict[str, Any]) -> Optional[str]:
    """Apply one change entry; returns an error string or None."""
    kind = change.get("#") or change.get("type")
    path = change.get("path")
    try:
        if kind in ("created", "modified"):
            target = _safe_join(root, path)
            if change.get("is_directory"):
                os.makedirs(target, exist_ok=True)
                return None
            os.makedirs(os.path.dirname(target), exist_ok=True)
            content = change.get("content") or ""
            data = base64.b64decode(content)
            with open(target, "wb") as handle:
                handle.write(data)
            return None
        if kind == "deleted":
            target = _safe_join(root, path)
            if os.path.isdir(target):
                shutil.rmtree(target, ignore_errors=True)
            elif os.path.exists(target):
                os.remove(target)
            return None
        if kind == "moved":
            source = _safe_join(root, path)
            dest = _safe_join(root, change.get("dest_path"))
            os.makedirs(os.path.dirname(dest), exist_ok=True)
            os.replace(source, dest)
            return None
        return f"unknown change type {kind!r}"
    except Exception as e:
        return f"{kind} {path}: {type(e).__name__}: {e}"


def _reload_loaded_plugin(plugin_id: str) -> Dict[str, Any]:
    """Reload through the host plugin loader; degrade to module eviction."""
    state = get_state(plugin_id)
    try:
        plugin_loader = importlib.import_module("extera_utils.plugin_loader")
    except Exception:
        plugin_loader = None

    if plugin_loader is not None and state is not None:
        try:
            registry = getattr(plugin_loader, "plugins", {})
            if plugin_id in registry:
                plugin_loader.unload_plugin(plugin_id)
                result = plugin_loader.load_plugin(state.archive_path, plugin_id)
                import json as _json

                try:
                    payload = _json.loads(result)
                except Exception:
                    payload = {}
                if payload.get("ok"):
                    return {"reloaded": True, "errors": []}
                return {"reloaded": False,
                        "errors": [f"reload failed: {payload.get('error', result)!r}"]}
        except Exception as e:
            return {"reloaded": False, "errors": [f"reload failed: {type(e).__name__}: {e}"]}

    # Not registered with the host loader (host run / integrator pending):
    # evict so the next enable executes fresh code.
    if state is not None:
        _teardown(plugin_id)
    else:
        evict_modules(plugin_id)
    return {"reloaded": False,
            "errors": ["plugin is not loaded by the host loader; modules evicted, "
                       "changes apply on next enable"]}


def _cmd_changes(params: Dict[str, Any]) -> Dict[str, Any]:
    plugin_id = params.get("plugin_id")
    changes = params.get("changes")
    if not isinstance(changes, list):
        return {"ok": False, "error": "changes must be a list"}
    try:
        root = _plugin_root(str(plugin_id))
    except KeyError as e:
        return {"ok": False, "error": str(e)}

    errors: List[str] = []
    for change in changes:
        if not isinstance(change, dict):
            errors.append(f"bad change entry {change!r}")
            continue
        error = _apply_change(root, change)
        if error:
            errors.append(error)

    reload_result = _reload_loaded_plugin(str(plugin_id))
    errors.extend(reload_result["errors"])
    return {"ok": not errors, "plugin_id": plugin_id,
            "reloaded": reload_result["reloaded"], "errors": errors}


_HANDLERS = {
    "elyx_ping": _cmd_ping,
    "get_elyx_plugins": _cmd_get_elyx_plugins,
    "elyx_compare_folder": _cmd_compare_folder,
    "elyx_changes": _cmd_changes,
}


def handle_dev_command(command: str, params: Optional[Dict[str, Any]] = None) -> Dict[str, Any]:
    """Dispatch one dev-server Elyx command. Always returns a dict."""
    name = str(command or "").lstrip("@")
    handler = _HANDLERS.get(name)
    if handler is None:
        return {"ok": False,
                "error": f"unknown Elyx command {command!r}",
                "known": sorted(_HANDLERS)}
    try:
        return handler(dict(params or {}))
    except Exception as e:
        return {"ok": False, "error": f"{type(e).__name__}: {e}"}
