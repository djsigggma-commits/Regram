"""Bulletin (in-app notification) helpers — part of the re:gram plugin SDK.

Everything is posted onto the UI thread automatically. If BulletinFactory
cannot serve a request, the helper degrades to an Android Toast.
Contextual bulletins ("link copied", "saved to gallery") take their text from
Telegram's own localization (LocaleController / BulletinFactory.FileType), so
they follow the language of the app.
"""

import os
import sys

# Make sibling top-level modules importable regardless of interpreter setup.
_SRC_DIR = os.path.dirname(os.path.dirname(os.path.abspath(__file__)))
if _SRC_DIR not in sys.path:
    sys.path.insert(0, _SRC_DIR)

DURATION_SHORT = 1500
DURATION_LONG = 2750
DURATION_PROLONG = 5000


def _jclass(name: str):
    from java import jclass
    return jclass(name)


def _raw_icon(name: str):
    """org.telegram.messenger.R.raw.<name> (Lottie res id), 0 when missing."""
    try:
        return getattr(_jclass("org.telegram.messenger.R$raw"), name)
    except Exception:
        return 0


def _factory(fragment=None):
    """BulletinFactory for *fragment*, the last visible fragment, or global()."""
    BulletinFactory = _jclass("org.telegram.ui.Components.BulletinFactory")
    if fragment is None:
        try:
            fragment = _jclass("org.telegram.ui.LaunchActivity").getLastFragment()
        except Exception:
            fragment = None
    if fragment is not None:
        try:
            return BulletinFactory.of(fragment)
        except Exception:
            pass
    # "global" is a Python reserved word, so it cannot be used as an attribute.
    return getattr(BulletinFactory, "global")()


def _toast(text):
    """Last-resort fallback when BulletinFactory is unavailable."""
    try:
        Toast = _jclass("android.widget.Toast")
        context = _jclass("org.telegram.messenger.ApplicationLoader").applicationContext
        Toast.makeText(context, str(text), Toast.LENGTH_SHORT).show()
    except Exception:
        pass


def _runnable(fn):
    try:
        from app.regram.plugins import PluginServices
        return PluginServices.runnable(fn)
    except Exception:
        from android_utils import R
        return R(fn)


def _string(key, fallback=""):
    """Текст из локализации Telegram: LocaleController знает ключи R.string."""
    try:
        return _jclass("org.telegram.messenger.LocaleController").getString(str(key))
    except Exception:
        return fallback


def _file_type(name, fallback="UNKNOWN"):
    """BulletinFactory.FileType по имени enum'а; текст плашки берёт сам Java."""
    FileType = _jclass("org.telegram.ui.Components.BulletinFactory$FileType")
    try:
        return FileType.valueOf(str(name))
    except Exception:
        return FileType.valueOf(str(fallback))


def _looks_like_fragment(value):
    """Позиционная совместимость: в старых сборках fragment шёл первым."""
    return value is not None and not isinstance(value, (bool, int, float, str))


def _safe(fn):
    """Wrap a user callback so exceptions never escape into Java."""
    def _wrapped(*args):
        try:
            return fn(*args)
        except Exception as e:
            try:
                from android_utils import log
                log(f"bulletin callback failed: {type(e).__name__}: {e}")
            except Exception:
                pass
    return _wrapped


def _show(make_bulletin, fallback_text):
    """Post bulletin creation+show onto the UI thread; Toast on failure."""
    from android_utils import run_on_ui_thread

    def _do():
        try:
            bulletin = make_bulletin()
            if bulletin is not None:
                bulletin.show()
                return
        except Exception:
            pass
        _toast(fallback_text)

    try:
        run_on_ui_thread(_do)
    except Exception:
        _toast(fallback_text)


def _with_duration(bulletin, duration):
    if bulletin is None or duration is None:
        return bulletin
    return bulletin.setDuration(int(duration))


class BulletinHelper:
    """Static bulletin helpers; all calls are marshalled to the UI thread."""

    DURATION_SHORT = DURATION_SHORT
    DURATION_LONG = DURATION_LONG
    DURATION_PROLONG = DURATION_PROLONG

    @staticmethod
    def show_info(message, fragment=None, duration=None):
        def make():
            bulletin = _factory(fragment).createSimpleBulletin(
                _raw_icon("info"), str(message))
            return _with_duration(bulletin, duration)
        _show(make, message)

    @staticmethod
    def show_error(message, fragment=None, duration=None):
        def make():
            bulletin = _factory(fragment).createErrorBulletin(str(message))
            return _with_duration(bulletin, duration)
        _show(make, message)

    @staticmethod
    def show_success(message, fragment=None, duration=None):
        def make():
            bulletin = _factory(fragment).createSuccessBulletin(str(message))
            return _with_duration(bulletin, duration)
        _show(make, message)

    @staticmethod
    def builder(text="", fragment=None):
        """Цепочка настроек бюллетеня; строится один раз в ``show()``."""
        return BulletinBuilder(text, fragment)

    @staticmethod
    def show_simple(text, icon_res_id, fragment=None, duration=None):
        def make():
            bulletin = _factory(fragment).createSimpleBulletin(
                int(icon_res_id), str(text))
            return _with_duration(bulletin, duration)
        _show(make, text)

    @staticmethod
    def show_two_line(title, subtitle, icon_res_id, fragment=None, duration=None):
        def make():
            bulletin = _factory(fragment).createSimpleBulletin(
                int(icon_res_id), str(title), str(subtitle))
            return _with_duration(bulletin, duration)
        _show(make, title)

    @staticmethod
    def show_with_button(text, icon_res_id, button_text, on_click,
                         fragment=None, duration=DURATION_PROLONG):
        def make():
            runnable = _runnable(_safe(on_click)) if on_click is not None else _runnable(lambda: None)
            return _factory(fragment).createSimpleBulletin(
                int(icon_res_id), str(text), str(button_text), int(duration), runnable)
        _show(make, text)

    @staticmethod
    def show_undo(text, on_undo, on_action=None, subtitle=None, fragment=None):
        def make():
            undo_runnable = _runnable(_safe(on_undo)) if on_undo is not None else _runnable(lambda: None)
            action_runnable = _runnable(_safe(on_action)) if on_action is not None else _runnable(lambda: None)
            factory = _factory(fragment)
            if subtitle is not None:
                return factory.createUndoBulletin(
                    str(text), str(subtitle), undo_runnable, action_runnable)
            return factory.createUndoBulletin(str(text), undo_runnable, action_runnable)
        _show(make, text)

    # -- контекстные плашки: тексты берёт локализация, а не хардкод --

    @staticmethod
    def show_copied_to_clipboard(message=None, fragment=None):
        if _looks_like_fragment(message):  # старое show_copied_to_clipboard(fragment)
            message, fragment = None, message
        text = message if message is not None else _string("TextCopied", "Text copied to clipboard")

        def make():
            return _factory(fragment).createCopyBulletin(str(text))
        _show(make, text)

    @staticmethod
    def show_link_copied(is_private_link_info=False, fragment=None):
        if _looks_like_fragment(is_private_link_info):  # старое show_link_copied(fragment)
            is_private_link_info, fragment = False, is_private_link_info

        def make():
            return _factory(fragment).createCopyLinkBulletin(bool(is_private_link_info))
        _show(make, _string("LinkCopied", "Link copied to clipboard"))

    @staticmethod
    def show_file_saved_to_gallery(is_video=False, amount=1, fragment=None):
        # Старые вызовы: show_file_saved_to_gallery(fragment) / (fragment, amount).
        if _looks_like_fragment(is_video):
            is_video, fragment = False, is_video
        elif isinstance(is_video, int) and not isinstance(is_video, bool):
            is_video, amount = False, is_video

        def make():
            if int(amount) > 1:
                file_type = "VIDEOS" if is_video else "PHOTOS"
            else:
                file_type = "VIDEO" if is_video else "PHOTO"
            return _download_bulletin(_file_type(file_type), amount, fragment)
        _show(make, _string("PhotoSavedHint", "Saved to gallery"))

    @staticmethod
    def show_file_saved_to_downloads(file_type_enum_name="UNKNOWN", amount=1,
                                     fragment=None, file_type=None):
        """file_type_enum_name: имя enum'а BulletinFactory.FileType (например "GIF").

        ``file_type=`` — имя параметра из прежней сборки, работает как алиас.
        """
        name = file_type if file_type is not None else file_type_enum_name
        if _looks_like_fragment(name):  # старое show_file_saved_to_downloads(fragment)
            name, fragment = "UNKNOWN", name
        elif isinstance(name, int) and not isinstance(name, bool):
            name, amount = "UNKNOWN", name

        def make():
            return _download_bulletin(_file_type(name), amount, fragment)
        _show(make, _string("FileSavedHintLinked", "Saved to downloads"))


def _download_bulletin(file_type, amount, fragment):
    """createDownloadBulletin: подпись плашки строит сам Java по FileType."""
    factory = _factory(fragment)
    try:
        return factory.createDownloadBulletin(file_type, int(amount), None)
    except Exception:
        return factory.createDownloadBulletin(file_type)


class BulletinBuilder:
    """Chainable bulletin over org.telegram.ui.Components.BulletinFactory.

    Java собирает вьюху бюллетеня один раз вместе с фабричным методом, поэтому
    билдер копит параметры и строит бюллетень в ``show()``; после сборки
    доступны только те настройки, у которых есть свой сеттер у Bulletin.
    """

    STYLES = ("simple", "info", "success", "error")

    def __init__(self, text="", fragment=None):
        self._text = str(text)
        self._subtitle = None
        self._icon = _raw_icon("info")
        self._style = "simple"
        self._duration = None
        self._button = None  # (text, callback) — Java держит ровно одну кнопку
        self._on_click = None
        self._on_hide = None
        self._keep_on_background = False
        self._remove_on_touch = False
        self._fragment = fragment
        self._bulletin = None
        self._click = None  # держим proxy слушателя, пока жив билдер

    # -- content --

    def set_text(self, text):
        self._text = str(text)
        return self

    def set_subtitle(self, subtitle):
        self._subtitle = None if subtitle is None else str(subtitle)
        return self

    def set_icon(self, icon):
        """Lottie-иконка: res id из R.raw либо его имя ("info", "copy")."""
        self._icon = int(icon) if isinstance(icon, int) and not isinstance(icon, bool) \
            else _raw_icon(str(icon))
        return self

    def set_style(self, style):
        """'simple' | 'info' | 'success' | 'error' — см. BulletinFactory.

        С включённой кнопкой стиль не применяется: у кнопочного конструктора
        нет error/success-вариантов, там роль стиля играет set_icon.
        """
        if str(style) not in self.STYLES:
            raise ValueError(f"unknown bulletin style {style!r}")
        self._style = str(style)
        return self

    def set_duration(self, duration):
        self._duration = int(duration)
        return self

    def add_button(self, text, on_click=None):
        """Кнопка действия. Повторный вызов заменяет предыдущую: у строки
        бюллетеня (Bulletin.ButtonLayout) ровно одна кнопка."""
        self._button = (str(text), on_click)
        return self

    def set_action(self, on_click):
        """Действие по тапу по всей плашке."""
        self._on_click = on_click
        return self

    def set_listener(self, on_hide):
        """Колбэк скрытия плашки."""
        self._on_hide = on_hide
        return self

    def set_keep_on_background(self, keep=True):
        """Не прятать плашку, когда поверх открывается bottom sheet."""
        self._keep_on_background = bool(keep)
        return self

    def set_remove_on_touch(self, remove=True):
        """Тап по плашке убирает её сразу."""
        self._remove_on_touch = bool(remove)
        return self

    # -- lifecycle --

    def _build(self):
        factory = _factory(self._fragment)
        icon, text = int(self._icon), str(self._text)
        if self._button is not None:
            # У createError/createSuccessBulletin кнопки нет: с кнопкой плашка
            # собирается simple-конструктором, а роль иконки задаёт set_icon.
            button_text, on_click = self._button
            runnable = _runnable(_safe(on_click))
            duration = int(self._duration) if self._duration is not None else DURATION_PROLONG
            if self._subtitle is not None:
                self._bulletin = factory.createSimpleBulletin(
                    icon, text, str(self._subtitle), button_text, runnable)
            else:
                self._bulletin = factory.createSimpleBulletin(
                    icon, text, button_text, duration, runnable)
        elif self._style == "error":
            self._bulletin = factory.createErrorBulletin(text)
        elif self._style == "success":
            self._bulletin = factory.createSuccessBulletin(text)
        elif self._subtitle is not None:
            self._bulletin = factory.createSimpleBulletin(icon, text, str(self._subtitle))
        else:
            self._bulletin = factory.createSimpleBulletin(icon, text)

    def _apply(self):
        bulletin = self._bulletin
        if bulletin is None:
            return
        if self._duration is not None:
            bulletin.setDuration(int(self._duration))
        if self._on_hide is not None:
            bulletin.setOnHideListener(_runnable(_safe(self._on_hide)))
        if self._keep_on_background:
            bulletin.hideAfterBottomSheet(False)  # по умолчанию Java прячет плашку
        if self._on_click is not None or self._remove_on_touch:
            self._click = _click_proxy(self)
            bulletin.setOnClickListener(self._click)

    def show(self, top=False):
        from android_utils import run_on_ui_thread

        def _do():
            try:
                self._build()
                if self._bulletin is None:
                    raise RuntimeError("BulletinFactory returned no bulletin")
                self._apply()
                self._bulletin.show(bool(top))
            except Exception:
                _toast(self._text)

        try:
            run_on_ui_thread(_do)
        except Exception:
            _toast(self._text)
        return self

    def hide(self):
        bulletin = self._bulletin
        if bulletin is None:
            return self
        from android_utils import run_on_ui_thread
        try:
            run_on_ui_thread(bulletin.hide)
        except Exception:
            pass
        return self


def _call_with_prefix(fn, args):
    """Позволить колбэку принять не всё, что у него просят: (view) либо ()."""
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


def _click_proxy(builder):
    from java import dynamic_proxy

    OnClickListener = _jclass("android.view.View$OnClickListener")
    target = builder._bulletin
    action = builder._on_click

    class _Listener(dynamic_proxy(OnClickListener)):
        def onClick(self, view):
            if action is not None:
                _safe(_call_with_prefix)(action, (view,))
            if builder._remove_on_touch and target is not None:
                try:
                    target.hide()
                except Exception:
                    pass

    return _Listener()
