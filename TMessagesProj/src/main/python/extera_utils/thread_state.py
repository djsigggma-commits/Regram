import ctypes
import sys
import threading

_ensure = None


def _resolve():
    libraries = []
    pythonapi = getattr(ctypes, "pythonapi", None)
    if pythonapi is not None:
        libraries.append(pythonapi)
    try:
        libraries.append(ctypes.PyDLL("libpython%d.%d.so" % sys.version_info[:2]))
    except OSError:
        pass
    for library in libraries:
        try:
            ensure = library.PyGILState_Ensure
        except AttributeError:
            continue
        ensure.restype = ctypes.c_int
        ensure.argtypes = []
        return ensure
    return False


def pin():
    global _ensure
    if not isinstance(threading.current_thread(), threading._DummyThread):
        return True
    if _ensure is None:
        _ensure = _resolve()
    if not _ensure:
        return False
    _ensure()
    return True
