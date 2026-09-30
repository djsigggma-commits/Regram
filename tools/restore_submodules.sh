#!/usr/bin/env bash
# Восстанавливает отсутствующие подмодули re:gram по ревизиям, закреплённым в
# upstream-теге exteraless v12.10.5-beta13.1 (gitlink SHA получены через GitHub
# git trees API). Каталог проекта не является git-репозиторием, поэтому
# используется git init + fetch по SHA вместо `git submodule update`.
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
    echo "SKIP  $path (не пустой)"
    return 0
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
fetch_one TMessagesProj_Modules/media        https://github.com/Arseny271/media.git            c430d207677071b1873f9f18266d55ec45722180 || failures=$((failures + 1))
fetch_one TMessagesProj/lib/jlatexmath       https://github.com/dkaraush/jlatexmath-android.git f59d6eecfac6fb58d6e3b7d6cff6c8aed9dbd5b4 || failures=$((failures + 1))
fetch_one TMessagesProj/jni/tlottie          https://github.com/dkaraush/tlottie.git            92df98dc209bc39b1e567ec74a8c86a0af5239de || failures=$((failures + 1))
fetch_one TMessagesProj/jni/third_party/absl https://github.com/abseil/abseil-cpp.git           54fac219c4ef0bc379dfffb0b8098725d77ac81b || failures=$((failures + 1))
fetch_one TMessagesProj/jni/third_party/wamr https://github.com/wasm-micro-runtime/wasm-micro-runtime.git 25bd7eb63e828e4bd242cc9b38d260b4b31c6605 || failures=$((failures + 1))
fetch_one TMessagesProj/jni/third_party/dav1d https://github.com/videolan/dav1d.git             54706fc6bc0cdecab7e9593974a4039cc038fca7 || failures=$((failures + 1))
fetch_one TMessagesProj/jni/third_party/xiph/ogg https://github.com/xiph/ogg.git                be05b13e98b048f0b5a0f5fa8ce514d56db5f822 || failures=$((failures + 1))
fetch_one TMessagesProj/jni/third_party/xiph/opus https://github.com/xiph/opus.git              22244de5a79bd1d6d623c32e72bf1954b56235be || failures=$((failures + 1))
fetch_one TMessagesProj/jni/third_party/xiph/opusfile https://github.com/xiph/opusfile.git      a55c164e9891a9326188b7d4d216ec9a88373739 || failures=$((failures + 1))
fetch_one TMessagesProj/jni/third_party/libvpx https://github.com/webmproject/libvpx.git        1024874c5919305883187e2953de8fcb4c3d7fa6 || failures=$((failures + 1))
fetch_one TMessagesProj/jni/third_party/ffmpeg https://github.com/FFmpeg/FFmpeg.git             45f1910444f34b02621f9f0426ea1a538a613c41 || failures=$((failures + 1))
if (( failures )); then
  echo "Не удалось восстановить подмодули: $failures" >&2
  exit 1
fi
