"""alert_dialog_builder — docs-путь импорта AlertDialogBuilder.

Доки описывают диалоги как ``from alert_dialog_builder import
AlertDialogBuilder``, а реализация живёт в ``ui/alert``. Это тонкий ре-экспорт
того же класса (никакой второй копии логики), плюс вынесенные в модуль
константы ALERT_TYPE_* и BUTTON_* — плагины зовут их по имени модуля:
``alert_dialog_builder.ALERT_TYPE_SPINNER``.
"""

import os
import sys

# Make sibling top-level modules importable regardless of interpreter setup.
_SRC_DIR = os.path.dirname(os.path.abspath(__file__))
if _SRC_DIR not in sys.path:
    sys.path.insert(0, _SRC_DIR)

from ui.alert import AlertDialogBuilder  # noqa: E402

ALERT_TYPE_MESSAGE = AlertDialogBuilder.ALERT_TYPE_MESSAGE
ALERT_TYPE_LOADING = AlertDialogBuilder.ALERT_TYPE_LOADING
ALERT_TYPE_SPINNER = AlertDialogBuilder.ALERT_TYPE_SPINNER

BUTTON_POSITIVE = AlertDialogBuilder.BUTTON_POSITIVE
BUTTON_NEGATIVE = AlertDialogBuilder.BUTTON_NEGATIVE
BUTTON_NEUTRAL = AlertDialogBuilder.BUTTON_NEUTRAL

__all__ = [
    "AlertDialogBuilder",
    "ALERT_TYPE_MESSAGE",
    "ALERT_TYPE_LOADING",
    "ALERT_TYPE_SPINNER",
    "BUTTON_POSITIVE",
    "BUTTON_NEGATIVE",
    "BUTTON_NEUTRAL",
]
