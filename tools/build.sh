#!/usr/bin/env bash
# Собирает re:gram вне исходного каталога.
#
# Причина: двоеточие в пути «Рабочий стол/re:gram». Оно ломает две независимые
# вещи: загрузку jar-а Gradle Wrapper (classpath делится символом «:») и создание
# venv плагина Chaquopy («Refusing to create a venv ... contains the PATH
# separator»). Wrapper обходится запуском gradle из дистрибутива (GRADLE_BIN),
# но venv Chaquopy работает только в пути без «:» — поэтому и нужна копия.
#
# Исходный каталог остаётся источником прав: сюда копируется только дерево
# исходников, все сборки и кеши живут в репозитории сборки.
set -euo pipefail

SRC="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
BUILD_DIR="${REGRAM_BUILD_DIR:-$HOME/projects/regram}"
GRADLE="${GRADLE_BIN:-$HOME/.local/share/gradle-dist/gradle-9.7.1/bin/gradle}"
WANTED_GRADLE="$(sed -n 's/^distributionUrl=.*gradle-\([0-9.]*\)-bin.zip$/\1/p' "$SRC/gradle/wrapper/gradle-wrapper.properties" 2>/dev/null || true)"

# Двоеточие в исходном пути допустимо (его и обходят этой копией),
# а вот сборочный каталог обязан быть без него.
if [[ "$BUILD_DIR" == *":"* ]]; then
  echo "Сборочный путь содержит «:» — смените REGRAM_BUILD_DIR." >&2
  exit 1
fi
# rsync --delete работает только с отдельным каталогом-копией. Не даём
# случайно удалить исходники либо домашний/корневой каталог при опечатке.
BUILD_DIR="$(realpath -m "$BUILD_DIR")"
if [[ "$BUILD_DIR" == / || "$BUILD_DIR" == "$HOME" ||
      "$BUILD_DIR" == "$SRC" || "$BUILD_DIR" == "$SRC/"* ||
      "$SRC" == "$BUILD_DIR/"* ]]; then
  echo "REGRAM_BUILD_DIR должен быть отдельным каталогом вне дерева исходников и HOME: $BUILD_DIR" >&2
  exit 1
fi

# Задаются в окружении или в local.properties исходного каталога.
# REGRAM_COMPILE_CHECK=1 подставляет заведомо нерабочие Telegram API данные:
# этого хватает для компиляции, но вход в аккаунт таким APK невозможен.
if [[ "${REGRAM_COMPILE_CHECK:-0}" == "1" ]]; then
  export TELEGRAM_APP_ID="${TELEGRAM_APP_ID:-9999999}"
  export TELEGRAM_APP_HASH="${TELEGRAM_APP_HASH:-00000000000000000000000000000000}"
  echo "REGRAM_COMPILE_CHECK=1: используются плейсхолдеры Telegram API (только компиляция)."
fi

: "${JAVA_HOME:=$HOME/.gradle/jdks/jetbrains_s_r_o_-21-amd64-linux.2}"
: "${ANDROID_HOME:=$HOME/Android/Sdk}"
export JAVA_HOME ANDROID_HOME ANDROID_SDK_ROOT="$ANDROID_HOME"

if [[ ! -x "$GRADLE" ]]; then
  echo "Gradle не найден: $GRADLE" >&2
  echo "Скачайте дистрибутив версии из gradle/wrapper/gradle-wrapper.properties" >&2
  echo "и укажите путь через GRADLE_BIN (оболочка gradlew в этом пути не работает)." >&2
  exit 1
fi
if [[ -n "$WANTED_GRADLE" && "$GRADLE" != *"$WANTED_GRADLE"* ]]; then
  echo "Внимание: GRADLE_BIN не совпадает с gradle-wrapper.properties (нужна $WANTED_GRADLE)." >&2
fi

# Chaquopy требует интерпретатор той же минорной версии, что указана в
# TMessagesProj/build.gradle (python { version '3.11' }); автоматический поиск
# берёт python3 из PATH и падает, если там другая версия.
if [[ -z "${CHAQUOPY_BUILD_PYTHON:-}" ]]; then
  echo "CHAQUOPY_BUILD_PYTHON не задан: install*PythonRequirements упадёт с" >&2
  echo "«Couldn't find Python 3.11». Укажите путь к интерпретатору нужной" >&2
  echo "минорной версии (см. README, раздел «Подготовка сборки»)." >&2
fi

mkdir -p "$BUILD_DIR"
echo "== $SRC -> $BUILD_DIR"
rsync -a --delete \
  --exclude '.gradle/' --exclude 'build/' --exclude '.cxx/' \
  --exclude '.pytest_cache/' --exclude '__pycache__/' \
  --exclude '.git/' --exclude 'local.properties' \
  "$SRC/" "$BUILD_DIR/"
# local.properties копируется отдельно, чтобы --delete не трогал файл во время
# rsync; если исходный файл удалён, не используем старые credentials из копии.
if [[ -f "$SRC/local.properties" ]]; then
  cp "$SRC/local.properties" "$BUILD_DIR/local.properties"
  chmod 600 "$BUILD_DIR/local.properties"
else
  rm -f "$BUILD_DIR/local.properties"
fi

cd "$BUILD_DIR"
exec "$GRADLE" --no-daemon -p . "${@:-:TMessagesProj:compileDebugJavaWithJavac}"
