"""Telegram client helpers: queues, requests, controllers, sending — re:gram plugin SDK.

All Java interop is lazy; importing this module on a host interpreter is
safe, calling into it requires the Android runtime.

Multi-account (PLUGINS-API.md §4.1): every account-scoped helper accepts an
optional ``account`` keyword. Without it the helper works on the UI-selected
account (``UserConfig.selectedAccount``); called from inside a hook callback
without ``account=`` it logs a one-time warning, because the hook's account
is almost always the intended one — take it from ``get_hook_account()`` /
``self.client`` instead. The hook account itself propagates automatically
around hook callbacks, work posted via ``run_on_queue`` from them and
``send_request`` completion callbacks.
"""

import os
import sys
import threading
from contextlib import contextmanager

# Make sibling top-level modules importable regardless of interpreter setup.
_SRC_DIR = os.path.dirname(os.path.abspath(__file__))
if _SRC_DIR not in sys.path:
    sys.path.insert(0, _SRC_DIR)

# DispatchQueue names. Значения — имена полей org.telegram.messenger.Utilities:
# работа плагина должна идти в той же очереди клиента, что и всё остальное, а
# не в отдельном потоке с таким же названием (как в SDK exteraGram).
STAGE_QUEUE = "stageQueue"
GLOBAL_QUEUE = "globalQueue"
CACHE_CLEAR_QUEUE = "cacheClearQueue"
SEARCH_QUEUE = "searchQueue"
PHONE_BOOK_QUEUE = "phoneBookQueue"
THEME_QUEUE = "themeQueue"
EXTERNAL_NETWORK_QUEUE = "externalNetworkQueue"
PLUGINS_QUEUE = "pluginsQueue"

_queues = {}


def _jclass(name: str):
    from java import jclass
    return jclass(name)


def _java_static(name: str, field: str):
    try:
        return getattr(_jclass(name), field)
    except Exception:
        return None


def _log(message):
    try:
        from android_utils import log
        log(message)
    except Exception:
        print(f"[regram:client_utils] {message}", file=sys.stderr)


def _require(perm: str, what: str, detail=None):
    """Проверить разрешение плагина.

    Импорт ленивый: plugin_loader импортирует client_utils первым, на уровне
    модуля это был бы цикл. Плагин определяется по стеку, поэтому проверка
    работает и в колбэках из Java, где plugin_context не выставлен.
    """
    from extera_utils.plugin_loader import require_permission
    require_permission(perm, what, detail=detail)


# Hook account scope

_hook_state = threading.local()
_MISSING = object()


def get_hook_account():
    """Account of the hook callback running on this thread, or None."""
    return getattr(_hook_state, "account", None)


def get_selected_account() -> int:
    """The account currently selected in the UI."""
    return int(_jclass("org.telegram.messenger.UserConfig").selectedAccount)


@contextmanager
def hook_scope(account):
    """Bind *account* as the hook account for the current thread.

    Used by the plugin loader around hook dispatch and by client_utils
    itself around queued work / request callbacks. Restores the previous
    value on exit; safe to nest.
    """
    previous = getattr(_hook_state, "account", _MISSING)
    _hook_state.account = account
    try:
        yield account
    finally:
        if previous is _MISSING:
            try:
                del _hook_state.account
            except AttributeError:
                pass
        else:
            _hook_state.account = previous


_warned_helpers = set()

_MAX_ACCOUNTS_FALLBACK = 10
_max_accounts = None


def _account_bounds():
    """UserConfig.MAX_ACCOUNT_COUNT — вне этого диапазона Java-массив
    Instance[] бьёт ArrayIndexOutOfBoundsException из недр плагина."""
    global _max_accounts
    if _max_accounts is None:
        try:
            _max_accounts = int(_java_static(
                "org.telegram.messenger.UserConfig", "MAX_ACCOUNT_COUNT"))
        except Exception:
            _max_accounts = _MAX_ACCOUNTS_FALLBACK
        if _max_accounts <= 0:
            _max_accounts = _MAX_ACCOUNTS_FALLBACK
    return _max_accounts


def _check_account(num: int) -> int:
    limit = _account_bounds()
    if not 0 <= num < limit:
        raise ValueError(
            f"account index {num} is out of range 0..{limit - 1} "
            "(UserConfig.MAX_ACCOUNT_COUNT)")
    return num


def _check_account_logged_in(num: int) -> int:
    """Незалогиненный аккаунт: AccountInstance.getInstance() построил бы все
    его контроллеры молча — плагин получил бы пустой клиент вместо ошибки.

    isValidAccount может быть недоступен (R8), тогда проверку пропускаем:
    отсутствие проверки лучше падения всего SDK.
    """
    try:
        logged_in = _jclass("org.telegram.messenger.UserConfig").isValidAccount(num)
    except Exception:
        return num
    if not bool(logged_in):
        raise RuntimeError(f"account {num} is not logged in")
    return num


def _resolve_account(account, helper_name: str = None) -> int:
    """explicit account -> UI-selected account (warn once per helper inside
    a hook scope: the scope account is usually the intended one there)."""
    if account is not None:
        return _check_account(int(account))
    hook_account = get_hook_account()
    if hook_account is not None and helper_name is not None \
            and helper_name not in _warned_helpers:
        _warned_helpers.add(helper_name)
        _log(f"client_utils.{helper_name}() called without account= inside a hook "
             f"callback (account {hook_account}); helpers default to the UI-selected "
             "account — pass account= explicitly (see PLUGINS-API.md §4.1)")
    return _check_account(get_selected_account())


def _resolve_scoped_account(account) -> int:
    """AccountClient resolution: explicit -> hook scope -> UI-selected."""
    if account is not None:
        return _check_account(int(account))
    hook_account = get_hook_account()
    if hook_account is not None:
        return _check_account(int(hook_account))
    return _check_account(get_selected_account())


# Dispatch queues

def get_queue_by_name(name: str):
    """Return the client DispatchQueue *name*, creating one only if absent.

    Named after the SDK constant, so ``get_queue_by_name(PLUGINS_QUEUE)`` gives
    the very thread the client itself posts to.
    """
    if not name:
        return None
    queue = _queues.get(name)
    if queue is None:
        # Штатная очередь клиента — статическое поле Utilities; своего потока
        # заводить нельзя: работа плагина уходит из очереди, с которой ядро
        # читает то же состояние.
        queue = _java_static("org.telegram.messenger.Utilities", name)
        if queue is None:
            queue = _jclass("org.telegram.messenger.DispatchQueue")(str(name))
        _queues[name] = queue
    return queue


def show_error_bulletin(message, fragment=None):
    """Ошибка плашкой. Есть в SDK exteraGram и зовётся плагинами из client_utils,
    хотя реализация живёт в ui.bulletin, — без этого имени они не грузятся."""
    from ui.bulletin import BulletinHelper
    BulletinHelper.show_error(message, fragment)


def show_info_bulletin(message, fragment=None):
    """Сообщение плашкой; парная к show_error_bulletin."""
    from ui.bulletin import BulletinHelper
    BulletinHelper.show_info(message, fragment)


def run_on_queue(fn, queue: str = None, delay: int = 0, delay_ms: int = None,
                 queue_name: str = None):
    """Post *fn* to a named DispatchQueue, optionally after *delay* ms.

    The queue may be passed as ``queue=`` (ours) or ``queue_name=`` (the SDK
    name plugins use); without either, ``PLUGINS_QUEUE``.

    The current hook account (if any) is captured now and restored around
    the execution of *fn* (PLUGINS-API.md §4.1).
    """
    from android_utils import R

    if delay_ms is not None:
        delay = delay_ms
    account = get_hook_account()
    # Владельца берём в момент постановки в очередь: исполняться _run будет на
    # чужом потоке, где кадра плагина на стеке уже нет, и Java-гейт без метки
    # пропустил бы обращения плагина к сети и рефлексии.
    from extera_utils.plugin_loader import java_runtime_mark, plugin_frame_owner
    owner = plugin_frame_owner()

    def _run():
        with java_runtime_mark(owner):
            if account is None:
                fn()
            else:
                with hook_scope(account):
                    fn()

    dispatch_queue = get_queue_by_name(queue or queue_name or PLUGINS_QUEUE)
    services = _plugin_services()
    if services is not None:
        try:
            services.postRunnable(dispatch_queue, _run, int(delay or 0))
            return dispatch_queue
        except Exception as exc:
            _log(f"run_on_queue: java runnable unavailable ({exc}), falling back to proxy")
    runnable = R(_run)
    if delay and int(delay) > 0:
        dispatch_queue.postRunnable(runnable, int(delay))
    else:
        dispatch_queue.postRunnable(runnable)
    return dispatch_queue


# TL requests

def RequestCallback(fn, account=None):
    """Wrap ``fn(response, error)`` as a Java ``RequestDelegate``.

    Plugins use this in two ways, both supported:

    * handed to :func:`send_request` — which also accepts a bare callable, so
      the wrapper is optional there;
    * handed straight to ``get_connections_manager().sendRequest(req, cb, flags)``,
      where a bare Python callable would not satisfy the Java signature.

    The callback runs inside the hook scope of *account* (the UI-selected
    account when not given), so account-scoped helpers called from within it
    target the account the request was sent on.
    """
    from java import dynamic_proxy

    RequestDelegate = _jclass("org.telegram.tgnet.RequestDelegate")
    resolved = _resolve_account(account, "RequestCallback")

    class _RequestDelegate(dynamic_proxy(RequestDelegate)):
        def run(self, response, error):
            # Колбэк уходит в Java: ошибка плагина не должна ронять приложение.
            from android_utils import safe_call

            with hook_scope(resolved):
                safe_call(fn, response, error)

    proxy = _RequestDelegate()
    # Marks an already-wrapped callback so send_request does not double-wrap.
    try:
        proxy.__dict__["_regram_request_delegate"] = True
    except Exception:
        pass
    return proxy


def _is_request_delegate(fn) -> bool:
    """True when *fn* is already a Java RequestDelegate rather than a callable."""
    if getattr(fn, "_regram_request_delegate", False):
        return True
    # A dynamic_proxy instance exposes run() but is not callable itself;
    # every plain Python callback is callable. That is the discriminator.
    return not callable(fn) and hasattr(fn, "run")


def send_request(request, fn, account=None) -> int:
    """Send a TL request; fn(response, error) is called on completion.

    *fn* may be a plain callable or an already-built :func:`RequestCallback`
    delegate — plugins in the wild pass both.

    Defaults to the UI-selected account; the completion callback runs with
    the scope of the account the request was sent on. Returns the
    ConnectionsManager request id.
    """
    # TL-запрос из кода плагина — это сетевой запрос из кода плагина, то есть
    # ровно то, что закрывает "network" (PLUGINS-SECURITY.md, набор разрешений).
    _require("network", "send_request")
    resolved = _resolve_account(account, "send_request")
    if _is_request_delegate(fn):
        return int(get_connections_manager(resolved).sendRequest(request, fn))
    services = _plugin_services()
    if services is not None:
        def _run(response, error):
            from android_utils import safe_call
            with hook_scope(resolved):
                safe_call(fn, response, error)
        try:
            return int(services.sendRequest(resolved, request, _run))
        except Exception as exc:
            _log(f"send_request: java delegate unavailable ({exc}), falling back to proxy")
    return int(get_connections_manager(resolved).sendRequest(
        request, RequestCallback(fn, account=resolved)))


def _plugin_services():
    try:
        from app.regram.plugins import PluginServices
        return PluginServices
    except Exception:
        return None


# Core controller accessors

def get_account_instance(account=None):
    """AccountInstance for *account* (default: UI-selected / hook scope rules)."""
    num = _check_account_logged_in(
        _resolve_account(account, "get_account_instance"))
    return _jclass("org.telegram.messenger.AccountInstance").getInstance(num)


def get_current_account() -> int:
    """The currently selected account index (alias of get_selected_account)."""
    return get_selected_account()


def get_user_id(account=None) -> int:
    """The logged-in user's id (clientUserId) for the given/current account."""
    return int(get_account_instance(
        _resolve_account(account, "get_user_id")).getUserConfig().getClientUserId())


def get_context():
    """Best-effort Context: the visible activity if any, else the application one."""
    try:
        fragment = get_last_fragment()
        if fragment is not None and fragment.getParentActivity() is not None:
            return fragment.getParentActivity()
    except Exception:
        pass
    return _java_static("org.telegram.messenger.ApplicationLoader",
                        "applicationContext")


def get_file_ref_controller(account=None):
    return get_account_instance(_resolve_account(account, "get_file_ref_controller")) \
        .getFileRefController()


def get_stats_controller(account=None):
    return get_account_instance(_resolve_account(account, "get_stats_controller")) \
        .getStatsController()


def get_last_fragment():
    """The currently visible BaseFragment, or None when unavailable."""
    try:
        launch = _jclass("org.telegram.ui.LaunchActivity")
    except Exception:
        return None
    try:
        return launch.getLastFragmentIncludeMainTabs()
    except Exception:
        pass
    try:
        return launch.getLastFragment()
    except Exception:
        return None


def get_messages_controller(account=None):
    return get_account_instance(_resolve_account(account, "get_messages_controller")) \
        .getMessagesController()


def get_contacts_controller(account=None):
    return get_account_instance(_resolve_account(account, "get_contacts_controller")) \
        .getContactsController()


def get_media_data_controller(account=None):
    return get_account_instance(_resolve_account(account, "get_media_data_controller")) \
        .getMediaDataController()


def get_connections_manager(account=None):
    return get_account_instance(_resolve_account(account, "get_connections_manager")) \
        .getConnectionsManager()


def get_location_controller(account=None):
    return get_account_instance(_resolve_account(account, "get_location_controller")) \
        .getLocationController()


def get_notifications_controller(account=None):
    return get_account_instance(_resolve_account(account, "get_notifications_controller")) \
        .getNotificationsController()


def get_messages_storage(account=None):
    return get_account_instance(_resolve_account(account, "get_messages_storage")) \
        .getMessagesStorage()


def get_send_messages_helper(account=None):
    return get_account_instance(_resolve_account(account, "get_send_messages_helper")) \
        .getSendMessagesHelper()


def get_file_loader(account=None):
    return get_account_instance(_resolve_account(account, "get_file_loader")).getFileLoader()


def get_secret_chat_helper(account=None):
    return get_account_instance(_resolve_account(account, "get_secret_chat_helper")) \
        .getSecretChatHelper()


def get_download_controller(account=None):
    return get_account_instance(_resolve_account(account, "get_download_controller")) \
        .getDownloadController()


def get_notifications_settings(account=None):
    return get_account_instance(_resolve_account(account, "get_notifications_settings")) \
        .getNotificationsSettings()


def get_notification_center(account=None):
    return get_account_instance(_resolve_account(account, "get_notification_center")) \
        .getNotificationCenter()


def get_media_controller(account=None):
    """Global singleton — *account* is accepted and ignored, MediaController
    is not per-account (как в SDK exteraGram)."""
    return _jclass("org.telegram.messenger.MediaController").getInstance()


def get_user_config(account=None):
    # Напрямую, без AccountInstance: проверить залогиненность аккаунта плагин
    # должен уметь и по незалогиненному индексу (см. _check_account_logged_in).
    return _jclass("org.telegram.messenger.UserConfig").getInstance(
        _resolve_account(account, "get_user_config"))


# Sending messages

def _to_array_list(items):
    array_list = _jclass("java.util.ArrayList")()
    for item in items:
        array_list.add(item)
    return array_list


def _to_hash_map(mapping):
    hash_map = _jclass("java.util.HashMap")()
    for key, value in mapping.items():
        if key is None or value is None:
            continue
        hash_map.put(str(key), str(value))
    return hash_map


def _apply_parse_mode(params, field: str, text, parse_mode):
    """Replace params.message/params.caption with parsed text + entities."""
    if not parse_mode or text is None:
        return
    from extera_utils.text_formatting import parse_text

    parsed = parse_text(str(text), parse_mode, is_caption=(field == "caption"))
    plain = parsed.get(field, str(text))
    setattr(params, field, plain)
    entities = parsed.get("entities") or []
    if entities:
        params.entities = _to_array_list(entities)


def _parse_caption(caption, parse_mode):
    """-> (caption_str | None, entities ArrayList | None) for prepareSending*."""
    if caption is None:
        return None, None
    if not parse_mode:
        return str(caption), None
    from extera_utils.text_formatting import parse_text

    parsed = parse_text(str(caption), parse_mode, is_caption=True)
    plain = parsed.get("caption", str(caption))
    entities = parsed.get("entities") or []
    return plain, (_to_array_list(entities) if entities else None)


def _new_text_params(peer_id, text, parse_mode=None):
    SendMessageParams = _jclass(
        "org.telegram.messenger.SendMessagesHelper$SendMessageParams")
    params = SendMessageParams.of(str(text), int(peer_id))
    _apply_parse_mode(params, "message", text, parse_mode)
    return params


def _resolve_message_ref(value, account, helper: str, field: str):
    """MessageObject проходит как есть; числовой id — id сообщения в БД."""
    if value is None or hasattr(value, "getId") or hasattr(value, "messageOwner"):
        return value
    try:
        mid = int(value)
    except (TypeError, ValueError):
        _log(f"{helper}: {field}={value!r} is neither a MessageObject nor a "
             "message id — ignored")
        return None
    try:
        found = get_messages_controller(account).dialogMessagesByIds.get(mid)
    except Exception as exc:
        _log(f"{helper}: cannot resolve {field}={mid}: {exc}")
        return None
    if found is None:
        _log(f"{helper}: message {mid} is not loaded — {field} ignored")
    return found


def _opt(opts: dict, name: str, default=None):
    """Опция из kwargs; прочитанное имя удаляется, чтобы потом одним
    сообщением назвать неизвестные (раньше они падали с TypeError)."""
    return opts.pop(name, default)


def _warn_unknown_opts(helper: str, opts: dict):
    if opts:
        _log(f"{helper}: unsupported arguments for this sender — ignored: "
             + ", ".join(sorted(opts)))


def _send_kwargs(data: dict, parse_mode, account, helper: str):
    """Нормализация аргументов отправки: алиасы имён и резолв числовых id.

    replyToMsg/replyToMsgId — поле MessageObject, поэтому int резолвится через
    MessagesController; topMsgId вместе с replyToTopMsg=True — это то же поле
    replyToTopMsg (в Java это объект, а не флаг).
    """
    opts = data
    parse_mode = parse_mode if parse_mode is not None else _opt(opts, "formatting")
    _opt(opts, "formatting")
    _opt(opts, "parse_mode")  # уже в явном параметре вызова
    if opts.get("replyToMsg") is None and opts.get("replyToMsgId") is not None:
        opts["replyToMsg"] = opts.pop("replyToMsgId")
    else:
        opts.pop("replyToMsgId", None)
    top = opts.pop("topMsgId", None)
    if opts.get("replyToTopMsg") is True:
        opts["replyToTopMsg"] = top
    elif top is not None and opts.get("replyToTopMsg") is None:
        opts["replyToTopMsg"] = top
    for field in ("replyToMsg", "replyToTopMsg"):
        value = opts.get(field)
        if value is not None and not isinstance(value, bool):
            opts[field] = _resolve_message_ref(value, account, helper, field)
        elif isinstance(value, bool):
            opts.pop(field)
    return parse_mode, opts


def _apply_send_opts(send_params, opts: dict, helper: str):
    """Остаток kwargs -> поля SendMessageParams: что у объекта есть, то сядет."""
    grouped = _opt(opts, "groupedId")
    if grouped is not None:
        # Album: Java читает группу из params["groupId"] (SendMessagesHelper).
        merged = dict(opts.pop("params", None) or {})
        merged["groupId"] = str(grouped)
        opts["params"] = merged
    for key, value in opts.items():
        if value is None:
            continue
        # Списковые поля SendMessageParams — java.util.ArrayList. Питоновский
        # список Chaquopy в него не превращает, setattr падал, а ошибка уходила
        # в лог, и сообщение отправлялось без форматирования: плагин строит
        # entities сам и кладёт их обычным list'ом.
        if isinstance(value, (list, tuple)):
            value = _to_array_list(value)
        elif isinstance(value, dict):
            value = _to_hash_map(value)
        try:
            setattr(send_params, key, value)
        except Exception as exc:
            _log(f"{helper}: cannot set params key {key!r}: {exc}")


def send_text(peer_id, text, replyToMsg=None, parse_mode=None, account=None,
              **kwargs):
    """Send a text message to *peer_id*; returns the SendMessageParams sent.

    parse_mode is 'HTML' or 'Markdown' (alias: formatting=). Extra kwargs are
    fields of SendMessageParams (notify, scheduleDate, ttl, replyMarkup,
    groupedId, ...); replyToMsg accepts a MessageObject or a numeric message id.
    """
    _require("messages.send", "send_text")
    resolved = _resolve_account(account, "send_text")
    if replyToMsg is not None:
        kwargs.setdefault("replyToMsg", replyToMsg)
    parse_mode, kwargs = _send_kwargs(kwargs, parse_mode, resolved, "send_text")
    params = _new_text_params(peer_id, text, parse_mode)
    _apply_send_opts(params, kwargs, "send_text")
    _send_on_ui_thread(lambda: get_send_messages_helper(resolved).sendMessage(params))
    return params


def send_message(params: dict, parse_mode=None, account=None, **kwargs):
    """Send a message from a dict of SendMessageParams fields.

    Recognized keys: peer, message, caption, replyToMsg, replyToMsgId,
    replyToTopMsg, topMsgId, replyMarkup, notify, scheduleDate, groupedId,
    ttl, hasMediaSpoilers, sendingHighQuality, path, photo, document, params,
    searchLinks. Returns the SendMessageParams object.
    """
    _require("messages.send", "send_message")
    SendMessageParams = _jclass(
        "org.telegram.messenger.SendMessagesHelper$SendMessageParams")
    resolved = _resolve_account(account, "send_message")

    opts = dict(params or {})
    for key, value in kwargs.items():
        opts.setdefault(key, value)
    peer = opts.pop("peer", opts.pop("peer_id", 0))
    message = opts.pop("message", None)
    caption = opts.pop("caption", None)
    parse_mode, opts = _send_kwargs(opts, parse_mode, resolved, "send_message")

    base_text = message if message is not None else (caption or "")
    send_params = SendMessageParams.of(str(base_text), int(peer))
    if message is not None:
        _apply_parse_mode(send_params, "message", message, parse_mode)
    if caption is not None:
        send_params.caption = str(caption)
        _apply_parse_mode(send_params, "caption", caption, parse_mode)
    if message is None and any(opts.get(key) is not None for key in ("photo", "document")):
        send_params.message = None

    _apply_send_opts(send_params, opts, "send_message")

    _send_on_ui_thread(lambda: get_send_messages_helper(resolved).sendMessage(send_params))
    return send_params


def _on_ui_thread(fn):
    from android_utils import run_on_ui_thread
    run_on_ui_thread(fn)


def _is_ui_thread() -> bool:
    try:
        Looper = _jclass("android.os.Looper")
        Thread = _jclass("java.lang.Thread")
        return Looper.getMainLooper().getThread() == Thread.currentThread()
    except Exception:
        return False


def _send_on_ui_thread(fn):
    if _is_ui_thread():
        fn()
    else:
        _on_ui_thread(fn)


def send_photo(peer_id, path, caption=None, high_quality=False, parse_mode=None,
               replyToMsg=None, account=None, **kwargs):
    """Send a photo file to *peer_id* (high_quality=True sends it as a document).

    Uses SendMessagesHelper.prepareSendingPhoto (24-arg overload:
    accountInstance, imageFilePath, thumbFilePath, imageUri, dialogId,
    replyToMsg, replyToTopMsg, storyItem, quote, entities, stickers,
    inputContent, ttl, editingMessageObject, videoEditedInfo, notify,
    scheduleDate, mode, forceDocument, caption, quickReplyShortcut,
    quickReplyShortcutId, effectId, payStars).

    kwargs occupy the slots of that overload (replyToTopMsg, ttl, notify,
    scheduleDate, effectId); names without a slot are logged and dropped.
    """
    _require("messages.send", "send_photo")
    resolved = _resolve_account(account, "send_photo")
    if replyToMsg is not None:
        kwargs.setdefault("replyToMsg", replyToMsg)
    parse_mode, opts = _send_kwargs(kwargs, parse_mode, resolved, "send_photo")
    caption_str, entities = _parse_caption(caption, parse_mode)
    reply_to = opts.pop("replyToMsg", None)
    reply_to_top = opts.pop("replyToTopMsg", None)
    ttl = int(opts.pop("ttl", 0) or 0)
    notify = bool(opts.pop("notify", True))
    schedule_date = int(opts.pop("scheduleDate", 0) or 0)
    effect_id = int(opts.pop("effectId", 0) or 0)
    _warn_unknown_opts("send_photo", opts)

    def _send():
        _jclass("org.telegram.messenger.SendMessagesHelper").prepareSendingPhoto(
            get_account_instance(resolved), str(path), None, None, int(peer_id),
            reply_to, reply_to_top, None, None, entities, None, None, ttl,
            None, None, notify, schedule_date, 0, bool(high_quality),
            caption_str, None, 0, effect_id, 0)

    _on_ui_thread(_send)


def send_video(peer_id, path, caption=None, parse_mode=None,
               replyToMsg=None, account=None, **kwargs):
    """Send a video file to *peer_id*.

    Uses SendMessagesHelper.prepareSendingVideo (23-arg overload:
    accountInstance, videoPath, info, coverPath, coverPhoto, dialogId,
    replyToMsg, replyToTopMsg, storyItem, quote, entities, ttl,
    editingMessageObject, notify, scheduleDate, scheduleRepeatPeriod,
    forceDocument, hasMediaSpoilers, caption, quickReplyShortcut,
    quickReplyShortcutId, effectId, stars).
    """
    _require("messages.send", "send_video")
    resolved = _resolve_account(account, "send_video")
    if replyToMsg is not None:
        kwargs.setdefault("replyToMsg", replyToMsg)
    parse_mode, opts = _send_kwargs(kwargs, parse_mode, resolved, "send_video")
    caption_str, entities = _parse_caption(caption, parse_mode)
    reply_to = opts.pop("replyToMsg", None)
    reply_to_top = opts.pop("replyToTopMsg", None)
    ttl = int(opts.pop("ttl", 0) or 0)
    notify = bool(opts.pop("notify", True))
    schedule_date = int(opts.pop("scheduleDate", 0) or 0)
    repeat_period = int(opts.pop("scheduleRepeatPeriod", 0) or 0)
    spoilers = bool(opts.pop("hasMediaSpoilers", False))
    force_document = bool(opts.pop("forceDocument", False))
    effect_id = int(opts.pop("effectId", 0) or 0)
    _warn_unknown_opts("send_video", opts)

    def _send():
        _jclass("org.telegram.messenger.SendMessagesHelper").prepareSendingVideo(
            get_account_instance(resolved), str(path), None, None, None, int(peer_id),
            reply_to, reply_to_top, None, None, entities, ttl, None, notify,
            schedule_date, repeat_period, force_document, spoilers, caption_str,
            None, 0, effect_id, 0)

    _on_ui_thread(_send)


def _send_document_like(peer_id, path, caption, parse_mode, replyToMsg, resolved,
                        mime, helper_name, kwargs=None):
    # Одна проверка на send_document/send_audio: обе идут сюда.
    _require("messages.send", helper_name)
    opts = dict(kwargs or {})
    if replyToMsg is not None:
        opts.setdefault("replyToMsg", replyToMsg)
    parse_mode, opts = _send_kwargs(opts, parse_mode, resolved, helper_name)
    caption_str, entities = _parse_caption(caption, parse_mode)
    reply_to = opts.pop("replyToMsg", None)
    notify = bool(opts.pop("notify", True))
    schedule_date = int(opts.pop("scheduleDate", 0) or 0)
    reply_to_top = opts.pop("replyToTopMsg", None)
    invert_media = bool(opts.pop("invertMedia", False))
    _warn_unknown_opts(helper_name, opts)

    def _send():
        # prepareSendingDocument(accountInstance, path, originalPath, uri,
        #   caption, mime, dialogId, replyToMsg, replyToTopMsg, storyItem,
        #   quote, editingMessageObject, notify, scheduleDate, inputContent,
        #   quickReplyShortcut, quickReplyShortcutId, invertMedia)
        #
        # caption entities cannot ride along in this overload (the plural
        # prepareSendingDocuments variants carrying them are ambiguous from
        # Python), so parsed captions fall back to plain text here.
        _jclass("org.telegram.messenger.SendMessagesHelper").prepareSendingDocument(
            get_account_instance(resolved), str(path), str(path), None,
            caption_str, mime, int(peer_id), reply_to, reply_to_top, None, None,
            None, notify, schedule_date, None, None, 0, invert_media)

    if entities is not None:
        _log(f"{helper_name}: parse_mode entities are not supported for "
             "documents in this build — sending plain caption")
    _on_ui_thread(_send)


def send_document(peer_id, path, caption=None, parse_mode=None,
                  replyToMsg=None, account=None, **kwargs):
    """Send an arbitrary file as a document to *peer_id*."""
    _send_document_like(peer_id, path, caption, parse_mode, replyToMsg,
                        _resolve_account(account, "send_document"), None,
                        "send_document", kwargs)


def send_audio(peer_id, path, caption=None, parse_mode=None,
               replyToMsg=None, account=None, **kwargs):
    """Send an audio file to *peer_id*.

    There is no path-based audio sender in this tree
    (prepareSendingAudioDocuments takes existing MessageObjects), so audio
    goes through prepareSendingDocument with an audio/* mime type — the
    internal pipeline detects mp3/m4a/opus/ogg/flac and attaches audio
    attributes (duration/performer/title) itself.
    """
    import mimetypes

    mime, _ = mimetypes.guess_type(str(path))
    if mime is None or not mime.startswith("audio/"):
        mime = "audio/mpeg"
    _send_document_like(peer_id, path, caption, parse_mode, replyToMsg,
                        _resolve_account(account, "send_audio"), mime,
                        "send_audio", kwargs)


def _media_services():
    from app.regram.plugins import PluginMediaServices
    return PluginMediaServices


def _prepare_document(path):
    from file_utils import _require_files
    _require_files(path, "prepare document")
    return _media_services().prepareDocument(os.fspath(path))


class _LocalFileSystem:
    @classmethod
    def tempdir(cls):
        from file_utils import get_plugin_cache_dir
        path = get_plugin_cache_dir()
        if not path:
            raise RuntimeError("No active plugin cache directory")
        os.makedirs(path, exist_ok=True)
        return path

    @classmethod
    def write_temp_file(cls, filename, content, mode="wb", delete_after=0):
        import tempfile
        if mode not in ("w", "wb"):
            raise ValueError("Temporary files require w or wb mode")
        name = os.path.basename(os.fspath(filename))
        fd, path = tempfile.mkstemp(prefix="plugin-", suffix="-" + name, dir=cls.tempdir())
        try:
            with os.fdopen(fd, mode, encoding=None if "b" in mode else "utf-8") as handle:
                handle.write(content)
        except Exception:
            os.unlink(path)
            raise
        if delete_after > 0:
            def remove():
                try:
                    os.unlink(path)
                except FileNotFoundError:
                    pass
            timer = threading.Timer(delete_after, remove)
            timer.daemon = True
            timer.start()
        return path


def edit_message(message_obj, text=None, file_path=None, with_spoiler=False,
                 parse_mode=None, account=None):
    """Edit a message's text in place; returns the ConnectionsManager request id.

    Text edit uses the real SendMessagesHelper.editMessage(MessageObject,
    String, boolean searchLinks, BaseFragment, ArrayList<MessageEntity>,
    int scheduleDate, int scheduleRepeatPeriod). The fragment argument is
    the currently visible BaseFragment (get_last_fragment()).

    """
    _require("messages.send", "edit_message")
    resolved = _resolve_account(account, "edit_message")
    if file_path is not None:
        from file_utils import _require_files
        _require_files(file_path, "edit message media")
        path = os.fspath(file_path)
        if not os.path.isfile(path):
            raise FileNotFoundError(path)
        caption, entities = _parse_caption(text, parse_mode)
        _send_on_ui_thread(lambda: _media_services().editMedia(
            resolved, message_obj, path, caption, entities, bool(with_spoiler)))
        return None
    if text is None:
        raise ValueError("edit_message requires text= or file_path=")
    if with_spoiler:
        _log("edit_message: with_spoiler only applies to media edits — ignored")

    entities = None
    message = str(text)
    if parse_mode:
        from extera_utils.text_formatting import parse_text

        parsed = parse_text(message, parse_mode)
        message = parsed.get("message", message)
        raw_entities = parsed.get("entities") or []
        if raw_entities:
            entities = _to_array_list(raw_entities)

    fragment = get_last_fragment()
    if fragment is None:
        raise RuntimeError(
            "edit_message: no visible BaseFragment — the real editMessage API "
            "requires one and returns 0 without it")
    return int(get_send_messages_helper(resolved).editMessage(
        message_obj, message, True, fragment, entities, 0, 0))


# AccountClient (PLUGINS-API.md §4.1)

class AccountClient:
    """Per-account view of the client_utils helpers.

    ``AccountClient(None)`` follows the current hook scope (then the
    UI-selected account); ``AccountClient(3)`` is pinned to account 3.
    Calling an instance — ``client(account)`` — returns a pinned client for
    that account, which is what makes ``self.client(account)`` on BasePlugin
    work while ``self.client`` stays a property.
    """

    def __init__(self, account=None):
        self._account = None if account is None else int(account)

    def __call__(self, account=None) -> "AccountClient":
        return AccountClient(account)

    @property
    def account(self) -> int:
        """The resolved account this client currently targets."""
        return _resolve_scoped_account(self._account)

    # -- controllers --

    def get_account_instance(self):
        return get_account_instance(self.account)

    def get_messages_controller(self):
        return get_messages_controller(self.account)

    def get_contacts_controller(self):
        return get_contacts_controller(self.account)

    def get_media_data_controller(self):
        return get_media_data_controller(self.account)

    def get_connections_manager(self):
        return get_connections_manager(self.account)

    def get_location_controller(self):
        return get_location_controller(self.account)

    def get_notifications_controller(self):
        return get_notifications_controller(self.account)

    def get_messages_storage(self):
        return get_messages_storage(self.account)

    def get_send_messages_helper(self):
        return get_send_messages_helper(self.account)

    def get_file_loader(self):
        return get_file_loader(self.account)

    def get_secret_chat_helper(self):
        return get_secret_chat_helper(self.account)

    def get_download_controller(self):
        return get_download_controller(self.account)

    def get_notifications_settings(self):
        return get_notifications_settings(self.account)

    def get_notification_center(self):
        return get_notification_center(self.account)

    def get_user_config(self):
        return get_user_config(self.account)

    def get_user_id(self):
        return get_user_id(self.account)

    def get_file_ref_controller(self):
        return get_file_ref_controller(self.account)

    def get_stats_controller(self):
        return get_stats_controller(self.account)

    def get_media_controller(self):
        return get_media_controller(self.account)

    def get_last_fragment(self):
        return get_last_fragment()

    def get_context(self):
        return get_context()

    # -- requests / queues --

    def send_request(self, request, fn) -> int:
        return send_request(request, fn, account=self.account)

    def run_on_queue(self, fn, queue: str = None, delay: int = 0,
                     delay_ms: int = None, queue_name: str = None):
        return run_on_queue(fn, queue=queue, delay=delay, delay_ms=delay_ms,
                            queue_name=queue_name)

    # -- sending --

    def send_text(self, peer_id, text, replyToMsg=None, parse_mode=None, **kwargs):
        return send_text(peer_id, text, replyToMsg=replyToMsg,
                         parse_mode=parse_mode, account=self.account, **kwargs)

    def send_message(self, params: dict, parse_mode=None, **kwargs):
        return send_message(params, parse_mode=parse_mode, account=self.account,
                            **kwargs)

    def send_photo(self, peer_id, path, caption=None, high_quality=False,
                   parse_mode=None, replyToMsg=None, **kwargs):
        return send_photo(peer_id, path, caption=caption, high_quality=high_quality,
                          parse_mode=parse_mode, replyToMsg=replyToMsg,
                          account=self.account, **kwargs)

    def send_video(self, peer_id, path, caption=None, parse_mode=None,
                   replyToMsg=None, **kwargs):
        return send_video(peer_id, path, caption=caption, parse_mode=parse_mode,
                          replyToMsg=replyToMsg, account=self.account, **kwargs)

    def send_document(self, peer_id, path, caption=None, parse_mode=None,
                      replyToMsg=None, **kwargs):
        return send_document(peer_id, path, caption=caption, parse_mode=parse_mode,
                             replyToMsg=replyToMsg, account=self.account, **kwargs)

    def send_audio(self, peer_id, path, caption=None, parse_mode=None,
                   replyToMsg=None, **kwargs):
        return send_audio(peer_id, path, caption=caption, parse_mode=parse_mode,
                          replyToMsg=replyToMsg, account=self.account, **kwargs)

    def edit_message(self, message_obj, text=None, file_path=None,
                     with_spoiler=False, parse_mode=None):
        return edit_message(message_obj, text=text, file_path=file_path,
                            with_spoiler=with_spoiler, parse_mode=parse_mode,
                            account=self.account)


# Re-exports: some plugins reach for these through client_utils rather than
# android_utils, and an ImportError at module level kills the whole plugin.
from android_utils import log, run_on_ui_thread  # noqa: E402,F401

# Alias used by some plugins for the same "topmost visible fragment" lookup.
get_current_fragment = get_last_fragment


def get_client(account=None) -> AccountClient:
    """AccountClient for *account* (None: follow hook scope, then UI selection)."""
    return AccountClient(account)


# NotificationCenter

_delegate_proxy_base = None


def _delegate_proxy_class():
    """dynamic_proxy-база делегата — лениво, на хосте java нет.

    Экземпляр обязан быть именно java-объектом: пример из документации
    передаёт delegate в ``NotificationCenter.addObserver`` как есть.
    """
    global _delegate_proxy_base
    if _delegate_proxy_base is None:
        from java import dynamic_proxy

        interface = _jclass(
            "org.telegram.messenger.NotificationCenter$NotificationCenterDelegate")

        class _Proxy(dynamic_proxy(interface)):
            def didReceivedNotification(self, notification_id, account, args):
                """Override in a subclass. `args` is a Java Object[] array."""

            def start_observing(self, notification_id: int, account=None):
                """addObserver(self) on the account's NotificationCenter."""
                get_notification_center(account).addObserver(self, int(notification_id))
                return self

            def stop_observing(self, notification_id: int, account=None):
                """removeObserver(self) on the account's NotificationCenter."""
                get_notification_center(account).removeObserver(self, int(notification_id))
                return self

        _delegate_proxy_base = _Proxy
    return _delegate_proxy_base


class _DelegateMeta(type):
    """Подклассы собираются от dynamic_proxy-базы, а не от python-корня.

    Сам корневой класс остаётся python-классом: база нужна в момент
    объявления подкласса, то есть при импорте плагина на устройстве, а
    импорт client_utils на хосте не должен трогать JVM.
    """

    def __new__(mcls, name, bases, namespace, **kwargs):
        if any(isinstance(base, _DelegateMeta) for base in bases):
            bases = (_delegate_proxy_class(),) + tuple(
                base for base in bases if not isinstance(base, _DelegateMeta))
        return super().__new__(mcls, name, bases, namespace, **kwargs)

    def __instancecheck__(cls, instance):
        if cls.__name__ == "NotificationCenterDelegate":
            return isinstance(instance, _delegate_proxy_class())
        return super().__instancecheck__(instance)


class NotificationCenterDelegate(metaclass=_DelegateMeta):
    """Python base for NotificationCenter.NotificationCenterDelegate.

    Subclass it and override didReceivedNotification(id, account, args); the
    instance is a Java delegate, so it goes straight into addObserver().

    NOTE: the hook-account scope does NOT propagate into
    didReceivedNotification — bind explicitly with get_client(account)
    (PLUGINS-API.md §4.1).
    """

    def __new__(cls, *args, **kwargs):
        # Прямой экземпляр корня (без подкласса) — тоже прокси, как в SDK.
        # Подклассы собираются от dynamic_proxy-базы и этот __new__ не видят.
        return _delegate_proxy_class()()


def __getattr__(name: str):
    # Плагины достают jclass/dynamic_proxy и из client_utils (см. заголовок);
    # на хосте java нет, поэтому отдаём лениво.
    if name in ("jclass", "dynamic_proxy"):
        import java

        return getattr(java, name)
    raise AttributeError(f"module {__name__!r} has no attribute {name!r}")
