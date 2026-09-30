<div align="center">

<img src="assets/logo.png" width="128" alt="exteraless">

# exteraless

Открытая версия exteraGram — с моделью разрешений для плагинов

[![Канал](https://img.shields.io/badge/Telegram-@exteraless-2CA5E0?logo=telegram&logoColor=white)](https://t.me/exteraless)

</div>

---

## Что это

exteraless — форк [NagramX](https://github.com/risin42/NagramX), в котором возможности
exteraGram реализованы с открытым исходным кодом.

Отдельный упор — **безопасность плагинов**. В exteraGram плагин не ограничен ни в чём;
здесь у него есть модель разрешений: плагин объявляет, что ему нужно, это
видно на экране установки, и получает он только заявленное.

Проект **в бете**: часть возможностей exteraGram ещё не перенесена. Если заметили, что
каких-то функций не хватает, — откройте issue или напишите в [канал](https://t.me/exteraless?direct).

* Имя пакета: `com.exteraless.app`
* Что перенесено: экраны настроек, оформление и параметры чатов, иконпаки, полоса
  пилюль (Pill Stack), боковое меню, движок Python-плагинов

### Ссылки

* Канал: [@exteraless](https://t.me/exteraless)
* Исходники: [github.com/exteraless/exteraless](https://github.com/exteraless/exteraless)

### Сборка

1. Склонировать репозиторий вместе с подмодулями:

    ```bash
    git clone --recursive --shallow-submodules https://github.com/exteraless/exteraless.git exteraless
    ```

    Если репозиторий уже склонирован без подмодулей:

    ```bash
    git submodule update --init --recursive --depth=1
    ```

2. Получить `TELEGRAM_APP_ID` и `TELEGRAM_APP_HASH` на [my.telegram.org](https://my.telegram.org/auth)
   и создать `local.properties` в корне проекта:

   ```properties
   TELEGRAM_APP_ID=<ваш_app_id>
   TELEGRAM_APP_HASH=<ваш_app_hash>
   ```

3. Для подписи APK положить свой `TMessagesProj/release.keystore` и дописать в `local.properties`:

   ```properties
   KEYSTORE_PASS=<пароль_хранилища>
   ALIAS_NAME=<имя_ключа>
   ALIAS_PASS=<пароль_ключа>
   ```

   Ключа в репозитории нет намеренно. Без него сборка не падает — APK подписывается
   отладочным ключом Android.

4. Для push-уведомлений положить свой `TMessagesProj/google-services.json`
   (Firebase, имя пакета `com.exteraless.app`).

5. Заменить метаданные проекта:

    - ключ Google Maps в записи `com.google.android.maps.v2.API_KEY` в `TMessagesProj/src/main/AndroidManifest.xml`;
    - `BaseRemoteHelper.CHANNEL_METADATA_ID` — числовой id вашего канала метаданных, без префикса `-100`.

6. Собрать: `./gradlew :TMessagesProj:assembleDebug` или открыть проект в Android Studio.

**Про ABI.** Собираются только 64-битные `arm64-v8a` и `x86_64`: Chaquopy собирает
Python 3.12 лишь под них, и на `armeabi-v7a` конфигурация обрывается. Переменная
`NATIVE_TARGET` задаёт цель: `arm64-v8a` (один ABI, быстрее), `universal` (оба),
`SKIP` (без нативной части — только Java и ресурсы).

### Сборка через GitHub Actions

Нужны два секрета репозитория:

* `LOCAL_PROPERTIES` — содержимое `local.properties` в base64:

  ```bash
  base64 -w0 local.properties
  ```

* `RELEASE_KEYSTORE` — файл ключа в base64:

  ```bash
  base64 -w0 TMessagesProj/release.keystore
  ```

Дальше запустить workflow **Release Build**. Готовый APK лежит в артефактах прогона.

### Авторы дизайна

Иконки и оформление, унаследованные от exteraGram, созданы его дизайнером —
[@the8055u](https://t.me/the8055u) и студией [@BlueprintDsgn](https://t.me/BlueprintDsgn).
Права на эти материалы принадлежат авторам.

### Благодарности

- [AyuGram](https://github.com/AyuGram/AyuGram4A)
- [Cherrygram](https://github.com/arsLan4k1390/Cherrygram)
- [Dr4iv3rNope](https://github.com/Dr4iv3rNope/NotSoAndroidAyuGram)
- [exteraGram](https://github.com/exteraSquad/exteraGram)
- [Nagram](https://github.com/NextAlone/Nagram)
- [NagramX](https://github.com/risin42/NagramX)
- [Nekogram](https://github.com/Nekogram/Nekogram)
- [OctoGram](https://github.com/OctoGramApp/OctoGram)

---

## English

### What this is

exteraless is a fork of [NagramX](https://github.com/risin42/NagramX) that implements
exteraGram's features open-source.

The special focus is **plugin security**. In exteraGram a plugin is not restricted in
any way; here it comes with a permission model and isolation: a plugin declares what it
needs, you see that on the install sheet, and it gets only what was declared.

The project is **in beta**: some exteraGram features have not been ported yet. If you
notice a missing feature, open an issue or write to the [channel](https://t.me/exteraless?direct).

* Package name: `com.exteraless.app`

### Design credits

Icons and visual design inherited from exteraGram are the work of its designer,
[@the8055u](https://t.me/the8055u), and the [@BlueprintDsgn](https://t.me/BlueprintDsgn)
studio. Rights to those materials belong to their authors.

### Building

1. Clone with submodules:

    ```bash
    git clone --recursive --shallow-submodules https://github.com/exteraless/exteraless.git exteraless
    ```

2. Get `TELEGRAM_APP_ID` and `TELEGRAM_APP_HASH` from [my.telegram.org](https://my.telegram.org/auth)
   and put them into `local.properties` in the project root.

3. For release signing, drop your own `TMessagesProj/release.keystore` and add
   `KEYSTORE_PASS`, `ALIAS_NAME`, `ALIAS_PASS` to `local.properties`. No keystore is
   shipped with the repository; without one the build is signed with the Android debug key.

4. For push notifications, replace `TMessagesProj/google-services.json` with your own
   Firebase config for `com.exteraless.app`.

5. Build with `./gradlew :TMessagesProj:assembleDebug` or from Android Studio.

Only 64-bit ABIs are built (`arm64-v8a`, `x86_64`): Chaquopy ships Python 3.12 for those
only, and `armeabi-v7a` fails at configuration time. `NATIVE_TARGET` selects the target:
`arm64-v8a`, `universal` (both) or `SKIP` (no native part).

For CI, set two repository secrets — `LOCAL_PROPERTIES` and `RELEASE_KEYSTORE`, both
base64-encoded — and run the **Release Build** workflow. The APK ends up in the run
artifacts.
