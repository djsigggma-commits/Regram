import gc
import json
import os
import threading
import time

active = False
clock = time.perf_counter_ns

_hooks = {}
_gc = {}
_gc_started = {}
_main_tid = os.getpid()


def record(stat_id, elapsed):
    entry = _hooks.get(stat_id)
    if entry is None:
        _hooks[stat_id] = [1, elapsed, elapsed]
        return
    entry[0] += 1
    entry[1] += elapsed
    if elapsed > entry[2]:
        entry[2] = elapsed


def _on_gc(phase, info):
    tid = threading.get_native_id()
    if phase == "start":
        _gc_started[tid] = clock()
        return
    started = _gc_started.pop(tid, None)
    if started is None:
        return
    elapsed = clock() - started
    key = (info.get("generation", -1), tid == _main_tid)
    entry = _gc.get(key)
    if entry is None:
        _gc[key] = [1, elapsed, elapsed]
        return
    entry[0] += 1
    entry[1] += elapsed
    if elapsed > entry[2]:
        entry[2] = elapsed


def start():
    global active
    _hooks.clear()
    _gc.clear()
    _gc_started.clear()
    if _on_gc not in gc.callbacks:
        gc.callbacks.append(_on_gc)
    active = True


def stop():
    global active
    active = False
    try:
        gc.callbacks.remove(_on_gc)
    except ValueError:
        pass


def snapshot():
    hooks = {str(key): list(value) for key, value in list(_hooks.items())}
    collections = [[generation, main, *value] for (generation, main), value in list(_gc.items())]
    return json.dumps({"hooks": hooks, "gc": collections})
