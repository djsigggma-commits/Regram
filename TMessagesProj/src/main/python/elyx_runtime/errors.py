"""Exception hierarchy for the Elyx structured-plugin runtime.

All errors raised by elyx_runtime derive from ElyxError so the host loader can
present a single, clear failure category to the user.
"""


class ElyxError(Exception):
    """Base class for every error raised by the Elyx runtime."""


class ElyxArchiveError(ElyxError):
    """The .elyx/.eaf archive is malformed (not a ZIP, unsafe paths, ...)."""


class RefmapNotFoundError(ElyxArchiveError):
    """No refmap.yaml / refmap.yml / refmap.json at the archive root."""


class RefmapError(ElyxArchiveError):
    """The refmap file exists but cannot be parsed into a path mapping."""


class MetainfoNotFoundError(ElyxArchiveError):
    """No metainfo file found (refmap pointer and root discovery both failed)."""


class MetainfoError(ElyxError):
    """The metainfo file exists but its content is invalid."""


class MainModuleNotFoundError(ElyxArchiveError):
    """The entry module (refmap `main` or root main.py) does not exist."""


class PluginClassNotFoundError(ElyxError):
    """The entry module defines no concrete BasePlugin subclass."""
