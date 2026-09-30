"""Small shared helpers for the Elyx runtime."""

from __future__ import annotations

from typing import Any


class LazyDict(dict):
    """A dict with attribute-style lookup.

    Missing attributes behave like missing keys and raise KeyError (per the
    Elyx public-API spec), except dunder lookups which raise AttributeError so
    copy/pickle/inspect protocols keep working.
    """

    def __getattr__(self, name: str) -> Any:
        if name.startswith("__") and name.endswith("__"):
            raise AttributeError(name)
        try:
            return self[name]
        except KeyError:
            raise KeyError(name)

    def __setattr__(self, name: str, value: Any) -> None:
        self[name] = value

    def __delattr__(self, name: str) -> None:
        try:
            del self[name]
        except KeyError:
            raise KeyError(name)


def lazy_wrap(value: Any) -> Any:
    """Recursively wrap dicts into LazyDict (and lists' elements)."""
    if isinstance(value, LazyDict):
        return value
    if isinstance(value, dict):
        return LazyDict({key: lazy_wrap(item) for key, item in value.items()})
    if isinstance(value, (list, tuple)):
        return [lazy_wrap(item) for item in value]
    return value
