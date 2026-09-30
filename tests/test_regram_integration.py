"""Source/packaging regression checks, NOT Android compilation or device tests."""
import importlib.util
import json
import os
from pathlib import Path
import re
import sys
import tempfile
import unittest
import xml.etree.ElementTree as ET

ROOT = Path(__file__).resolve().parents[1]
JAVA = ROOT / "TMessagesProj/src/main/java"


def module(name):
    spec = importlib.util.spec_from_file_location(name, ROOT / "tools" / f"{name}.py")
    result = importlib.util.module_from_spec(spec)
    spec.loader.exec_module(result)
    return result


branding = module("generate_branding")
preflight = module("check_build_env")
firebase_placeholder = module("make_placeholder_google_services")


class RegramIntegrationTests(unittest.TestCase):
    def test_credits_are_reachable_and_source_handles_are_preserved(self):
        credits = (JAVA / "app/regram/ui/RegramCreditsActivity.java").read_text()
        features = (JAVA / "app/regram/ui/RegramSettingsActivity.java").read_text()
        root = (JAVA / "app/regram/settings/OpenExteraSettingsActivity.java").read_text()
        for name in ("WeexTech", "@ihufe", "ChatGPT", "@lime_2612", "@atb_ptzhn",
                     "@nagramxf", "@exteraless", "@inugram", "@exteragram", "@ayugram"):
            self.assertIn('"' + name + '"', credits)
        self.assertIn("presentFragment(new RegramCreditsActivity())", features)
        self.assertIn("new app.regram.ui.RegramCreditsActivity()", root)
        for language in ("values", "values-ru-rRU"):
            path = ROOT / "TMessagesProj/src/main/res" / language / "strings_regram.xml"
            names = {node.get("name") for node in ET.parse(path).getroot()}
            self.assertTrue({"RegramCreatorsAndSources", "RegramOwner", "RegramDesigner",
                             "RegramCoder1", "RegramCoder2", "RegramCoder3", "RegramCodeSources"} <= names)
        for path, text in branding.overlays().items():
            for node in ET.fromstring(text).findall("string"):
                self.assertNotRegex(node.text or "", r"(?i)nagram|exteraless|exteragram")

    def test_branding_overlays_are_current(self):
        for path, expected in branding.overlays().items():
            self.assertEqual((ROOT / "TMessagesProj/src/branding/res" / path).read_text(), expected)

    def test_all_app_name_translations_are_rebranded(self):
        for path in (ROOT / "TMessagesProj/src/main/res").glob("values*/*.xml"):
            names = {n.get("name") for n in ET.parse(path).getroot().findall("string")}
            for name in names & branding.FIXED.keys():
                overlay = ROOT / "TMessagesProj/src/branding/res" / path.parent.name / "strings_branding.xml"
                values = {n.get("name"): n.text for n in ET.parse(overlay).getroot()}
                self.assertEqual(values[name], branding.FIXED[name])

    def test_all_build_types_use_branding(self):
        gradle = (ROOT / "TMessagesProj/build.gradle").read_text()
        self.assertIn("['debug', 'staging', 'release'].each", gradle)
        self.assertIn("res.srcDir('src/branding/res')", gradle)
        self.assertIn("APP_PACKAGE=app.regram.android", (ROOT / "gradle.properties").read_text())

    def test_non_debug_builds_cannot_fall_back_to_debug_signing(self):
        gradle = (ROOT / "TMessagesProj/build.gradle").read_text()
        self.assertIn("releaseKeystore.isFile()", gradle)
        self.assertIn("[keystorePwd, alias, pwd].every { it != null && !it.isBlank() }", gradle)
        self.assertIn("gradle.taskGraph.whenReady", gradle)
        self.assertIn("task.name ==~ /(?i).*(release|staging).*/", gradle)
        self.assertIn("System.getenv('REGRAM_COMPILE_CHECK') == '1'", gradle)
        self.assertIn("releaseAppHash == '00000000000000000000000000000000'", gradle)
        self.assertIn("releaseAppId == '9999999'", gradle)
        self.assertIn("new JsonSlurper().parse(firebaseFile)", gradle)
        self.assertIn("non-placeholder Firebase client", gradle)
        self.assertNotIn("hasReleaseKeystore ? signingConfigs.release : signingConfigs.debug", gradle)
        self.assertEqual(gradle.count("signingConfig = signingConfigs.release"), 2)
        self.assertEqual(gradle.count("signingConfig = signingConfigs.debug"), 1)

    def test_android_identity_does_not_target_the_base_app(self):
        paths = [ROOT / "TMessagesProj/src/main/AndroidManifest.xml"]
        paths.extend((ROOT / "TMessagesProj/src/main/res/xml").glob("*.xml"))
        for path in paths:
            self.assertNotIn("com.exteraless.app", path.read_text(), str(path))

    def test_every_port_has_persistence_ui_and_call_site(self):
        config = (JAVA / "tw/nekomimi/nekogram/NekoConfig.java").read_text()
        options = re.findall(r'ConfigItem (regram\w+) = addConfig\("(Regram\w+)", configTypeBool, false\)', config)
        self.assertEqual(len(options), 10)
        settings = (JAVA / "app/regram/ui/RegramSettingsActivity.java").read_text()
        consumers = "\n".join((JAVA / path).read_text() for path in (
            "app/regram/ui/M3SliderHelper.kt",
            "org/telegram/ui/ArticleViewer.java",
            "org/telegram/ui/Components/ChatAttachAlertPhotoLayout.java",
            "org/telegram/ui/GroupCreateActivity.java",
            "org/telegram/messenger/MediaController.java",
            "org/telegram/ui/Components/SizeNotifierFrameLayout.java",
            "org/telegram/messenger/ProxyPingController.java",
            "org/telegram/messenger/MessageObject.java",
            "org/telegram/messenger/MessagesController.java",
            "org/telegram/ui/Adapters/MentionsAdapter.java",
            "org/telegram/ui/TopicsFragment.java",
        ))
        resources = ET.parse(ROOT / "TMessagesProj/src/main/res/values/strings_regram.xml").getroot()
        names = {node.get("name") for node in resources}
        for field, key in options:
            self.assertIn("NekoConfig." + field, settings)
            self.assertIn("NekoConfig." + field + ".Bool()", consumers)
            self.assertIn(key, names)
            self.assertIn(key + "Info", names)

    def test_icon_packs_recover_after_process_restart_and_reset(self):
        manager = (JAVA / "app/regram/icons/IconPackManager.java").read_text()
        reset = (JAVA / "app/regram/general/GeneralHelper.java").read_text()
        init = manager[manager.index("public void ensureInitialized()"):manager.index("public void reload()")]
        self.assertIn("reloadInternal();", init)
        self.assertNotIn("initialized = true;", init)
        self.assertIn("public synchronized void reloadInternal()", manager)
        self.assertIn("if (ApplicationLoader.applicationContext == null)", manager)
        self.assertIn("missing.clear();\n                initialized = true;", manager)
        self.assertIn("IconPacksConfig.reset();\n        app.regram.icons.IconPackManager.getInstance().reload();", reset)

    def test_tablet_mode_tracks_forced_setting_and_orientation(self):
        utilities = (JAVA / "org/telegram/messenger/AndroidUtilities.java").read_text()
        navigation = (JAVA / "app/regram/settings/OpenExteraAppNavigationActivity.java").read_text()
        self.assertIn("if (mode == NekoConfig.TABLET_ENABLE)", utilities)
        self.assertIn("else if (mode != NekoConfig.TABLET_AUTO)", utilities)
        self.assertIn("isTablet = isTabletForce();", utilities)
        self.assertIn("isTabletInternal() && !SharedConfig.forceDisableTabletMode", utilities)
        self.assertIn("NekoConfig.tabletMode.setConfigInt(which);\n                        AndroidUtilities.resetTabletFlag();", navigation)

    def test_unchanged_failed_plugin_is_not_retried_on_rescan(self):
        controller = (JAVA / "app/regram/plugins/PluginsController.java").read_text()
        self.assertIn("if (existing != null && path.equals(existing.path)\n"
                      "                    && metadataJsonCache.containsKey(key))", controller)
        self.assertNotIn("existing.loadError == null && path.equals(existing.path)", controller)
        self.assertIn("p.enabled && !p.loaded && p.loadError == null", controller)

    def test_bare_web_links_prefer_https_but_preserve_ipv4_http(self):
        utilities = (JAVA / "org/telegram/messenger/AndroidUtilities.java").read_text()
        self.assertIn('return url != null && IPV4_URL.matcher(url).lookingAt() ? "http://" : "https://";', utilities)
        self.assertIn('"http://".equals(prefixes[0]) ? defaultUrlScheme(url) : prefixes[0]', utilities)
        for path in ("org/telegram/ui/Cells/SharedLinkCell.java",
                     "org/telegram/ui/Components/PhonebookShareAlert.java"):
            self.assertIn("AndroidUtilities.defaultUrlScheme(", (JAVA / path).read_text())

    def test_ai_history_is_accessible_and_empty_responses_are_errors(self):
        screen = (JAVA / "app/regram/ai/ui/AiSettingsActivity.java").read_text()
        client = (JAVA / "app/regram/ai/network/Client.java").read_text()
        self.assertIn('viewHistoryRow = addRow("aiHistory")', screen)
        self.assertIn("AiConfig.getConversationHistory()", screen)
        self.assertIn("showConversationHistory();", screen)
        self.assertIn('response == null || response.trim().isEmpty()', client)
        self.assertIn('notifyError(requestId, callback, 0, "Response body is empty")', client)
        for language in ("values", "values-ru-rRU"):
            path = ROOT / "TMessagesProj/src/main/res" / language / "strings_oe_ai.xml"
            names = {node.get("name") for node in ET.parse(path).getroot()}
            self.assertTrue({"OEAiViewHistory", "OEAiHistoryEmpty",
                             "OEAiHistoryUser", "OEAiHistoryAssistant"} <= names)

    def test_september_ports_have_settings_and_consumers(self):
        kotlin = ROOT / "TMessagesProj/src/main/kotlin/app/regram"
        ports = (
            ("chats/ChatsConfig.kt", "hideChannelSearchButton",
             "app/regram/settings/OpenExteraChatsActivity.java", "OEChatsHideSearchButton",
             "org/telegram/ui/ChatActivity.java", "strings_oe_chats.xml"),
            ("general/GeneralConfig.kt", "disableNotificationDelay",
             "app/regram/settings/OpenExteraGeneralActivity.java", "OEGeneralDisableNotificationDelay",
             "org/telegram/messenger/NotificationsController.java", "strings_oe_general.xml"),
        )
        for config_file, field, settings_file, label, consumer_file, strings_file in ports:
            with self.subTest(field=field):
                self.assertIn(f'val {field} = addConfig(', (kotlin / config_file).read_text())
                self.assertIn(field, (JAVA / settings_file).read_text())
                self.assertIn(field, (JAVA / consumer_file).read_text())
                for language in ("values", "values-ru-rRU"):
                    strings = ROOT / "TMessagesProj/src/main/res" / language / strings_file
                    names = {node.get("name") for node in ET.parse(strings).getroot()}
                    self.assertIn(label, names)

    def test_general_topic_debloat_preserves_explicit_topic_actions(self):
        topics = (JAVA / "org/telegram/ui/TopicsFragment.java").read_text()
        self.assertIn("topic.id == 1 && selectedTopics.isEmpty() && NekoConfig.regramDisableGeneralTopicSwipe.Bool()", topics)
        self.assertIn("if (selectedTopics.isEmpty() && viewHolder.itemView instanceof TopicDialogCell && topic.id == 1)", topics)
        settings = (JAVA / "app/regram/ui/RegramSettingsActivity.java").read_text()
        self.assertIn("NekoConfig.regramDisableGeneralTopicSwipe", settings)
        for language in ("values", "values-ru-rRU"):
            strings = (ROOT / "TMessagesProj/src/main/res" / language / "strings_regram.xml").read_text()
            self.assertIn('name="RegramDisableGeneralTopicSwipe"', strings)
            self.assertIn('name="RegramDisableGeneralTopicSwipeInfo"', strings)
        cover = (JAVA / "org/telegram/ui/Components/EditCoverButton.java").read_text()
        self.assertNotIn("Bitmap.createBitmap(", cover)
        self.assertIn("AndroidUtilities.runOnUIThread(() -> setImage(frame));", cover)
        reaction = (JAVA / "org/telegram/ui/Components/Reactions/ReactionsLayoutInBubble.java").read_text()
        self.assertNotIn("if (false && !hasPaidReaction)", reaction)
        self.assertNotIn("includeEmptyStarButton", reaction)
        self.assertIn("includeEmptyLikeButton", reaction)
        inventory = {row["patch"]: row["status"] for row in json.loads(
            (ROOT / "docs/source-inventory.json").read_text())["inugram_md3"]}
        self.assertTrue(inventory["debloat/disable-swipe-to-hide-general-topic.patch"].startswith("adapted:"))
        for name in ("disable-swipe-to-unarchive", "notification-bubbles", "hide-ai-features", "hide-trending-stickers"):
            self.assertTrue(inventory[f"debloat/{name}.patch"].startswith("inherited:"))
        self.assertTrue(inventory["feature/show-seconds.patch"].startswith("inherited:"))

    def test_plugin_hook_failure_is_diagnosable_without_bypassing_permissions(self):
        message = (JAVA / "org/telegram/messenger/MessageObject.java").read_text()
        services = (JAVA / "app/regram/plugins/PluginServices.java").read_text()
        hooks = (JAVA / "app/regram/plugins/xposed/XposedHooks.java").read_text()
        gate = (JAVA / "app/regram/plugins/xposed/HookGate.java").read_text()
        self.assertIn("public static boolean addEntitiesToText(CharSequence text, ArrayList<TLRPC.MessageEntity> entities, boolean out, boolean usernames, boolean photoViewer, boolean useManualParse)", message)
        self.assertIn('PluginPermissions.check(pluginId, PluginPermissions.HOOKS, "hookMethod")', services)
        self.assertIn('+ ", target " + member, t)', hooks)
        self.assertIn('native hook0 returned no backup for ', gate)

    def test_build_mirror_does_not_reuse_deleted_local_credentials(self):
        build = (ROOT / "tools/build.sh").read_text()
        self.assertIn('BUILD_DIR="$(realpath -m "$BUILD_DIR")"', build)
        self.assertIn('"$BUILD_DIR" == "$HOME"', build)
        self.assertIn('"$BUILD_DIR" == "$SRC/"*', build)
        self.assertIn('"$SRC" == "$BUILD_DIR/"*', build)
        self.assertIn('rm -f "$BUILD_DIR/local.properties"', build)
        self.assertIn('chmod 600 "$BUILD_DIR/local.properties"', build)

    def test_proxy_ping_has_no_disabled_polling_or_stale_results(self):
        controller = (JAVA / "org/telegram/messenger/ProxyPingController.java").read_text()
        settings = (JAVA / "app/regram/ui/RegramSettingsActivity.java").read_text()
        self.assertIn("ProxyPingController.onSettingChanged()", settings)
        self.assertIn("AndroidUtilities.cancelRunOnUIThread(pingRunnable)", controller)
        self.assertIn("if (NekoConfig.regramLiveProxyPing.Bool() && isForeground()) scheduleNextPing(0)", controller)
        self.assertIn("scheduleNextPing(PING_INTERVAL_MS);", controller)
        self.assertIn("request != generation", controller)
        self.assertIn("proxy != SharedConfig.currentProxy || account != UserConfig.selectedAccount", controller)
        self.assertIn("SystemClock.elapsedRealtime()", controller)
        self.assertNotIn("System.currentTimeMillis()", controller)
        self.assertIn("proxy.ping = time == -1 ? 0 : time", controller)

    def test_megagroup_row_reaches_every_creation_path(self):
        source = (JAVA / "org/telegram/ui/GroupCreateFinalActivity.java").read_text()
        self.assertIn("case VIEW_TYPE_MEGAGROUP", source)
        self.assertIn("items.add(new InnerItem(VIEW_TYPE_MEGAGROUP))", source)
        self.assertIn("regramAsMegagroup = !regramAsMegagroup", source)
        self.assertEqual(source.count("createChat(editText.getText().toString(), selectedContacts, null, getEffectiveChatType()"), 2)
        self.assertNotIn("createChat(editText.getText().toString(), selectedContacts, null, chatType", source)

    def test_settings_are_reachable_and_searchable(self):
        root = (JAVA / "app/regram/settings/OpenExteraSettingsActivity.java").read_text()
        search = (JAVA / "tw/nekomimi/nekogram/helpers/SettingsHelper.java").read_text()
        self.assertIn("new app.regram.ui.RegramSettingsActivity()", root)
        self.assertIn("new app.regram.ui.RegramSettingsActivity()", search)

    def test_slider_fallbacks_and_no_missing_inugram_runtime(self):
        helper = (JAVA / "app/regram/ui/M3SliderHelper.kt").read_text()
        seek = (JAVA / "org/telegram/ui/Components/SeekBarView.java").read_text()
        self.assertNotIn("desu.inugram", helper)
        self.assertIn("if (!enabled()) return false", helper)
        self.assertIn("!view.regramCanDrawSlider()", helper)
        self.assertIn("previewingState == -1", seek)
        self.assertIn("sliderStyleOverride == -1", seek)
        self.assertIn("!twoSided && !hasCustomLineWidthValue", seek)
        self.assertIn("timestamps == null || timestamps.isEmpty()", seek)

    def test_second_batch_inugram_fixes_preserve_callsite_invariants(self):
        checks = {
            "org/telegram/messenger/MediaController.java": (
                "this.discardLivePhoto = state instanceof PhotoEntry ? ((PhotoEntry) state).discardLivePhoto : null;",
                "SharedConfig.pauseMusicOnMedia ? AudioManager.AUDIOFOCUS_GAIN_TRANSIENT : AudioManager.AUDIOFOCUS_GAIN_TRANSIENT_MAY_DUCK",
            ),
            "org/telegram/ui/CameraScanActivity.java": (
                "if (points == null || points.length != 4)",
            ),
            "org/telegram/ui/Components/ReactionsContainerLayout.java": (
                "allReactionsIsDefault))) && LiteMode.isEnabled(LiteMode.FLAG_ANIMATED_EMOJI_REACTIONS)",
            ),
            "org/telegram/ui/Components/Reactions/ReactionsLayoutInBubble.java": (
                "(dp(12) - scrimPreviewCounterDrawable.getCurrentWidth()) / 2f",
            ),
            "org/telegram/ui/Stories/PeerStoriesView.java": (
                "String firstName = UserObject.getForcedFirstName(user);",
            ),
            "org/telegram/ui/ProfileActivity.java": (
                "!hasMainTabs && (actionsView == null || !actionsView.supportsEditInfo())",
            ),
            "org/telegram/ui/GroupCallActivity.java": (
                "if (call.recording && call.call.record_start_date != 0)",
            ),
            "org/telegram/ui/Components/InstantCameraView.java": (
                "public void stopRecording(int send, SendOptions options) {\n            if (handler == null) return;",
            ),
            "org/telegram/ui/ChatActivity.java": (
                "!pausedOnLastMessage && !firstLoading && !inPreviewMode",
            ),
        }
        for path, expressions in checks.items():
            with self.subTest(path=path):
                source = (JAVA / path).read_text()
                for expression in expressions:
                    self.assertIn(expression, source)
        adapter = (JAVA / "org/telegram/ui/Components/UniversalAdapter.java").read_text()
        self.assertIn("if (position == RecyclerView.NO_POSITION) return;", adapter)
        self.assertIn("if (item == null) return;", adapter)

    def test_inventory_tracks_second_batch_and_does_not_duplicate_nagram_proxy_pill(self):
        inventory = json.loads((ROOT / "docs/source-inventory.json").read_text())
        patches = {row["patch"]: row["status"] for row in inventory["inugram_md3"]}
        for name in (
            "pause-on-media-transient", "qr-scanner-corner-points", "paid-reaction-animation",
            "reaction-counter-jump", "motion-photo-with-caption", "story-privacy-hint-npe",
            "profile-menu-duplicate-actions", "group-call-record-timer", "instant-camera-cancel-race",
            "chat-preview-scroll-persist",
        ):
            self.assertTrue(patches[f"bugfix/{name}.patch"].startswith("adapted:"), name)
        self.assertTrue(patches["bugfix/universal-adapter-reorder-npe.patch"].startswith("inherited:"))
        self.assertTrue(any(entry["feature"] == "ProxyPill display" and
                            entry["status"].startswith("inherited:")
                            for entry in inventory["nagram_adapted"]))
        self.assertTrue(any(entry["feature"] == "Case-insensitive streamed <think> tags" and
                            entry["status"].startswith("adapted:")
                            for entry in inventory["nagram_adapted"]))

    def test_third_batch_inugram_fixes_preserve_callsite_invariants(self):
        checks = {
            "org/telegram/ui/ChatActivity.java": (
                "messageObjectToReply.isSpoilersRevealed ? ~TextStyleSpan.FLAG_STYLE_SPOILER : -1",
            ),
            "org/telegram/ui/Components/Paint/Views/StickerMakerView.java": (
                "srcBitmap == null || srcBitmap.isRecycled()",
                "sourceBitmap == null || sourceBitmap.isRecycled() || segmentingLoaded",
            ),
            "org/telegram/ui/Cells/ChatMessageCell.java": (
                "if (replyPressed) cancelCheckLongPress();\n            replyPressed = false;",
                "if (replyPressed) cancelCheckLongPress();\n                replyPressed = false;",
            ),
            "org/telegram/ui/Components/RecyclerListView.java": (
                "boolean hadHashes = hashesComputed;", "if (diff && hadHashes)",
                "hashesComputed = true;", "if (child.getVisibility() != VISIBLE)",
            ),
            "org/telegram/ui/Cells/UserCell.java": (
                "adminTextView.setClickable(onClick != null && role != null);",
            ),
            "org/telegram/ui/Cells/DialogCell.java": (
                "import org.telegram.ui.Components.URLSpanMono;", "span instanceof URLSpanMono",
            ),
            "org/telegram/ui/MessageStatisticActivity.java": (
                "Emoji.replaceEmoji(message, avatarContainer.getSubtitlePaint().getFontMetricsInt(), true)",
            ),
            "org/telegram/ui/Components/Reactions/ReactionsLayoutInBubble.java": (
                "textDrawable.setTextSize(dp(13));\n                textDrawable.setIncludeFontPadding(false);",
            ),
        }
        for path, expressions in checks.items():
            with self.subTest(path=path):
                body = (JAVA / path).read_text()
                for expression in expressions:
                    self.assertIn(expression, body)
        inventory = {row["patch"]: row["status"] for row in json.loads(
            (ROOT / "docs/source-inventory.json").read_text())["inugram_md3"]}
        for name in ("reply-bar-revealed-spoiler", "sticker-maker-recycled-bitmap",
                     "reply-longpress-cancel", "sections-adapter-first-diff", "dialog-preview-code-color",
                     "statistics-shared-message-text", "reaction-tag-text-baseline", "list-row-dead-zones"):
            self.assertTrue(inventory[f"bugfix/{name}.patch"].startswith("adapted:"), name)

    def test_fourth_batch_inugram_fixes_preserve_callsite_invariants(self):
        checks = {
            "org/telegram/ui/Cells/HintDialogCell.java": (
                "NotificationCenter.getInstance(currentAccount).listen(this, NotificationCenter.updateInterfaces,",
                "args.length > 0 && args[0] instanceof Integer",
                "protected void onAttachedToWindow() {\n        super.onAttachedToWindow();\n        update(0);",
            ),
            "org/telegram/messenger/ImageLocation.java": (
                "if (imageLocation.location.dc_id == 0) {\n                imageLocation.location.dc_id = dc_id;",
            ),
            "org/telegram/ui/Components/RecyclerListView.java": (
                "private int getCurrentChildPosition(View child)",
                "int position = getCurrentChildPosition(currentChildView);",
                "if (position == NO_POSITION)",
            ),
            "org/telegram/tgnet/NativeByteBuffer.java": (
                "private static final int MAX_POOLED_WRAPPERS = 64;",
                "if (queue.size() < MAX_POOLED_WRAPPERS)",
            ),
            "org/telegram/ui/Components/EditCoverButton.java": (
                "if (frame == null) {\n                AndroidUtilities.runOnUIThread(() -> setImage((Bitmap) null));",
            ),
            "org/telegram/ui/Components/ShareAlert.java": (
                "return y >= scrollOffsetY + dp(darkTheme && linkToCopy[1] != null ? 111 : 58);",
            ),
            "org/telegram/messenger/MessagesController.java": (
                "if (!cache && ChatObject.isCommunity(adminsChat) && !ChatObject.hasAdminRights(adminsChat))",
            ),
            "org/telegram/ui/ProfileActivity.java": (
                "if (!animated || hasColorById != wasHasColorById)",
                "hasColorAnimated.set(hasColorById ? 1f : 0f, true);",
            ),
        }
        for path, expressions in checks.items():
            with self.subTest(path=path):
                body = (JAVA / path).read_text()
                for expression in expressions:
                    self.assertIn(expression, body)
        share = (JAVA / "org/telegram/ui/Components/ShareAlert.java").read_text()
        self.assertEqual(2, share.count("return y >= scrollOffsetY + dp(darkTheme && linkToCopy[1] != null ? 111 : 58);"))
        inventory = {row["patch"]: row["status"] for row in json.loads(
            (ROOT / "docs/source-inventory.json").read_text())["inugram_md3"]}
        for name in ("top-peers-unread-counter", "profile-photo-location-dc-id",
                     "recycler-stale-longpress-position", "native-byte-buffer-pool",
                     "edit-cover-null-frame", "share-alert-search-dead-zone",
                     "community-admin-list-spam", "profile-black-bar"):
            self.assertTrue(inventory[f"bugfix/{name}.patch"].startswith("adapted:"), name)

    def test_upstream_license_not_removed(self):
        for name in ("LICENSE-Inugram", "LICENSE-NagramXF"):
            self.assertGreater((ROOT / "docs/upstream" / name).stat().st_size, 100)
        self.assertTrue((ROOT / "LICENSE").exists())

    def test_preflight_reports_missing_credentials_and_native_sources(self):
        with tempfile.TemporaryDirectory() as directory:
            root = Path(directory)
            (root / ".gitmodules").write_text('[submodule "codec"]\npath = native/codec\n')
            errors = preflight.check(root, {})
            self.assertTrue(any("TELEGRAM_APP_ID" in e for e in errors))
            self.assertTrue(any("google-services.json" in e for e in errors))
            self.assertTrue(any("native/codec" in e for e in errors))

    def test_preflight_rejects_upstream_firebase_package(self):
        with tempfile.TemporaryDirectory() as directory:
            root = Path(directory)
            (root / "TMessagesProj").mkdir()
            firebase = {"client": [{"client_info": {"android_client_info": {"package_name": "com.exteraless.app"}}}]}
            (root / "TMessagesProj/google-services.json").write_text(json.dumps(firebase))
            errors = preflight.check(root, {})
            self.assertTrue(any("Firebase config must contain" in e for e in errors))

    def test_preflight_accepts_environment_credentials_without_local_properties(self):
        with tempfile.TemporaryDirectory() as directory:
            root = Path(directory)
            errors = preflight.check(root, {
                "TELEGRAM_APP_ID": "12345", "TELEGRAM_APP_HASH": "a" * 32,
                "NATIVE_TARGET": "SKIP",
            })
            self.assertFalse(any("TELEGRAM_APP" in e for e in errors))

    def test_preflight_handles_invalid_encoded_properties(self):
        with tempfile.TemporaryDirectory() as directory:
            errors = preflight.check(Path(directory), {"LOCAL_PROPERTIES": "!invalid!"})
            self.assertTrue(any("base64" in e for e in errors))

    def prepared_root(self, directory, cmake="3.31.6", python=None):
        """Minimal SDK/app layout so the only remaining complaints are the ones under test."""
        root = Path(directory)
        sdk = root / "sdk"
        for component in ("build-tools/36.0.0", "ndk/27.2.12479018", "platforms/android-37.0"):
            (sdk / component).mkdir(parents=True, exist_ok=True)
        if cmake:
            (sdk / "cmake" / cmake).mkdir(parents=True, exist_ok=True)
            (sdk / "cmake" / cmake / "source.properties").write_text(f"Pkg.Revision = {cmake}\n")
        (root / "TMessagesProj").mkdir(parents=True, exist_ok=True)
        wanted = python or f"{sys.version_info.major}.{sys.version_info.minor}"
        (root / "TMessagesProj/build.gradle").write_text(
            f"python {{\n    version '{wanted}'\n}}\nexternalNativeBuild {{ cmake {{\n"
            "    version = '3.31.6+'\n}} } }\n")
        return root

    def env_without_path_problems(self, root, **extra):
        env = {"TELEGRAM_APP_ID": "12345", "TELEGRAM_APP_HASH": "a" * 32,
               "ANDROID_HOME": str(root / "sdk"),
               "CHAQUOPY_BUILD_PYTHON": sys.executable, "PATH": os.environ["PATH"]}
        env.update(extra)
        return env

    def test_preflight_accepts_a_complete_native_setup(self):
        with tempfile.TemporaryDirectory() as directory:
            root = self.prepared_root(directory)
            (root / "TMessagesProj/google-services.json").write_text(json.dumps(
                {"client": [{"client_info": {"android_client_info": {"package_name": "app.regram.android"}}}]}))
            (root / ".gitmodules").write_text("")
            errors = preflight.check(root, self.env_without_path_problems(root))
            self.assertEqual([], errors)

    def test_preflight_rejects_stale_cmake_and_wrong_build_python(self):
        with tempfile.TemporaryDirectory() as directory:
            root = self.prepared_root(directory, cmake="3.22.1", python="2.7")
            errors = preflight.check(root, self.env_without_path_problems(root))
            self.assertTrue(any("CMake" in e for e in errors), errors)
            self.assertTrue(any("build Python 2.7" in e for e in errors), errors)

    def test_preflight_skips_cmake_when_native_build_is_skipped(self):
        with tempfile.TemporaryDirectory() as directory:
            root = self.prepared_root(directory, cmake=None)
            errors = preflight.check(root, self.env_without_path_problems(root, NATIVE_TARGET="SKIP"))
            self.assertFalse(any("CMake" in e for e in errors), errors)

    def test_release_preflight_rejects_placeholders_and_missing_signing(self):
        with tempfile.TemporaryDirectory() as directory:
            root = self.prepared_root(directory)
            (root / "TMessagesProj/google-services.json").write_text(json.dumps({
                "project_info": {"project_id": "regram-compile-check"},
                "client": [{"client_info": {"android_client_info": {"package_name": "app.regram.android"},
                                            "mobilesdk_app_id": "1:000000000000:android:fake"},
                            "api_key": [{"current_key": "AIzaSyPLACEHOLDER"}]}],
            }))
            errors = preflight.check_release(root, {"TELEGRAM_APP_ID": "9999999", "TELEGRAM_APP_HASH": "0" * 32,
                                                    "REGRAM_COMPILE_CHECK": "1"})
            self.assertTrue(any("Compile-only Telegram" in e for e in errors), errors)
            self.assertTrue(any("Firebase" in e for e in errors), errors)
            self.assertTrue(any("keystore" in e for e in errors), errors)
            self.assertTrue(any("REGRAM_COMPILE_CHECK" in e for e in errors), errors)

    def test_release_preflight_accepts_configured_candidate_and_keeps_secrets_private(self):
        with tempfile.TemporaryDirectory() as directory:
            root = self.prepared_root(directory)
            (root / "TMessagesProj/release.keystore").write_bytes(b"example only")
            (root / "TMessagesProj/google-services.json").write_text(json.dumps({
                "project_info": {"project_id": "my-regram"},
                "client": [{"client_info": {"android_client_info": {"package_name": "app.regram.android"},
                                            "mobilesdk_app_id": "1:123456789012:android:abcdef"},
                            "api_key": [{"current_key": "AIzaSyRealExampleKey"}]}],
            }))
            env = {"TELEGRAM_APP_ID": "12345", "TELEGRAM_APP_HASH": "a" * 32,
                   "KEYSTORE_PASS": "private-store", "ALIAS_NAME": "release", "ALIAS_PASS": "private-alias"}
            self.assertEqual([], preflight.check_release(root, env))
            env["TELEGRAM_APP_HASH"] = "0" * 32
            errors = preflight.check_release(root, env)
            self.assertTrue(errors)
            self.assertNotIn("private-store", str(errors))
            self.assertNotIn("private-alias", str(errors))

    def test_firebase_and_keystore_cannot_be_committed_accidentally(self):
        ignored = (ROOT / ".gitignore").read_text()
        self.assertIn("TMessagesProj/google-services.json", ignored)
        self.assertIn("TMessagesProj/release.keystore", ignored)

    def test_placeholder_script_never_shadows_real_firebase_or_overwrites_existing_debug_config(self):
        with tempfile.TemporaryDirectory() as directory:
            root = Path(directory)
            app = root / "TMessagesProj"
            app.mkdir()
            (app / "google-services.json").write_text("real config")
            with self.assertRaises(FileExistsError):
                firebase_placeholder.create_placeholder(root)
            (app / "google-services.json").unlink()
            path = firebase_placeholder.create_placeholder(root)
            self.assertTrue(path.exists())
            with self.assertRaises(FileExistsError):
                firebase_placeholder.create_placeholder(root)

    def test_placeholder_firebase_is_a_note_not_a_blocker(self):
        with tempfile.TemporaryDirectory() as directory:
            root = self.prepared_root(directory)
            debug = root / "TMessagesProj/src/debug"
            debug.mkdir(parents=True)
            (debug / "google-services.json").write_text(json.dumps({"client": []}))
            (root / ".gitmodules").write_text("")
            env = self.env_without_path_problems(root)
            errors = preflight.check(root, env)
            self.assertFalse(any("google-services" in e for e in errors), errors)
            self.assertTrue(any("placeholder" in n for n in preflight.notes(root, env)))


if __name__ == "__main__":
    unittest.main()
