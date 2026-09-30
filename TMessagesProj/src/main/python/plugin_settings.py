"""Хранилище настроек плагинов из SDK exteraGram.

У нас настройки живут на стороне Java (SharedPreferences на плагин), а `BasePlugin`
ходит в них через `PythonBridge`. Плагины каталога при этом ждут отдельный модуль
`plugin_settings` с функциями уровня модуля и — как zwylib — лезут в его приватные
`_lock`, `_settings_cache` и `_save_settings_to_file`, чтобы удалить ключ.

Поэтому модуль держит зеркало настроек в памяти и синхронизирует его с Java:
чтение и запись идут в обе стороны, а `_save_settings_to_file` переписывает
хранилище целиком — иначе выкинутый из кэша ключ остался бы в SharedPreferences.
"""

import json as _json
import threading as _threading

try:
    from app.regram.plugins import PythonBridge as _bridge
except Exception:
    try:
        from extera_utils.plugin_loader import engine_java_class as _engine_class
        _bridge = _engine_class("app.regram.plugins.PythonBridge")
    except Exception:
        _bridge = None

_lock = _threading.RLock()
_settings_cache = {}
# То же словарное дерево под именем из SDK: плагины-обёртки читают `_cache`.
_cache = _settings_cache
_synced = {}

def _decode(raw, default=None):
    if raw is None:
        return default
    try:
        return _json.loads(raw)
    except (ValueError, TypeError):
        return default


def _load_plugin(plugin_id):
    if _bridge is None:
        return {}
    try:
        raw = _bridge.exportSettings(plugin_id)
    except Exception:
        return {}
    data = _decode(raw, {})
    if not isinstance(data, dict):
        return {}
    return {key: _decode(value, value) for key, value in data.items()}


def _snapshot(values):
    try:
        return _json.dumps(values, ensure_ascii=False, sort_keys=True, default=repr)
    except Exception:
        return None


def _cached(plugin_id):
    cached = _settings_cache.get(plugin_id)
    if cached is None:
        cached = _load_plugin(plugin_id)
        _settings_cache[plugin_id] = cached
        _synced[plugin_id] = _snapshot(cached)
    return cached


def get_setting(plugin_id, key, default=None):
    with _lock:
        return _cached(plugin_id).get(key, default)


def set_setting(plugin_id, key, value, reload_settings=False):
    with _lock:
        cached = _cached(plugin_id)
        cached[key] = value
        if _bridge is None:
            return
        try:
            _bridge.setSetting(plugin_id, key,
                               _json.dumps(value, ensure_ascii=False),
                               bool(reload_settings))
        except Exception:
            return
        _settings_cache[plugin_id] = cached
        _synced[plugin_id] = _snapshot(cached)


def get_settings(plugin_id):
    with _lock:
        return dict(_cached(plugin_id))


# Имя из SDK: все настройки плагина одним dict.
get_all_settings = get_settings


def get_plugin_config(plugin_id, key=None, default=None):
    """Aliases of the SDK names: ``get_plugin_config(id)`` == ``get_settings(id)``.

    С ``key=`` — то же, что get_setting(plugin_id, key, default).
    """
    if key is None:
        return get_settings(plugin_id)
    return get_setting(plugin_id, key, default)


def set_all_settings(plugin_id, settings, reload_settings=False):
    """Заменить все настройки плагина (значения unknown-типов не хранятся)."""
    values = dict(settings or {})
    with _lock:
        _settings_cache[plugin_id] = values
        if _bridge is None:
            return
        payload = {key: _json.dumps(value, ensure_ascii=False)
                   for key, value in values.items()}
        try:
            _bridge.replaceSettings(plugin_id, _json.dumps(payload, ensure_ascii=False))
        except Exception:
            pass
    if reload_settings:
        _reload_screen(plugin_id)


def clear_settings(plugin_id, reload_settings=False):
    """Сбросить настройки плагина: пустой набор и в кэше, и в хранилище."""
    set_all_settings(plugin_id, {}, reload_settings=reload_settings)


def _reload_screen(plugin_id):
    """Перерисовать экран настроек плагина, если он открыт."""
    if _bridge is None:
        return
    try:
        _bridge.reloadSettingsScreen(plugin_id)
    except Exception:
        pass


def remove_setting(plugin_id, key):
    with _lock:
        if plugin_id in _settings_cache:
            _settings_cache[plugin_id].pop(key, None)
            _save_settings_to_file()


def invalidate(plugin_id=None):
    with _lock:
        if plugin_id is None:
            _settings_cache.clear()
            _synced.clear()
        else:
            _settings_cache.pop(plugin_id, None)
            _synced.pop(plugin_id, None)


def _save_settings_to_file():
    if _bridge is None:
        return
    with _lock:
        for plugin_id, values in list(_settings_cache.items()):
            snapshot = _snapshot(values)
            if snapshot is not None and snapshot == _synced.get(plugin_id):
                continue
            payload = {key: _json.dumps(value, ensure_ascii=False)
                       for key, value in values.items()}
            try:
                _bridge.replaceSettings(plugin_id, _json.dumps(payload, ensure_ascii=False))
            except Exception:
                continue
            _settings_cache[plugin_id] = values
            _synced[plugin_id] = snapshot
