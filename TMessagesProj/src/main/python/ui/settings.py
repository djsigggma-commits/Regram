"""Settings screen item declarations for plugins — part of the re:gram plugin SDK.

Pure Python dataclasses; serialization into the JSON schema consumed by the
Java renderer lives in extera_utils.plugin_loader.

Each item carries a ``type`` field with the docs name of the control: plugins
read ``item.type`` to post-process their own settings list (rename, filter,
translate). The JSON type the Java renderer sees is a separate, lowercase
vocabulary owned by plugin_loader ("switch", "edittext", ...) — ``type`` here
is a Python-side label only and does not drive rendering.
"""

from typing import Any, Callable, List, Optional

from dataclasses import dataclass
import math


@dataclass
class Header:
    text: str
    type: str = "Header"


@dataclass
class Divider:
    text: Optional[str] = None
    type: str = "Divider"


@dataclass
class Switch:
    key: str
    text: str
    default: bool
    subtext: Optional[str] = None
    icon: Optional[str] = None
    on_change: Optional[Callable] = None
    on_long_click: Optional[Callable] = None
    link_alias: Optional[str] = None
    type: str = "Switch"


@dataclass
class Selector:
    key: str
    text: str
    default: int
    items: List[str]
    subtext: Optional[str] = None
    icon: Optional[str] = None
    on_change: Optional[Callable] = None
    on_long_click: Optional[Callable] = None
    link_alias: Optional[str] = None
    type: str = "Selector"


@dataclass
class Input:
    key: str
    text: str
    default: Optional[str] = None
    subtext: Optional[str] = None
    icon: Optional[str] = None
    on_change: Optional[Callable] = None
    on_long_click: Optional[Callable] = None
    link_alias: Optional[str] = None
    type: str = "Input"


@dataclass
class Text:
    text: str
    subtext: Optional[str] = None
    icon: Optional[str] = None
    accent: bool = False
    red: bool = False
    on_click: Optional[Callable] = None
    on_long_click: Optional[Callable] = None
    create_sub_fragment: Optional[Callable[[], list]] = None
    link_alias: Optional[str] = None
    type: str = "Text"


@dataclass
class EditText:
    key: str
    hint: str
    default: str = ""
    multiline: bool = False
    max_length: Optional[int] = None
    mask: Optional[str] = None
    on_change: Optional[Callable] = None
    type: str = "EditText"


@dataclass
class Custom:
    item: Optional[Any] = None
    view: Optional[Any] = None
    factory: Optional[Any] = None
    factory_args: Optional[tuple] = None
    on_click: Optional[Callable] = None
    on_long_click: Optional[Callable] = None
    create_sub_fragment: Optional[Callable[[], list]] = None
    link_alias: Optional[str] = None
    type: str = "Custom"


class _FactoryPeer:
    """Docs-ручка ``factory.instance``.

    В доках строку оформляют как ``Custom(factory=X.instance.java)``. В этом
    форке общий Java-PluginItemFactory не умеет держать одну строку, поэтому
    под именем ``java`` живёт сама Python-фабрика: именно её ждёт
    plugin_loader._build_custom_view и вызывает у неё ``build_view``.
    """

    __slots__ = ("java",)

    def __init__(self, factory):
        self.java = factory


def _selected_account():
    try:
        from java import jclass
        return int(jclass("org.telegram.messenger.UserConfig").selectedAccount)
    except Exception:
        return 0


def _log(message):
    try:
        from android_utils import log
        log(message)
    except Exception:
        pass


def _call_with_prefix(fn, args):
    import inspect
    try:
        signature = inspect.signature(fn)
    except (TypeError, ValueError):
        return fn(*args)
    for count in range(len(args), -1, -1):
        try:
            signature.bind(*args[:count])
        except TypeError:
            continue
        return fn(*args[:count])
    return fn(*args)


class SimpleSettingFactory:
    """Declarative factory for Custom settings items.

    ``create_view(context)`` and ``bind_view(view, item, divider)`` are called
    by the settings renderer when the row is drawn. ``java``/``instance`` give
    the shared Java peer (app.regram.plugins.models.PluginItemFactory),
    which delegates back to this object instead of generating a subclass.

    Колбэки ``on_click``/``on_long_click``/``create_sub_fragment``/``link_alias``
    уезжают в ``Custom``, который возвращает ``factory(...)``: их регистрирует
    plugin_loader и диалог получает их по обычному callback_id. ``attached_view``,
    ``equals`` и ``content_equals`` спрашивает с объекта общая Java-фабрика
    (``PluginItemFactory``). Одно остаётся недоступным: ``is_shadow`` —
    ``isShadow()`` спрашивает про всю фабрику целиком, а не про строку.
    """

    def __init__(self, create_view=None, bind_view=None, is_clickable: bool = False,
                 is_shadow: bool = False, create_item=None, on_click=None,
                 on_long_click=None, attached_view=None, equals=None,
                 content_equals=None, create_sub_fragment=None,
                 link_alias: Optional[str] = None):
        self.create_view = create_view
        self.bind_view = bind_view
        self.is_clickable = is_clickable
        self.is_shadow = is_shadow
        self.create_item = create_item
        self.on_click = on_click
        self.on_long_click = on_long_click
        self._attached_view = attached_view
        self._equals = equals
        self._content_equals = content_equals
        self.create_sub_fragment = create_sub_fragment
        self.link_alias = link_alias

    def build_view(self, context, divider=False, factory_args=None):
        """Точка входа Java-фабрики: `models/PluginItemFactory.bindView`."""
        if not callable(self.create_view):
            return None
        view = _call_with_prefix(self.create_view,
                                 (context, None, _selected_account(), 0, None))
        if view is None:
            return None
        if callable(self.bind_view):
            _call_with_prefix(self.bind_view,
                              (view, self._create_item(factory_args), divider, None, None))
        return view

    def attached_view(self, view):
        if callable(self._attached_view):
            _call_with_prefix(self._attached_view, (view,))

    def equals(self, item_a, item_b):
        return self._compare(self._equals, item_a, item_b)

    def content_equals(self, item_a, item_b):
        return self._compare(self._content_equals, item_a, item_b)

    def _compare(self, fn, item_a, item_b):
        """None = «мнения нет», сравнивает Java. Общий factory-метод у всех
        строк плагина один, поэтому решение отдаётся конкретной строке."""
        if not callable(fn):
            return None
        try:
            return bool(_call_with_prefix(fn, (item_a, item_b)))
        except Exception as e:
            _log(f"SimpleSettingFactory compare failed: {type(e).__name__}: {e}")
            return None

    def _create_item(self, factory_args=None):
        """UItem для строки: свой create_item или None — пусть решает Java."""
        if not callable(self.create_item):
            return None
        try:
            return _call_with_prefix(self.create_item, (None, None, factory_args))
        except Exception as e:
            _log(f"SimpleSettingFactory.create_item failed: {type(e).__name__}: {e}")
            return None

    @classmethod
    def getInstance(cls):
        from java import jclass
        return jclass("app.regram.plugins.models.PluginItemFactory").getInstance()

    @property
    def instance(self):
        """Docs-ручка на фабрику: ``Custom(factory=X.instance.java)``."""
        return _FactoryPeer(self)

    @property
    def java(self):
        from java import jclass
        return jclass("app.regram.plugins.models.PluginItemFactory").getInstance()

    def to_item(self, *factory_args):
        from java import jclass
        return jclass("app.regram.plugins.models.PluginItemFactory").create(
            self, factory_args or None)

    def __call__(self, *factory_args, link_alias: Optional[str] = None,
                 **callbacks) -> Custom:
        """Factory(link_alias="x") or Factory(*factory_args) -> Custom(...).

        Колбэки строки берутся из фабрики, а вызов может переопределить их
        именованными аргументами — одна фабрика, разные действия на строках.
        """
        return Custom(factory=self,
                      factory_args=factory_args or None,
                      on_click=callbacks.get("on_click", self.on_click),
                      on_long_click=callbacks.get("on_long_click", self.on_long_click),
                      create_sub_fragment=callbacks.get(
                          "create_sub_fragment", self.create_sub_fragment),
                      link_alias=link_alias if link_alias is not None else self.link_alias)


PluginItemFactory = SimpleSettingFactory


@dataclass
class Slider:
    key: str
    text: str
    default: float = 0
    min: float = 0
    max: float = 100
    step: float = 1
    subtext: Optional[str] = None
    icon: Optional[str] = None
    on_change: Optional[Callable] = None
    on_long_click: Optional[Callable] = None
    link_alias: Optional[str] = None
    type: str = "Slider"

    def __post_init__(self):
        if not all(math.isfinite(float(value)) for value in (self.default, self.min, self.max, self.step)):
            raise ValueError("Slider values must be finite")
        if self.max <= self.min or self.step <= 0 or math.ceil((self.max - self.min) / self.step) > 2147483647:
            raise ValueError("Invalid slider range or step")

    def normalize(self, value):
        try:
            value = float(value)
            if not math.isfinite(value):
                value = self.default
        except (TypeError, ValueError):
            value = self.default
        value = min(self.max, max(self.min, value))
        value = min(self.max, self.min + math.floor((value - self.min) / self.step + 0.5) * self.step)
        return int(value) if all(float(v).is_integer() for v in (self.min, self.max, self.step)) else value
