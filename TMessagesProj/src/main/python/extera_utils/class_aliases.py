"""Чужие имена Java-классов → имена классов re:gram.

Плагины каталога написаны под exteraGram и берут его классы по полному имени:
``find_class("com.exteragram.messenger.pillstack.core.PillRegistry")``,
``jclass(...)`` или ``from com.exteragram.messenger.plugins import PluginsController``.
У нас тот же код лежит в ``app.regram.*``, поэтому без подстановки плагин
получает ``None`` и падает на первом же обращении.

Второй слой — ``app.exteraless.*``: так эти классы назывались до переименования
пакета. Плагины, написанные под exteraless, обращаются по старому имени, и оно
нормализуется в то же ``app.regram.*``.

Подстановка работает только на имени: если класса с полученным именем у нас нет,
вызывающий получает прежний отказ. Разрешения проверяются уже по нашему имени —
правила в ``plugin_loader._JAVA_CLASS_RULES`` записаны для ``app.regram.*``.
"""

import importlib as _importlib
import importlib.util as _importlib_util
import sys as _sys
import types as _types

ROOT = "com.exteragram.messenger"
MEDIA_ROOT = "com.google.android.exoplayer2"
LEGACY_ROOT = "app.exteraless"
OUR_ROOT = "app.regram"
ROOTS = (ROOT, MEDIA_ROOT, LEGACY_ROOT)


def _under_root(name):
    for root in ROOTS:
        if name.startswith(root + "."):
            return True
    return False


_EXACT = {
    "com.exteragram.messenger.utils.chats.ChatUtils":
        "com.exteragram.messenger.utils.chats.ChatUtils",
    "com.exteragram.messenger.utils.ChatUtils":
        "com.exteragram.messenger.utils.chats.ChatUtils",
    "com.exteragram.messenger.utils.text.LocaleUtils":
        "com.exteragram.messenger.utils.text.LocaleUtils",
    "com.exteragram.messenger.utils.LocaleUtils":
        "com.exteragram.messenger.utils.text.LocaleUtils",
    "com.exteragram.messenger.utils.AppUtils":
        "com.exteragram.messenger.utils.AppUtils",
    "com.exteragram.messenger.R":
        "org.telegram.messenger.R",
    "com.exteragram.messenger.utils.system.VibratorUtils":
        "com.exteragram.messenger.utils.system.VibratorUtils",
    "com.exteragram.messenger.ai.AiConfig":
        "app.regram.ai.AiConfig",
    "com.exteragram.messenger.ai.AiController":
        "com.exteragram.messenger.ai.AiController",
    "com.exteragram.messenger.ai.ui.ResponseAlert":
        "com.exteragram.messenger.ai.ui.ResponseAlert",
    "com.exteragram.messenger.ai.ui.GenerateFromMessageBottomSheet":
        "com.exteragram.messenger.ai.ui.GenerateFromMessageBottomSheet",
    "com.exteragram.messenger.plugins.ui.components.InstallPluginBottomSheet":
        "com.exteragram.messenger.plugins.ui.components.InstallPluginBottomSheet",
    "com.exteragram.messenger.utils.system.SystemUtils":
        "com.exteragram.messenger.utils.system.SystemUtils",
    "com.exteragram.messenger.utils.SystemUtils":
        "com.exteragram.messenger.utils.system.SystemUtils",
    "com.exteragram.messenger.preferences.MainPreferencesActivity":
        "app.regram.settings.OpenExteraSettingsActivity",
    "com.exteragram.messenger.preferences.GeneralPreferencesActivity":
        "app.regram.settings.OpenExteraGeneralActivity",
    "com.exteragram.messenger.preferences.AppearancePreferencesActivity":
        "app.regram.settings.OpenExteraAppearanceActivity",
    "com.exteragram.messenger.preferences.ChatsPreferencesActivity":
        "app.regram.settings.OpenExteraChatsActivity",
    "com.exteragram.messenger.preferences.OtherPreferencesActivity":
        "app.regram.settings.OpenExteraOtherActivity",
    "com.exteragram.messenger.preferences.AppNavigationPreferencesActivity":
        "app.regram.settings.OpenExteraAppNavigationActivity",
    "com.exteragram.messenger.preferences.BasePreferencesActivity":
        "com.exteragram.messenger.preferences.BasePreferencesActivity",
    "com.exteragram.messenger.preferences.components.AltSeekbar":
        "app.regram.appearance.AltSeekbar",
    "com.exteragram.messenger.utils.chats.MainMenuHelper":
        "app.regram.drawer.MainMenuHelper",
    "com.exteragram.messenger.icons.ui.IconPacksActivity":
        "app.regram.icons.IconPacksActivity",
    "com.exteragram.messenger.pillstack.ui.pills.crypto.utils.ColoredBackground":
        "app.regram.pillstack.pills.ColoredBackground",
    "com.exteragram.messenger.pillstack.ui.pills.weather.WeatherPill":
        "app.regram.pillstack.pills.WeatherPill",
    "com.exteragram.messenger.pillstack.ui.PillStackPreferencesActivity":
        "app.regram.pillstack.PillStackSettingsActivity",
    "com.exteragram.messenger.pillstack.ui.PillStackLayout":
        "app.regram.pillstack.PillStackView",
    "com.exteragram.messenger.pillstack.ui.pills.weather.WeatherPreferencesActivity":
        "app.regram.pillstack.pills.weather.WeatherSettingsActivity",
    "com.exteragram.messenger.preferences.appearance.AppearancePreferencesActivity":
        "app.regram.settings.OpenExteraAppearanceActivity",
    "com.exteragram.messenger.nowplaying.ui.components.NowPlayingCard":
        "app.regram.components.ProfileMusicCard",
    "com.exteragram.messenger.nowplaying.NowPlayingController":
        "app.regram.nowplaying.NowPlayingController",
    "com.exteragram.messenger.proxy.ProxyController":
        "app.regram.proxy.ProxyController",
    "com.exteragram.messenger.utils.ui.UIUtil":
        "app.regram.utils.UIUtil",
    "com.exteragram.messenger.utils.ui.MainTabsUiHelper":
        "app.regram.appearance.MainTabsUiHelper",
    "com.google.android.exoplayer2.util.Consumer":
        "androidx.media3.common.util.Consumer",
    "com.google.android.exoplayer2.video.VideoSize":
        "androidx.media3.common.VideoSize",
    "com.google.android.exoplayer2.PlaybackException":
        "androidx.media3.common.PlaybackException",
    "com.google.android.exoplayer2.PlaybackParameters":
        "androidx.media3.common.PlaybackParameters",
    "com.google.android.exoplayer2.C":
        "androidx.media3.common.C",
}

_PREFIXES = (
    ("com.exteragram.messenger.ai.data.", "app.regram.ai.data."),
    ("com.exteragram.messenger.ai.network.", "app.regram.ai.network."),
    ("com.exteragram.messenger.pillstack.core.", "app.regram.pillstack."),
    ("com.exteragram.messenger.pillstack.ui.pills.", "app.regram.pillstack.pills."),
    ("com.exteragram.messenger.pillstack.ui.", "app.regram.pillstack."),
    ("com.exteragram.messenger.preferences.", "app.regram.settings."),
    ("com.exteragram.messenger.plugins.", "app.regram.plugins."),
    ("com.exteragram.messenger.icons.", "app.regram.icons."),
    ("com.exteragram.messenger.camera.", "app.regram.camera."),
    ("com.exteragram.messenger.backup.", "app.regram.backup."),
    ("com.exteragram.messenger.feed.", "app.regram.feed."),
    ("com.exteragram.messenger.drawer.", "app.regram.drawer."),
    ("com.exteragram.messenger.components.", "app.regram.components."),
    ("com.exteragram.messenger.utils.", "app.regram.utils."),
    # Старое имя нашего пакета: плагины, писавшиеся под exteraless, обращаются
    # по нему. Работает и обратная связь — app.regram.* проходит насквозь.
    ("app.exteraless.", "app.regram."),
)


def resolve(name):
    """Наше имя класса для имени exteraGram; чужие имена возвращаются как есть."""
    if not isinstance(name, str) or not _under_root(name):
        return name
    exact = _EXACT.get(name)
    if exact is not None:
        return exact
    outer, sep, nested = name.partition("$")
    exact = _EXACT.get(outer)
    if exact is not None:
        return exact + sep + nested
    for old, new in _PREFIXES:
        if outer.startswith(old):
            return new + outer[len(old):] + sep + nested
    return name


_FIELD_SHAPED = {
    "com.exteragram.messenger.ExteraConfig": {
        "translationProvider": ("getTranslationProvider", "setTranslationProvider"),
        "translationFormality": ("getTranslationFormality", "setTranslationFormality"),
        "disableNumberRounding": ("getDisableNumberRounding", "setDisableNumberRounding"),
        "formatTimeWithSeconds": ("getFormatTimeWithSeconds", "setFormatTimeWithSeconds"),
        "relativeLastSeen": ("getRelativeLastSeen", "setRelativeLastSeen"),
        "inAppVibration": ("getInAppVibration", "setInAppVibration"),
        "filterZalgo": ("getFilterZalgo", "setFilterZalgo"),
        "useYandexMaps": ("getUseYandexMaps", "setUseYandexMaps"),
        "downloadSpeedBoost": ("getDownloadSpeedBoost", "setDownloadSpeedBoost"),
        "uploadSpeedBoost": ("getUploadSpeedBoost", "setUploadSpeedBoost"),
        "hidePhoneNumber": ("getHidePhoneNumber", "setHidePhoneNumber"),
        "showIdAndDc": ("getShowIdAndDc", "setShowIdAndDc"),
        "hideArchiveFolder": ("getHideArchiveFolder", "setHideArchiveFolder"),
        "archiveOnPull": ("getArchiveOnPull", "setArchiveOnPull"),
        "disableUnarchiveSwipe": ("getDisableUnarchiveSwipe", "setDisableUnarchiveSwipe"),
        "doNotUseProxy": ("getDoNotUseProxy", "setDoNotUseProxy"),
        "customSavePath": ("getCustomSavePath", "setCustomSavePath"),
        "iconPack": ("getIconPack", "setIconPack"),
        "editingIconPackId": ("getEditingIconPackId", "setEditingIconPackId"),
        "avatarCorners": ("getAvatarCorners", "setAvatarCorners"),
        "singleCornerRadius": ("getSingleCornerRadius", "setSingleCornerRadius"),
        "dividerStyle": ("getDividerStyle", "setDividerStyle"),
        "forceSnow": ("getForceSnow", "setForceSnow"),
        "hideActionBarStatus": ("getHideActionBarStatus", "setHideActionBarStatus"),
        "centerTitle": ("getCenterTitle", "setCenterTitle"),
        "hideStories": ("getHideStories", "setHideStories"),
        "hideFloatingButton": ("getHideFloatingButton", "setHideFloatingButton"),
        "hideDialogsSearchBar": ("getHideDialogsSearchBar", "setHideDialogsSearchBar"),
        "senderMiniAvatars": ("getSenderMiniAvatars", "setSenderMiniAvatars"),
        "titleText": ("getTitleText", "setTitleText"),
        "tabIcons": ("getTabIcons", "setTabIcons"),
        "tabCounter": ("getTabCounter", "setTabCounter"),
        "hideAllChats": ("getHideAllChats", "setHideAllChats"),
        "squareFab": ("getSquareFab", "setSquareFab"),
        "sectionRadius": ("getSectionRadius", "setSectionRadius"),
        "sectionsSeparatedHeadersPreference": ("getSectionsSeparatedHeadersPreference", "setSectionsSeparatedHeadersPreference"),
        "newLoadingStyle": ("getNewLoadingStyle", "setNewLoadingStyle"),
        "newSliderStyle": ("getNewSliderStyle", "setNewSliderStyle"),
        "newSwitchStyle": ("getNewSwitchStyle", "setNewSwitchStyle"),
        "newChatHeaderStyle": ("getNewChatHeaderStyle", "setNewChatHeaderStyle"),
        "newNavigationBarStyle": ("getNewNavigationBarStyle", "setNewNavigationBarStyle"),
        "tabletMode": ("getTabletMode", "setTabletMode"),
        "useSystemFonts": ("getUseSystemFonts", "setUseSystemFonts"),
        "gooeyAvatarAnimation": ("getGooeyAvatarAnimation", "setGooeyAvatarAnimation"),
        "customThemes": ("getCustomThemes", "setCustomThemes"),
        "predictiveBackIntensity": ("getPredictiveBackIntensity", "setPredictiveBackIntensity"),
        "springAnimations": ("getSpringAnimations", "setSpringAnimations"),
        "glassOutlineStyle": ("getGlassOutlineStyle", "setGlassOutlineStyle"),
        "glassMessageMenu": ("getGlassMessageMenu", "setGlassMessageMenu"),
        "forceBlur": ("getForceBlur", "setForceBlur"),
        "eventType": ("getEventType", "setEventType"),
        "navigationDrawer": ("getNavigationDrawer", "setNavigationDrawer"),
        "immersiveDrawerAnimation": ("getImmersiveDrawerAnimation", "setImmersiveDrawerAnimation"),
        "showFeedTab": ("getShowFeedTab", "setShowFeedTab"),
        "showFeedUnreadCounter": ("getShowFeedUnreadCounter", "setShowFeedUnreadCounter"),
        "stickerSize": ("getStickerSize", "setStickerSize"),
        "hideStickerTime": ("getHideStickerTime", "setHideStickerTime"),
        "replyColors": ("getReplyColors", "setReplyColors"),
        "replyEmoji": ("getReplyEmoji", "setReplyEmoji"),
        "replyBackground": ("getReplyBackground", "setReplyBackground"),
        "stickerShape": ("getStickerShape", "setStickerShape"),
        "unlimitedRecentStickers": ("getUnlimitedRecentStickers", "setUnlimitedRecentStickers"),
        "hideReactionsInPrivateChats": ("getHideReactionsInPrivateChats", "setHideReactionsInPrivateChats"),
        "hideReactionsInChannels": ("getHideReactionsInChannels", "setHideReactionsInChannels"),
        "hideReactionsInGroups": ("getHideReactionsInGroups", "setHideReactionsInGroups"),
        "doubleTapAction": ("getDoubleTapAction", "setDoubleTapAction"),
        "doubleTapActionOutOwner": ("getDoubleTapActionOutOwner", "setDoubleTapActionOutOwner"),
        "bottomButton": ("getBottomButton", "setBottomButton"),
        "widePostsInFeed": ("getWidePostsInFeed", "setWidePostsInFeed"),
        "widePostsInChannels": ("getWidePostsInChannels", "setWidePostsInChannels"),
        "telegramAiEditor": ("getTelegramAiEditor", "setTelegramAiEditor"),
        "telegramAiSummaries": ("getTelegramAiSummaries", "setTelegramAiSummaries"),
        "quickAdminShortcuts": ("getQuickAdminShortcuts", "setQuickAdminShortcuts"),
        "quickTransitionForChannels": ("getQuickTransitionForChannels", "setQuickTransitionForChannels"),
        "quickTransitionForTopics": ("getQuickTransitionForTopics", "setQuickTransitionForTopics"),
        "disableGreetingSticker": ("getDisableGreetingSticker", "setDisableGreetingSticker"),
        "hideKeyboardOnScroll": ("getHideKeyboardOnScroll", "setHideKeyboardOnScroll"),
        "addCommaAfterMention": ("getAddCommaAfterMention", "setAddCommaAfterMention"),
        "disableMarkdown": ("getDisableMarkdown", "setDisableMarkdown"),
        "hideSendAsPeer": ("getHideSendAsPeer", "setHideSendAsPeer"),
        "removeMessageTail": ("getRemoveMessageTail", "setRemoveMessageTail"),
        "replaceEditedWithIcon": ("getReplaceEditedWithIcon", "setReplaceEditedWithIcon"),
        "showOnlineStatus": ("getShowOnlineStatus", "setShowOnlineStatus"),
        "hideShareButton": ("getHideShareButton", "setHideShareButton"),
        "showResultsBeforeVoting": ("getShowResultsBeforeVoting", "setShowResultsBeforeVoting"),
        "showCopyPhotoButton": ("getShowCopyPhotoButton", "setShowCopyPhotoButton"),
        "showSaveMessageButton": ("getShowSaveMessageButton", "setShowSaveMessageButton"),
        "showRepeatMessageButton": ("getShowRepeatMessageButton", "setShowRepeatMessageButton"),
        "showClearButton": ("getShowClearButton", "setShowClearButton"),
        "showHistoryButton": ("getShowHistoryButton", "setShowHistoryButton"),
        "showReportButton": ("getShowReportButton", "setShowReportButton"),
        "showGenerateButton": ("getShowGenerateButton", "setShowGenerateButton"),
        "showDetailsButton": ("getShowDetailsButton", "setShowDetailsButton"),
        "groupMessageMenu": ("getGroupMessageMenu", "setGroupMessageMenu"),
        "recognitionLanguage": ("getRecognitionLanguage", "setRecognitionLanguage"),
        "postprocessingWithAi": ("getPostprocessingWithAi", "setPostprocessingWithAi"),
        "cameraType": ("getCameraType", "setCameraType"),
        "extendedFramesPerSecond": ("getExtendedFramesPerSecond", "setExtendedFramesPerSecond"),
        "cameraStabilization": ("getCameraStabilization", "setCameraStabilization"),
        "cameraMirrorMode": ("getCameraMirrorMode", "setCameraMirrorMode"),
        "videoMessagesCamera": ("getVideoMessagesCamera", "setVideoMessagesCamera"),
        "rememberLastUsedCamera": ("getRememberLastUsedCamera", "setRememberLastUsedCamera"),
        "startWithWideAngleCamera": ("getStartWithWideAngleCamera", "setStartWithWideAngleCamera"),
        "zoomSlider": ("getZoomSlider", "setZoomSlider"),
        "staticZoom": ("getStaticZoom", "setStaticZoom"),
        "alwaysSendInHD": ("getAlwaysSendInHD", "setAlwaysSendInHD"),
        "hideCameraTile": ("getHideCameraTile", "setHideCameraTile"),
        "doubleTapSeekDuration": ("getDoubleTapSeekDuration", "setDoubleTapSeekDuration"),
        "preferOriginalQuality": ("getPreferOriginalQuality", "setPreferOriginalQuality"),
        "swipeToPip": ("getSwipeToPip", "setSwipeToPip"),
        "unmuteWithVolumeButtons": ("getUnmuteWithVolumeButtons", "setUnmuteWithVolumeButtons"),
        "pauseOnMinimizeVideo": ("getPauseOnMinimizeVideo", "setPauseOnMinimizeVideo"),
        "pauseOnMinimizeVoice": ("getPauseOnMinimizeVoice", "setPauseOnMinimizeVoice"),
        "pauseOnMinimizeRound": ("getPauseOnMinimizeRound", "setPauseOnMinimizeRound"),
        "useGoogleCrashlytics": ("getUseGoogleCrashlytics", "setUseGoogleCrashlytics"),
        "useGoogleAnalytics": ("getUseGoogleAnalytics", "setUseGoogleAnalytics"),
        "enableAdBlock": ("getEnableAdBlock", "setEnableAdBlock"),
        "updateScheduleTimestamp": ("getUpdateScheduleTimestamp", "setUpdateScheduleTimestamp"),
        "sdkUpdateScheduleTimestamp": ("getSdkUpdateScheduleTimestamp", "setSdkUpdateScheduleTimestamp"),
        "targetLang": ("getTargetLang", "setTargetLang"),
        "flashWarmth": ("getFlashWarmth", "setFlashWarmth"),
        "flashIntensity": ("getFlashIntensity", "setFlashIntensity"),
        "pluginsDevMode": ("getPluginsDevMode", "setPluginsDevMode"),
        "pluginsSafeMode": ("getPluginsSafeMode", "setPluginsSafeMode"),
        "pluginsCompactView": ("getPluginsCompactView", "setPluginsCompactView"),
        "pluginsPySdkAutoUpdate": ("getPluginsPySdkAutoUpdate", "setPluginsPySdkAutoUpdate"),
        "pluginsPySdkBetaVersions": ("getPluginsPySdkBetaVersions", "setPluginsPySdkBetaVersions"),
        "pluginsDisableArtOpts": ("getPluginsDisableArtOpts", "setPluginsDisableArtOpts"),
        "pinnedPlugins": ("getPinnedPlugins", "setPinnedPlugins"),
        "useSystemIconShape": ("getUseSystemIconShape", "setUseSystemIconShape"),
        "editor": "getEditor",
        "preferences": "getPreferences",
        "GSON": "getGSON",
        "pluginsEngine": ("getPluginsEngine", "setPluginsEngine"),
        "avatarSquareness": "getAvatarSquareness",
        "sectionRadiusDp": "getSectionRadiusDp",
        "sectionsSeparatedHeaders": ("getSectionsSeparatedHeaders", "setSectionsSeparatedHeaders"),
        "logging": ("getLogging", "setLogging"),
        "mainMenuLayout": "getMainMenuLayout",
        "mainMenuHiddenItems": "getMainMenuHiddenItems",
        "defaultMainMenuLayout": "getDefaultMainMenuLayout",
        "iconPacksLayout": "getIconPacksLayout",
        "iconPacksHidden": "getIconPacksHidden",
        "doNotMarkAsNew": "getDoNotMarkAsNew",
        "newFeaturesShowedAt": "getNewFeaturesShowedAt",
        "yandexSearchEngine": "getYandexSearchEngine",
        "currentLangName": "getCurrentLangName",
        "apiBotInfo": "getApiBotInfo",
        "backupKeys": "getBackupKeys",
        "onlineDotInnerRadius": "getOnlineDotInnerRadius",
        "onlineDotOuterRadius": "getOnlineDotOuterRadius",
    },
    "com.exteragram.messenger.plugins.PluginsController": {
        "engines": "getEngines",
        "plugins": "getPlugins",
        "pluginsDir": "getPluginsDir",
        "preferences": "getPreferences",
        "initialized": "getInitialized",
        "settings": "getSettings",
        "watchdog": "getWatchdog",
    },
    "com.exteragram.messenger.pillstack.core.PillStackConfig": {
        "activePills": "getActivePills",
        "hiddenPills": "getHiddenPills",
        "configLoaded": "isConfigLoaded",
    },
    "com.exteragram.messenger.ai.AiConfig": {
        "saveHistory": ("getSaveHistory", "setSaveHistory"),
        "responseStreaming": ("getResponseStreaming", "setResponseStreaming"),
        "temperature": ("getTemperature", "setTemperature"),
        "showResponseOnly": ("getShowResponseOnly", "setShowResponseOnly"),
        "insertAsQuote": ("getInsertAsQuote", "setInsertAsQuote"),
        "selectedServiceId": ("getSelectedServiceId", "setSelectedServiceId"),
        "selectedRole": ("getSelectedRole", "setSelectedRole"),
        "preferences": "getPreferences",
        "services": "getServices",
        "roles": "getRoles",
        "conversationHistory": "getConversationHistory",
        "selectedService": "getSelectedService",
    },
}


_INSTANCE_SHAPED = {
    "org.telegram.messenger.SharedConfig$ProxyInfo": {
        "address": ("getAddress", "setAddress"),
        "port": ("getPort", "setPort"),
        "username": ("getUsername", "setUsername"),
        "password": ("getPassword", "setPassword"),
        "secret": ("getSecret", "setSecret"),
        "link": "getLink",
    },
}

_WRAP_RESULT = {
    "org.telegram.messenger.SharedConfig": {
        "currentProxy": "org.telegram.messenger.SharedConfig$ProxyInfo",
    },
}


class _FieldShapedObject:
    """Java-объект, поля которого у эталона есть, а у нас переехали в геттеры."""

    def __init__(self, java_obj, fields):
        getters, setters = {}, {}
        for field, target in fields.items():
            if isinstance(target, (tuple, list)):
                getters[field] = target[0]
                if len(target) > 1 and target[1]:
                    setters[field] = target[1]
            else:
                getters[field] = target
        object.__setattr__(self, "_regram_java", java_obj)
        object.__setattr__(self, "_regram_fields", getters)
        object.__setattr__(self, "_regram_setters", setters)

    def __getattr__(self, attr):
        target = object.__getattribute__(self, "_regram_java")
        method = object.__getattribute__(self, "_regram_fields").get(attr)
        if method is not None:
            return getattr(target, method)()
        return getattr(target, attr)

    def __setattr__(self, attr, value):
        target = object.__getattribute__(self, "_regram_java")
        method = object.__getattribute__(self, "_regram_setters").get(attr)
        if method is not None:
            getattr(target, method)(value)
            return
        setattr(target, attr, value)

    def __eq__(self, other):
        return object.__getattribute__(self, "_regram_java") == unwrap(other)

    def __hash__(self):
        return hash(object.__getattribute__(self, "_regram_java"))

    def __repr__(self):
        return repr(object.__getattribute__(self, "_regram_java"))


def wrap_instance(obj, java_name):
    fields = _INSTANCE_SHAPED.get(java_name)
    if obj is None or fields is None or isinstance(obj, _FieldShapedObject):
        return obj
    try:
        return _FieldShapedObject(obj, fields)
    except Exception:
        return obj


class _FieldShapedClass:
    """Java-класс, у которого часть статических методов читается как поля.

    У exteraGram это поля (Kotlin-делегаты), у нас — методы: поле не умеет
    отдавать живое значение настройки. Chaquopy различает вызов и чтение
    атрибута, поэтому плагин, написанный под поле, получал объект метода —
    всегда истинный. Так zwylib считал, что включён safe mode, и молча
    отключал свои хуки.
    """

    def __init__(self, java_class, fields, wraps=None):
        getters = {}
        setters = {}
        for field, target in fields.items():
            if isinstance(target, (tuple, list)):
                getters[field] = target[0]
                if len(target) > 1 and target[1]:
                    setters[field] = target[1]
            else:
                getters[field] = target
        object.__setattr__(self, "_regram_java", java_class)
        object.__setattr__(self, "_regram_fields", getters)
        object.__setattr__(self, "_regram_setters", setters)
        object.__setattr__(self, "_regram_wraps", wraps or {})

    def __getattr__(self, attr):
        target = object.__getattribute__(self, "_regram_java")
        method = object.__getattribute__(self, "_regram_fields").get(attr)
        if method is not None:
            value = getattr(target, method)()
        else:
            value = getattr(target, attr)
        wrapped = object.__getattribute__(self, "_regram_wraps").get(attr)
        if wrapped is not None:
            return wrap_instance(value, wrapped)
        return value

    def __setattr__(self, attr, value):
        target = object.__getattribute__(self, "_regram_java")
        method = object.__getattribute__(self, "_regram_setters").get(attr)
        if method is not None:
            getattr(target, method)(value)
            return
        setattr(target, attr, value)

    def __repr__(self):
        return repr(object.__getattribute__(self, "_regram_java"))


def unwrap(obj):
    """Настоящий Java-класс из обёртки; чужие объекты возвращаются как есть."""
    if isinstance(obj, (_FieldShapedClass, _FieldShapedObject)):
        return object.__getattribute__(obj, "_regram_java")
    return obj


def _field_shape(name):
    fields = _FIELD_SHAPED.get(name)
    if fields is not None:
        return fields
    for source, shaped in _FIELD_SHAPED.items():
        if resolve(source) == name:
            return shaped
    return None


def adapt(name, obj):
    """Обёртка над классом, форма которого у нас разошлась с эталоном."""
    if obj is None or isinstance(obj, _FieldShapedClass):
        return obj
    replacement = substitute(name)
    if replacement is not None:
        return replacement
    fields = _field_shape(name)
    wraps = _WRAP_RESULT.get(name)
    if fields is None and wraps is None:
        return obj
    try:
        return _FieldShapedClass(obj, fields or {}, wraps)
    except Exception:
        return obj


_PYTHON_SUBSTITUTES = {
    "com.exteragram.messenger.plugins.models.PluginItemFactory":
        ("ui.settings", "SimpleSettingFactory"),
}


def substitute(name):
    if not isinstance(name, str):
        return None
    target = _PYTHON_SUBSTITUTES.get(name)
    if target is None:
        return None
    module, attr = target
    try:
        return getattr(_importlib.import_module(module), attr, None)
    except Exception:
        return None


def is_alias(name):
    """Стоит ли пытаться подставлять это имя."""
    return isinstance(name, str) and _under_root(name)


def _find_class(name):
    try:
        from hook_utils import find_class
        return find_class(name)
    except Exception:
        return None


class _AliasModule(_types.ModuleType):
    """Пакет-заглушка: атрибут сначала пробуется как класс, потом как подпакет."""

    def __getattr__(self, attr):
        if attr.startswith("__"):
            raise AttributeError(attr)
        full = self.__name__ + "." + attr
        replacement = substitute(full)
        if replacement is not None:
            return replacement
        found = _find_class(full)
        if found is not None:
            return found
        if attr[:1].isupper():
            raise AttributeError(attr)
        try:
            return _importlib.import_module(full)
        except Exception:
            raise AttributeError(attr)


class _AliasFinder:
    """sys.meta_path-финдер на ``com.exteragram.*`` и старое имя нашего пакета.

    Нужен для формы ``from com.exteragram.messenger.plugins import PluginsController``:
    она идёт мимо find_class и jclass, в машинерию импорта.

    Для ``app.exteraless`` подключена только прямая ветка: пакет ``app`` у нас
    настоящий и содержит ``app.regram``, а обратная ветка поставила бы на его
    место заглушку и затенила собственные классы приложения.
    """

    PACKAGE = "com.exteragram"
    PACKAGES = ("com.exteragram", MEDIA_ROOT)

    def find_spec(self, fullname, path=None, target=None):
        if not (fullname == LEGACY_ROOT or fullname.startswith(LEGACY_ROOT + ".")):
            if not any(fullname == root or fullname.startswith(root + ".")
                       or root.startswith(fullname + ".")
                       for root in self.PACKAGES):
                return None
        if fullname.rpartition(".")[2][:1].isupper():
            return None
        return _importlib_util.spec_from_loader(fullname, _AliasLoader(), is_package=True)


class _AliasLoader:

    def create_module(self, spec):
        module = _AliasModule(spec.name)
        module.__path__ = []
        return module

    def exec_module(self, module):
        return None


def _ensure_root_package():
    """Создаёт пакет ``com``, если его не даёт Chaquopy.

    На устройстве ``com`` существует (com.google, com.android), и подменять его
    нельзя. Заглушка ставится только там, где импорт вообще не проходит.
    """
    try:
        _importlib.import_module("com")
        return
    except Exception:
        pass
    module = _AliasModule("com")
    module.__path__ = []
    _sys.modules["com"] = module


def install_import_hook():
    """Ставит финдер в sys.meta_path. Идемпотентно, ничего не бросает."""
    try:
        if any(isinstance(finder, _AliasFinder) for finder in _sys.meta_path):
            return
        _sys.meta_path.insert(0, _AliasFinder())
        _ensure_root_package()
    except Exception:
        pass
