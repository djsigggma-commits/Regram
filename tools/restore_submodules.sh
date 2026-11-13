#!/usr/bin/env bash
# Восстанавливает отсутствующие подмодули re:gram по ревизиям, закреплённым в
# upstream-коммите exteraless 31a35e46 (Actions 43392419710, 13.2.2).
# Работает также с архивом исходников без .git: git init + fetch по SHA.
set -uo pipefail

ROOT="${1:-.}"
cd "$ROOT" || exit 1

fetch_one() {
  local path="$1" url="$2" sha="$3"
  if [ -e "$path/.git" ]; then
    local actual
    actual="$(git -C "$path" rev-parse HEAD 2>/dev/null)" || return 1
    if [ "$actual" != "$sha" ]; then
      echo "FAIL  $path: ревизия $actual, ожидалась $sha" >&2
      return 1
    fi
    echo "SKIP  $path (закреплённая ревизия $sha)"
    return 0
  fi
  if [ -d "$path" ] && [ -n "$(ls -A "$path" 2>/dev/null)" ]; then
    echo "FAIL  $path: непустой каталог без .git; закреплённая ревизия не проверена. Сохраните его отдельно и восстановите подмодуль в пустой каталог." >&2
    return 1
  fi
  echo "FETCH $path <- $url @ $sha"
  mkdir -p "$path" || return 1
  git -C "$path" init -q || return 1
  git -C "$path" remote add origin "$url" 2>/dev/null
  if git -C "$path" fetch -q --depth 1 origin "$sha"; then
    git -C "$path" checkout -q FETCH_HEAD || return 1
  else
    echo "FAIL  $path: fetch by SHA не удался, пробую полную историю"
    git -C "$path" fetch -q origin || return 1
    git -C "$path" checkout -q "$sha" || return 1
  fi
  echo "OK    $path $(git -C "$path" rev-parse HEAD)"
}

failures=0
fetch_one TMessagesProj/jni/twidget/twidget https://github.com/TelegramMessenger/twidget.git 4769d72c5c5a9bace3c7bd8b6308cb79bc150bab || failures=$((failures + 1))
fetch_one TMessagesProj/jni/twidget/widget-sandbox https://github.com/TelegramMessenger/widget-sandbox.git c498cefddefaba765282c9f3ab0b330f4bc990f6 || failures=$((failures + 1))
fetch_one TMessagesProj/jni/tgcalls https://github.com/TelegramMessenger/tgcalls.git 545f5ae599f8282e1ae3f38aaff166d179d75703 || failures=$((failures + 1))
fetch_one TMessagesProj/jni/wallet-engine https://github.com/dkaraush/wallet-engine.git 5c65e47cb8d68727c20d89382d0bc51292486b9a || failures=$((failures + 1))
fetch_one TMessagesProj/jni/third_party/boost-regex https://github.com/boostorg/regex.git 4cbcd3078e6ae10d05124379623a1bf03fcb9350 || failures=$((failures + 1))
fetch_one TMessagesProj/jni/third_party/libprisma https://github.com/TelegramMessenger/libprisma.git 94640c63c20eacdf70e37e23198d9ab53c802ebd || failures=$((failures + 1))
fetch_one TMessagesProj/chaquopy            https://github.com/exteraless/chaquopy.git         eab341a401913ac50254ff20e9efeebeaae872ef || failures=$((failures + 1))
fetch_one TMessagesProj_Modules/media        https://github.com/Arseny271/media.git            09b6c97c0d691c50cd4541e1573995d4066d6f5c || failures=$((failures + 1))
fetch_one TMessagesProj/lib/jlatexmath       https://github.com/dkaraush/jlatexmath-android.git f59d6eecfac6fb58d6e3b7d6cff6c8aed9dbd5b4 || failures=$((failures + 1))
fetch_one TMessagesProj/jni/tlottie          https://github.com/dkaraush/tlottie.git            31f1b542f88e7b4be9a01e749920d857535fc715 || failures=$((failures + 1))
fetch_one TMessagesProj/jni/third_party/absl https://github.com/abseil/abseil-cpp.git           54fac219c4ef0bc379dfffb0b8098725d77ac81b || failures=$((failures + 1))
fetch_one TMessagesProj/jni/third_party/wamr https://github.com/wasm-micro-runtime/wasm-micro-runtime.git 25bd7eb63e828e4bd242cc9b38d260b4b31c6605 || failures=$((failures + 1))
fetch_one TMessagesProj/jni/third_party/dav1d https://github.com/videolan/dav1d.git             54706fc6bc0cdecab7e9593974a4039cc038fca7 || failures=$((failures + 1))
fetch_one TMessagesProj/jni/third_party/xiph/ogg https://github.com/xiph/ogg.git                be05b13e98b048f0b5a0f5fa8ce514d56db5f822 || failures=$((failures + 1))
fetch_one TMessagesProj/jni/third_party/xiph/opus https://github.com/xiph/opus.git              22244de5a79bd1d6d623c32e72bf1954b56235be || failures=$((failures + 1))
fetch_one TMessagesProj/jni/third_party/xiph/opusfile https://github.com/xiph/opusfile.git      a55c164e9891a9326188b7d4d216ec9a88373739 || failures=$((failures + 1))
fetch_one TMessagesProj/jni/third_party/libvpx https://github.com/webmproject/libvpx.git        1024874c5919305883187e2953de8fcb4c3d7fa6 || failures=$((failures + 1))
fetch_one TMessagesProj/jni/third_party/ffmpeg https://github.com/FFmpeg/FFmpeg.git             45f1910444f34b02621f9f0426ea1a538a613c41 || failures=$((failures + 1))
# QuickJS is a nested gitlink pinned by widget-sandbox and required by twidget.cmake.
if [ -e TMessagesProj/jni/twidget/widget-sandbox/.git ]; then
  git -C TMessagesProj/jni/twidget/widget-sandbox submodule update --init --depth 1 -- \
    packages/ios/Sources/CQuickJS/quickjs || failures=$((failures + 1))
fi
if (( failures )); then
  echo "Не удалось восстановить подмодули: $failures" >&2
  exit 1
fi
