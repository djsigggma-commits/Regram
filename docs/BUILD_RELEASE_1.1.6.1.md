# re:gram 1.1.6.1 — bounded rendering work / safe debloat

This pass preserves account data, existing preferences, bundled plugins, themes, camera/story thumbnails, maps, calls, Glyph support and JNI/reflection APIs. It is not a claim of maximum speed or measured FPS/battery gains. Release code/resource shrinking remains enabled; compatibility keep rules and `-dontoptimize` were not blindly relaxed.

## Changes

- Quick-theme palette transforms have a fixed 64-slot, per-thread primitive cache (under 1 KiB per participating thread, no per-hit boxing/locks/allocations). The complete source ARGB and clamped contrast/saturation/brightness parameters form the cache key; theme animation, local providers and setting changes cannot reuse an incorrect result. Neutral palette settings bypass the nested resolution guard, including transparency-only customization. Original theme files and media are not modified.
- M3 circular progress and the player seek bar reuse an allocation-free sine oscillator. Evenly spaced samples use a recurrence instead of a sine call per point. The slider's fractional endpoint retains its direct sine calculation. Flat sliders use a native line, not a tessellated wave path. Settled amplitude skips the exponential smoothing calculation.
- Player header ripple drawables are created once, then recolored only when the ripple color actually changes. Identical cached palettes do not start another 600 ms transition; completed color animators release their reference. Unchanged button/badge colors do not request another redraw.
- Player slider and morph-button self-scheduled frames stop when the view/window is hidden. Visibility changes reset frame timing and resume pending animation on the next draw. Playback, seeking and accessibility remain unchanged.
- Custom-icon reload/prewarm uses a dedicated serial executor, not Telegram's shared `globalQueue`. Prewarm considers at most 64 icons; remaining icons are still loaded lazily. No artificial sleeps in the shared queue remain. The bitmap cache has a 2–8 MiB budget, with allocation bytes rounded up to at least 1 KiB (tiny icons cannot have zero weight). Generation guards reject stale missing flags/decoded bitmaps after pack changes. A prewarm losing a race to a foreground decode recycles only its unpublished bitmap, never a displayed/cached bitmap.
- Removed the empty private attachment-camera edit handler, two redundant overrides and commented legacy implementation. Attachment hide invalidates the artwork once, not once per visible photo. Shared `cthumb.jpg` persistence is retained because the story recorder still uses it.
- Release numbering and the GitHub updater now support four numeric parts. `versionName=1.1.6.1`, `versionCode=1010601`. The code scheme is now `major*1000000 + minor*10000 + patch*100 + revision`, including future two-/three-part releases. It upgrades the shipped 1.1 APK (10100); revision-only updates are correctly ordered. Invalid/overflowing tags do not crash version parsing. See [RELEASE.md](RELEASE.md).

## Validation

- Regression tests execute the real Java color policy (randomized cache hits/collisions, parameter changes and independent thread caches), real sine recurrence and real four-part version parser/comparator.
- Geometry checks compare short circular paths to direct double-precision sine and a deliberately wide float-based slider to its previous shape within 0.01 px. These are numerical checks, not GPU/device benchmarks.
- Source guards cover bounded icon work, generation checks, ripple reuse, visibility gating and retained story thumbnails.
- Full suite with JDK 21 available: **307 passed, 31 skipped, 27 subtests passed**. Log: `/tmp/regram-1161-final-tests.log`.
- Signed `:TMessagesProj:assembleRelease`, `NATIVE_TARGET=arm64-v8a`, Python 3.11 and a 10 GiB Gradle heap: **BUILD SUCCESSFUL in 6m 11s**. Log: `/tmp/regram-release-1.1.6.1.log`.
- `tools/verify_release_apk.py --abi arm64-v8a` passed: Android identity, non-debuggable manifest, Python engine, post-R8 FFmpeg JNI declarations, native ABI and APK signature. Manifest version was independently checked as **1.1.6.1 (1010601)**. The signing certificate SHA-256 digest matches the shipped 1.1 APK, so Android update identity is preserved.
- APK: `artifacts/regram-v1.1.6.1(1010601)-release-arm64-v8a.apk` (**77,461,322 bytes**). Matching `.sha256` file is alongside it; checksum verification passed after copying.
- SHA-256: `bc2cc73237245950c66557ec4d1378b68de4d65de4a8713674ed03b7a4c51f85`.
- GitHub release: [v1.1.6.1](https://github.com/djsigggma-commits/Regram/releases/tag/v1.1.6.1), with `regram-v1.1.6.1-arm64-v8a.apk` and matching `.sha256`. Public release visibility, GitHub's APK SHA-256 digest and downloadable checksum were verified. Initially published as stable/APK-only; subsequently requested as a **pre-release with source publication**. The source snapshot includes all changes since the repository's 1.0.9 commit, with signing credentials, Firebase configuration, device logs and generated APK/build artifacts excluded.
- The user subsequently confirmed excellent operation on their device. No independent on-device profiling or FPS/battery measurements were performed.

Source backup before this pass: `../Regram-before-1.1.6.1-debloat.tar.gz` (affected source directories, build version/configuration, tests and docs; no signing credentials).

## Building the published source snapshot

Initialize submodules before building; the Chaquopy runtime is pinned to `c217deac1c2df5ab879b9844d911e1c90e59a4c1`, not a floating branch. The local CMake 4 compatibility change used for the APK is preserved as a patch rather than silently losing a dirty submodule change:

```sh
git submodule update --init --recursive
git -C TMessagesProj/chaquopy apply ../../tools/patches/chaquopy-cmake4.patch
```

Apply the patch once to a clean submodule (the original local build tree already has it). Then provide your own private API/Firebase/signing configuration as described in [RELEASE.md](RELEASE.md) and run the normal arm64 release build. No submodule history is rewritten and no credentials are published.

## Device checks still needed

Install as an update over 1.1. Check launch, scrolling, night/local chat themes, quick-theme reset and transparency, custom icon switching, player seek/pause/resume and background/foreground, attachment camera and stories, calls, Glyph and Python plugins. Use Perfetto traces of the actual device/workload to decide further optimizations. No dependencies, languages, user-facing features or plugin bundles were removed solely to reduce APK size.
