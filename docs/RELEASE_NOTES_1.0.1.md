# re:gram 1.0.1 (arm64-v8a)

- Android: `versionName=1.0.1`, `versionCode=10001`, package `app.regram.android`.
- Fix: initialize the native video decoder's Java VM before streaming callbacks run, preventing a null-pointer crash when opening a chat with animated/video content.
- The affected chat was reported to open without a crash after installation of the signed 1.0 test build. Full on-device integration checks (login, push, plugins, other chats/devices) are not confirmed.
- APK: `regram-v1.0.1-arm64.apk` (arm64-v8a only). SHA-256: `290843b84c1e80d8981454eb4620c2010dfd85c036ca6a5a951ffbcc27e6b0ef`.
- Signed with the same certificate as the previously installed 1.0 build (certificate SHA-256: `0f47547f8fb5a5c3f1199339cf7b725a19dc030bd90891ce6e97cd364fea5f1d`).
