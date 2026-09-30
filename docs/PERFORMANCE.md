# Debloat / performance pass

This is a targeted pass, not a claim that all device-specific jank is fixed. No user data, plugin bundles, themes, or media were removed.

- `debugpy` is no longer shipped by default in Chaquopy requirements. Build with `REGRAM_PY_DEBUGGER=1` only if the Python developer debugger is needed; the dev server reports the missing optional module otherwise. The debug APK's `requirements-common.imy` is 5,088,373 bytes and contains no debugpy module.
- Live proxy ping stops in the background and resumes on foreground; disabled-by-default behavior is unchanged. Outstanding callbacks are invalidated on pause.
- Pill auto-refresh is suspended while detached, hidden, or in the background. Telemetry pills keep at most one measurement queued each, so a busy `globalQueue` cannot accumulate repeated CPU/RAM/network samples.
- exitFy Lite checks at most four proxies concurrently (up to 200 candidates), abandons a batch after 45 seconds, and does not overwrite a proxy selected by the user while probing.
- `versionCode` advanced from 1261 to 1262. Original pinned plugin payloads are unchanged.

Validation: `python3 -m pytest -q tests` (145 passed, 32 skipped, 27 subtests); offline Gradle `:TMessagesProj:assembleDebug` for `arm64-v8a` succeeded with `-Dorg.gradle.jvmargs=-Xmx10g`. Build from a copy outside the original `re:gram` path: Chaquopy cannot create a venv when the path contains `:`. D8 ran out of heap at the default `-Xmx3g`, so the APK build used 10 GiB.

APK: `~/projects/regram/TMessagesProj/build/outputs/apk/debug/regram-v12.10.3(1262)-debug.apk` (103,331,614 bytes; SHA-256 `18f5f19f98fad2adb61d1da08dfa0e8f04a63270c2a99bbe00a50ea3d8649e28`). The debug APK is **not a release**: it is debuggable and signed by the local debug key. A release/update APK requires the owner's original release keystore and its credentials; do not replace an existing production install with a differently signed build. On-device performance and the third-party DEX plugins still require testing.

If a device remains slow: temporarily disable Custom Profile and exitFy separately in the plugin manager (without deleting their settings), then capture a Perfetto / Android Studio CPU trace during the exact stutter. Closed third-party DEX can do work outside the Java paths changed here; without a trace it cannot be safely optimized by removing unrelated features.

## Follow-up pass (supporter badges and recommended plugins)

- The supporter-list HTTP request now runs on a dedicated single-thread executor instead of Telegram's shared `globalQueue`. Animated supporter emoji listeners are detached before profile/chat titles replace the badge on refresh, including chat-to-profile transitions; this prevents retained listeners and duplicate animation work. The network list is still cached for six hours.
- Large recommended-plugin file copies run on a dedicated executor rather than starving shared telemetry and other jobs. Installation traverses the batch by index instead of repeatedly shifting an `ArrayList`; pending messages are released when finished. No plugins or their settings are removed.
- Updated the obsolete Custom Profile test to match the already removed additional-features shortcut. Previously installed plugins are unaffected.

Validation: `python3 -m pytest -q tests` (157 passed, 32 skipped, 27 subtests). `:TMessagesProj:compileDebugJavaWithJavac` passed from the colon-free build copy with 10 GiB heap; this is a compilation check **not** a debug APK to distribute. A **release APK was not produced**: `assembleRelease` fails at the signing preflight because `TMessagesProj/release.keystore`, `KEYSTORE_PASS`, `ALIAS_NAME`, and `ALIAS_PASS` are absent. Never ship a debug-signed or unsigned build in its place. Re-run release verification from `docs/RELEASE.md` after the owner provides the matching signing key and credentials privately. Device-specific stutter still requires a trace.
