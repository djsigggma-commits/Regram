"""AlertDialog builder wrapper — part of the re:gram plugin SDK.

Wraps org.telegram.ui.ActionBar.AlertDialog.Builder. All UI operations are
executed on the Android UI thread; mutating builder calls can be chained.
Button/item/back listeners receive (builder, which); dismiss/cancel listeners
receive (builder). Every listener is optional: None means the stock Java
behaviour (dismiss the dialog, no callback).
"""

import os
import sys
import threading

# Make sibling top-level modules importable regardless of interpreter setup.
_SRC_DIR = os.path.dirname(os.path.dirname(os.path.abspath(__file__)))
if _SRC_DIR not in sys.path:
    sys.path.insert(0, _SRC_DIR)

from android_utils import safe_call


def _jclass(name: str):
    from java import jclass
    return jclass(name)


def _builder_class():
    """AlertDialog.Builder: в этом форке класс живёт в ActionBar, в соседних
    форках — в Components. Пробуем оба, чтобы SDK не ломался от переезда."""
    last_error = None
    for package in ("org.telegram.ui.ActionBar", "org.telegram.ui.Components"):
        try:
            return _jclass(package + ".AlertDialog$Builder")
        except Exception as e:
            last_error = e
    raise ImportError(
        f"AlertDialog$Builder is unavailable: {last_error}")


def _interface_class(name: str):
    """Тот же поиск, но для вложенных интерфейсов AlertDialog."""
    last_error = None
    for package in ("org.telegram.ui.ActionBar", "org.telegram.ui.Components"):
        try:
            return _jclass(f"{package}.AlertDialog${name}")
        except Exception as e:
            last_error = e
    raise ImportError(f"AlertDialog${name} is unavailable: {last_error}")


def _signed_int(value):
    """Цвет как знаковое int: 0xFFFF0000 в Java — это -65536, а не long."""
    value = int(value)
    return value - 0x100000000 if value > 0x7FFFFFFF else value


def _theme_key_value(theme_key):
    """Theme.key_* по значению или по имени ("dialogButtonTextColor")."""
    if isinstance(theme_key, str):
        name = theme_key if theme_key.startswith("key_") else "key_" + theme_key
        theme_key = getattr(_jclass("org.telegram.ui.ActionBar.Theme"), name)
    return _signed_int(theme_key)


def _is_ui_thread() -> bool:
    try:
        Looper = _jclass("android.os.Looper")
        Thread = _jclass("java.lang.Thread")
        return Looper.getMainLooper().getThread() == Thread.currentThread()
    except Exception:
        return False


def _run_sync(fn):
    """Run fn on the UI thread and wait for its result."""
    if _is_ui_thread():
        return fn()
    from android_utils import R

    done = threading.Event()
    box = {}

    def _wrap():
        try:
            box["result"] = fn()
        except Exception as e:  # propagated to the waiting thread
            box["error"] = e
        finally:
            done.set()

    try:
        from app.regram.plugins import PluginServices
        PluginServices.runOnUiThread(_wrap, 0)
    except Exception:
        _jclass("org.telegram.messenger.AndroidUtilities").runOnUIThread(R(_wrap))
    if not done.wait(10.0):
        raise TimeoutError("UI thread did not respond within 10 seconds")
    if "error" in box:
        raise box["error"]
    return box.get("result")


def _post(fn):
    """Run fn on the UI thread without waiting."""
    if _is_ui_thread():
        fn()
    else:
        from android_utils import run_on_ui_thread
        run_on_ui_thread(fn)


def _default_context():
    try:
        fragment = _jclass("org.telegram.ui.LaunchActivity").getLastFragment()
        if fragment is not None:
            context = fragment.getContext()
            if context is not None:
                return context
    except Exception:
        pass
    return _jclass("org.telegram.messenger.ApplicationLoader").applicationContext


class AlertDialogBuilder:
    """Fluent wrapper around AlertDialog.Builder (see the plugin docs)."""

    # Mirrors org.telegram.ui.ActionBar.AlertDialog constants — в этом форке
    # LOADING=2 и SPINNER=3 (в exteraGram были 1 и 2); сверяться надо с Java,
    # иначе LOADING-диалог молча строится как MESSAGE.
    ALERT_TYPE_MESSAGE = 0
    ALERT_TYPE_LOADING = 2
    ALERT_TYPE_SPINNER = 3

    # android.content.DialogInterface button ids.
    BUTTON_POSITIVE = -1
    BUTTON_NEGATIVE = -2
    BUTTON_NEUTRAL = -3

    def __init__(self, context=None, alert_type=ALERT_TYPE_MESSAGE,
                 resources_provider=None, *, progress_style=None):
        if progress_style is not None:
            if alert_type != self.ALERT_TYPE_MESSAGE and alert_type != progress_style:
                raise TypeError("Conflicting alert_type and progress_style")
            alert_type = progress_style
        if context is None:
            context = _default_context()
        self._alert_type = alert_type
        self._context = context
        self._dialog = None
        self._cancelable = None
        self._canceled_on_touch_outside = None
        self._progress = None
        self._red_buttons = []  # make_button_red() до show(), см. метод
        self._proxies = []  # keep dynamic proxies alive for the dialog lifetime

        Builder = _builder_class()
        if int(alert_type) == self.ALERT_TYPE_MESSAGE:
            if resources_provider is None:
                self._builder = _run_sync(lambda: Builder(context))
            else:
                self._builder = _run_sync(lambda: Builder(context, resources_provider))
        elif resources_provider is None:
            self._builder = _run_sync(lambda: Builder(context, int(alert_type)))
        else:
            self._builder = _run_sync(
                lambda: Builder(context, int(alert_type), resources_provider))

    @property
    def _java_builder(self):
        return self._builder

    # ---- content ----

    def set_title(self, text):
        _post(lambda: self._builder.setTitle(str(text)))
        return self

    def set_message(self, text):
        _post(lambda: self._builder.setMessage(str(text)))
        return self

    def set_view(self, view, height=-2):
        if height is None:
            _post(lambda: self._builder.setView(view))
        else:
            _post(lambda: self._builder.setView(view, int(height)))
        return self

    def set_message_text_view_clickable(self, clickable):
        """Сообщение становится кликабельным (ссылки, tg://-действия)."""
        def _apply():
            self._builder.setMessageTextViewClickable(bool(clickable))
        _post(_apply)
        return self

    def set_items(self, items, listener=None, icons=None):
        """items: list of strings; listener(builder, which); icons: optional res ids.

        Без *listener* строки просто закрывают диалог — Java проверяет
        onClickListener на null, поэтому null и есть «никакого колбэка».
        """
        from java import dynamic_proxy

        OnClickListener = _jclass("android.content.DialogInterface$OnClickListener")
        builder_self = self

        class _Listener(dynamic_proxy(OnClickListener)):
            def onClick(self, dialog, which):
                safe_call(listener, builder_self, which)

        proxy = _Listener() if listener is not None else None
        if proxy is not None:
            self._proxies.append(proxy)
        texts = [str(item) for item in items]

        def _apply():
            if icons is not None:
                self._builder.setItems(texts, [int(icon) for icon in icons], proxy)
            else:
                self._builder.setItems(texts, proxy)

        _post(_apply)
        return self

    # ---- buttons ----

    def _button_proxy(self, listener):
        """dynamic_proxy колбэка кнопки, либо null — «просто закрой»."""
        from java import dynamic_proxy

        if listener is None:
            return None
        OnButtonClickListener = _interface_class("OnButtonClickListener")
        builder_self = self

        class _Listener(dynamic_proxy(OnButtonClickListener)):
            def onClick(self, dialog, which):
                safe_call(listener, builder_self, which)

        proxy = _Listener()
        self._proxies.append(proxy)
        return proxy

    def _set_button(self, method_name, text, listener):
        # listener=None — штатный способ сказать «просто закрой кнопку»:
        # AlertDialog проверяет слушатель на null перед вызовом.
        proxy = self._button_proxy(listener)
        _post(lambda: getattr(self._builder, method_name)(str(text), proxy))
        return self

    def set_positive_button(self, text, listener=None):
        return self._set_button("setPositiveButton", text, listener)

    def set_negative_button(self, text, listener=None):
        return self._set_button("setNegativeButton", text, listener)

    def set_neutral_button(self, text, listener=None):
        return self._set_button("setNeutralButton", text, listener)

    def set_button(self, which, text, listener=None):
        """Одна кнопка по BUTTON_* — то же самое, что set_*_button, но с id."""
        proxy = self._button_proxy(listener)
        _post(lambda: self._builder.setButton(int(which), str(text), proxy))
        return self

    def make_button_red(self, which):
        """Покрасить кнопку в красный — работает и до show().

        На Java-стороне это Builder.makeRed(int): флаг применяется в
        Builder.show() тем же Theme.key_text_RedBold, что обещают доки. Если
        диалог уже создан (и Builder.show() нас не пройдёт), перекрашиваем
        сами — иначе красный виден был бы только после повторного show().
        """
        which = int(which)
        if which not in self._red_buttons:
            self._red_buttons.append(which)

        def _apply():
            if self._dialog is None:
                try:
                    self._builder.makeRed(which)
                except Exception:
                    pass  # нет makeRed — добьёт _recolor_red_buttons() после show()
            else:
                self._recolor_red_buttons()
        _post(_apply)
        return self

    def _recolor_red_buttons(self):
        if not self._red_buttons or self._dialog is None:
            return
        try:
            Theme = _jclass("org.telegram.ui.ActionBar.Theme")
            color = Theme.getColor(Theme.key_text_RedBold)
        except Exception:
            from java import jint
            color = jint(0xFFE53935, truncate=True)  # Material Red 600
        for which in self._red_buttons:
            try:
                button = self._dialog.getButton(which)
                if button is not None:
                    button.setTextColor(color)
            except Exception as e:
                try:
                    from android_utils import log
                    log(f"make_button_red failed: {e}")
                except Exception:
                    pass

    # ---- behavior / appearance ----

    def set_cancelable(self, cancelable):
        self._cancelable = bool(cancelable)
        if self._dialog is not None:
            _post(lambda: self._dialog.setCancelable(self._cancelable))
        return self

    def set_canceled_on_touch_outside(self, cancel):
        self._canceled_on_touch_outside = bool(cancel)
        if self._dialog is not None:
            _post(lambda: self._dialog.setCanceledOnTouchOutside(self._canceled_on_touch_outside))
        return self

    def set_progress(self, progress):
        """Set progress (0-100) on a LOADING/SPINNER dialog."""
        self._progress = int(progress)
        if self._dialog is not None:
            _post(lambda: self._dialog.setProgress(self._progress))
        return self

    def set_top_image(self, res_id, background_color):
        """Картинка сверху диалога: res id из R.raw/R.drawable + цвет подложки."""
        from java import jint

        def _apply():
            self._builder.setTopImage(int(res_id),
                                      jint(int(background_color), truncate=True))
        _post(_apply)
        return self

    def set_top_drawable(self, drawable, background_color):
        """То же, что set_top_image, но для готового Drawable.

        Отдельного setTopDrawable у AlertDialog нет — та же перегрузка
        setTopImage(Drawable, int).
        """
        from java import jint

        def _apply():
            try:
                self._builder.setTopDrawable(
                    drawable, jint(int(background_color), truncate=True))
            except Exception:
                self._builder.setTopImage(
                    drawable, jint(int(background_color), truncate=True))
        _post(_apply)
        return self

    def set_top_animation(self, res_id, size, auto_repeat, background_color,
                          layer_colors=None):
        """Lottie-анимация сверху; layer_colors — {имя слоя: цвет}."""
        from java import jint

        colors = None
        if layer_colors:
            colors = {str(name): _signed_int(color)
                      for name, color in dict(layer_colors).items()}

        def _apply():
            color = jint(int(background_color), truncate=True)
            if colors is None:
                self._builder.setTopAnimation(int(res_id), int(size),
                                              bool(auto_repeat), color)
            else:
                self._builder.setTopAnimation(int(res_id), int(size),
                                              bool(auto_repeat), color, colors)
        _post(_apply)
        return self

    def set_top_animation_is_new(self, is_new):
        _post(lambda: self._builder.setTopAnimationIsNew(bool(is_new)))
        return self

    def set_top_image(self, res_id, background_color):
        def _apply():
            from java import jint
            self._builder.setTopImage(int(res_id), jint(int(background_color), truncate=True))
        _post(_apply)
        return self

    def set_top_drawable(self, drawable, background_color):
        def _apply():
            from java import jint
            self._builder.setTopImage(drawable, jint(int(background_color), truncate=True))
        _post(_apply)
        return self

    def set_dialog_button_color_key(self, theme_key):
        _post(lambda: self._builder.setDialogButtonColorKey(int(theme_key)))
        return self

    def set_message_text_view_clickable(self, clickable):
        _post(lambda: self._builder.setMessageTextViewClickable(bool(clickable)))
        return self

    def set_blurred_background(self, blur, blur_behind_if_possible=True):
        _post(lambda: self._builder.setBlurredBackground(bool(blur)))
        return self

    def get_context(self):
        return self._context

    def set_dim_enabled(self, enabled):
        _post(lambda: self._builder.setDimEnabled(bool(enabled)))
        return self

    def set_dialog_button_color_key(self, theme_key):
        """Цвет подписей кнопок по ключу темы (Theme.key_* или его имя)."""
        def _apply():
            self._builder.setDialogButtonColorKey(_theme_key_value(theme_key))
        _post(_apply)
        return self

    def set_blurred_background(self, blur, blur_behind_if_possible=True):
        """Размытый фон. Второй флаг — для форков с двухаргументной перегрузкой;
        этот AlertDialog знает только про self.blur."""
        def _apply():
            try:
                self._builder.setBlurredBackground(bool(blur),
                                                   bool(blur_behind_if_possible))
            except Exception:
                self._builder.setBlurredBackground(bool(blur))
        _post(_apply)
        return self

    def set_on_dismiss_listener(self, listener=None):
        from java import dynamic_proxy

        if listener is None:
            _post(lambda: self._builder.setOnDismissListener(None))
            return self

        OnDismissListener = _jclass("android.content.DialogInterface$OnDismissListener")
        builder_self = self

        class _Listener(dynamic_proxy(OnDismissListener)):
            def onDismiss(self, dialog):
                safe_call(listener, builder_self)

        proxy = _Listener()
        self._proxies.append(proxy)
        _post(lambda: self._builder.setOnDismissListener(proxy))
        return self

    def set_on_cancel_listener(self, listener=None):
        from java import dynamic_proxy

        if listener is None:
            _post(lambda: self._builder.setOnCancelListener(None))
            return self

        OnCancelListener = _jclass("android.content.DialogInterface$OnCancelListener")
        builder_self = self

        class _Listener(dynamic_proxy(OnCancelListener)):
            def onCancel(self, dialog):
                safe_call(listener, builder_self)

        proxy = _Listener()
        self._proxies.append(proxy)
        _post(lambda: self._builder.setOnCancelListener(proxy))
        return self

    def set_on_back_button_listener(self, listener=None):
        """Back-кнопка: колбэк получает (builder, which). None — сброс слушателя."""
        proxy = self._button_proxy(listener)
        _post(lambda: self._builder.setOnBackButtonListener(proxy))
        return self

    # docs-имя без «button»: тот же слушатель, просто короче.
    set_on_back_listener = set_on_back_button_listener

    # ---- lifecycle ----

    def create(self):
        """Create the dialog (without showing it); returns self."""
        def _do():
            if self._dialog is None:
                self._dialog = self._builder.create()
                self._apply_pending_state()
        _run_sync(_do)
        return self

    def show(self):
        """Create (if needed) and show the dialog; returns self."""
        def _do():
            if self._dialog is None:
                self._dialog = self._builder.show()
                self._apply_pending_state()
            elif not self._dialog.isShowing():
                self._dialog.show()
                self._apply_pending_state()
        _run_sync(_do)
        return self

    def _apply_pending_state(self):
        """Флаги, выставленные до show(): Java их не видел, пока диалога не было."""
        if self._cancelable is not None:
            self._dialog.setCancelable(self._cancelable)
        if self._canceled_on_touch_outside is not None:
            self._dialog.setCanceledOnTouchOutside(self._canceled_on_touch_outside)
        if self._progress is not None:
            self._dialog.setProgress(self._progress)
        self._recolor_red_buttons()

    def dismiss(self):
        if self._dialog is not None:
            _post(self._dialog.dismiss)
        return self

    def get_dialog(self):
        """Живой AlertDialog: как в upstream, диалог создаётся по запросу."""
        if self._dialog is None:
            self.create()
        return self._dialog

    def get_button(self, which):
        """The dialog's button View for a BUTTON_* id, or None."""
        if self._dialog is None:
            self.create()
        try:
            return self._dialog.getButton(int(which))
        except Exception:
            return None
