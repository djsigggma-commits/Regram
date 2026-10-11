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

## Follow-up pass (Last.fm memory and request queue)

- Recent-track entries now have a 128-profile LRU cap; expired entries are evicted on access. The separate timestamp map was removed so metadata cannot outlive its track.
- Last.fm WebView requests use a deque instead of shifting an array on every completion; the idle-release timer restarts after the last request, even when a batch took longer than the previous timer.
- The HTTP HTML reader no longer reads beyond its 24 KiB prefix limit on the final chunk.

Validation in this checkout: `python3 -m pytest -q tests/test_nowplaying_debloat.py tests/test_performance_debloat.py tests/test_regressions.py` (21 passed, 1 skipped). Full suite: 161 passed, 32 skipped, 26 subtests passed; three pre-existing failures (missing alias targets, branding overlays, RecyclerListView invariant). Android compilation could not start: `TMessagesProj_Modules/media/core_settings.gradle` is missing (uninitialized submodule). No APK was produced or device performance measured.

## Pass after the 12.10.6-beta14 update

Changes in the current 1.0.2 source tree:

- The upstream cached-proxy-ping controller no longer schedules ten-second wakeups while the app is backgrounded or there is no active proxy. Foreground and proxy-setting events restart it; repeated initialization cannot add duplicate listeners. The separate opt-in active-probe controller retains its timeout, stale-result guards and existing settings.
- Ordinary builds no longer allocate `HookStats` counters for every registered Python hook/replacement. Bindings without a profiling ID skip the diagnostic import and extra Python dispatch frame, while retaining parameter conversion, filters, error handling, permissions and watchdogs. Explicit `-PdebugTools=true` builds keep profiling support.
- Online lyrics fetches for the same track share one in-flight job. Subscriber lists are removed before notification; a failing subscriber does not block the others. Negative lookups use a 128-entry cache and expire after ten minutes. Network/disk lyrics reads reject payloads exceeding 2 MiB instead of growing an unbounded buffer. Explicit fetches can still retry missing lyrics.
- Cover colour requests are grouped by key instead of repeatedly scanning/removing a shared list. Quantization runs on a dedicated serial executor, not Telegram's shared queue. Owned sample bitmaps and temporary hardware-to-software copies are explicitly recycled; the image loader's original bitmap is never recycled here.
- Immutable MD3 player palettes use a 32-entry seed/dark-mode cache. Colour interpolation reuses the two input arrays instead of allocating copies every frame. The path cache for player icons is bounded to 64 entries.
- Removed the unreachable startup debug runnable, unused private legacy AI setter (the migration reader remains), obsolete unused location-picker method in pill settings, associated imports, and a duplicated keep rule. The working weather settings screen remains intact.

No messages, account data, features, bundled plugins, themes, licenses, JNI APIs or reflection-accessible plugin classes were deleted. Broad R8 keep rules and `-dontoptimize` were not relaxed blindly: Xposed hooks, Python and JNI access members which appear unreachable to a static shrinker. Release builds already enable code/resource shrinking.

Validation: **239 passed, 33 skipped, 27 subtests passed** (`python3 -m pytest tests -q`). New tests include both direct-Java and wrapped Python hook-parameter paths. Java/Kotlin syntax and Python AST checks pass. APK compilation and device performance measurements were not performed: the SDK, build Python 3.11, credentials/configuration and submodule contents are still unavailable. The older compilation/APK results above describe earlier checkouts, not this update. A subsequent signed arm64 release build including this pass and call/startup fixes succeeded; see [BUILD_RELEASE_1.0.2.md](BUILD_RELEASE_1.0.2.md). This does not constitute an on-device performance test.

Backup before this pass: `../Regram-before-optimization.tar.gz`. For further optimization, capture Perfetto CPU/frame/memory traces with the actual phone, enabled plugins and exact stutter scenario; source checks do not establish an FPS or battery improvement.

## Additional bounded-work / debloat pass

- APK and Vosk model downloads share a latest-value progress dispatcher. At most one delivery is queued; updates are delayed by 100 ms. Completion flushes the latest value and releases the listener, so already queued callbacks cannot touch a completed download. Unknown content lengths also receive byte progress.
- Vosk settings no longer recursively scan model directories during RecyclerView binding. Sizes are loaded on a dedicated serial executor, cached for the screen lifetime and invalidated on delete/install. Generation checks ignore results from destroyed/rebuilt screens. Size queries do not queue behind downloads or occupy Telegram's global queue.
- Vosk extraction checks cancellation inside large ZIP entries, not just between files. Download state stays active until UI completion, preventing a new download from inheriting old progress callbacks.
- The player ring uses a native arc when flat. Animated rings reuse precomputed circle/wave samples and the sine angle-difference identity; only phase and the fractional endpoint require per-frame trigonometry. Hidden windows do not schedule subsequent frames. Geometry checks preserve the old wave shape to float precision.
- Recently Online subscribes to status updates only while resumed; pausing cancels the pending list rebuild. Resume refreshes current data.
- Removed unused private tab-position arrays, maximum-text-width calculations and an unused logging import. Removed the commented-out legacy layout implementation while retaining the public compatibility method.

No account data, bundled plugins, dependencies, JNI/reflection APIs, licenses or user-facing features were deleted. Existing release shrinking and plugin compatibility rules remain unchanged. This is a source-level optimization pass, not a claim of maximum speed or measured FPS/battery gains.

Validation: `python3 -m pytest tests -q`: **268 passed, 39 skipped, 27 subtests passed**. New executable Java checks cover 100,000 coalesced updates, concurrent producers, reentrant callbacks, completion and wave geometry. Offline `:TMessagesProj:compileDebugJavaWithJavac` succeeded through `tools/build.sh`, with `NATIVE_TARGET=SKIP`, Python 3.11 and a 10 GiB Gradle heap. This checks Java/Kotlin compilation and resources; native compilation, APK assembly and on-device performance were not tested in this pass.

Backup before this pass (includes the earlier folder-position changes): `../Regram-before-debloat-pass.tar.gz`.

## Release 1.1.6.1 follow-up

Bounded quick-theme color caching, recurrence-based M3/player waves, reused player ripples, hidden-view animation gating, a 2–8 MiB custom-icon cache and capped dedicated-queue prewarm, and removal of a no-op camera handler. Four-part version numbering/update ordering is supported. Features, plugins, JNI/reflection contracts and shared story thumbnails are preserved. See [BUILD_RELEASE_1.1.6.1.md](BUILD_RELEASE_1.1.6.1.md) for scope, regression checks, release verification and remaining device tests.
