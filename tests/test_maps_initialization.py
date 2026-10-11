"""Local map prerequisites and lifecycle regressions, not network/tile tests."""
from pathlib import Path
import shutil
import subprocess

import pytest

ROOT = Path(__file__).resolve().parents[1]
JAVA = ROOT / 'TMessagesProj/src/main/java'


def method(source, signature):
    start = source.index(signature)
    brace = source.index('{', start)
    depth, end = 1, brace + 1
    while depth:
        depth += (source[end] == '{') - (source[end] == '}')
        end += 1
    return source[start:end]


@pytest.mark.parametrize('path', [
    'org/telegram/ui/LocationActivity.java',
    'org/telegram/ui/Components/ChatAttachAlertLocationLayout.java',
])
def test_map_created_once_on_ui_thread_and_stale_callbacks_ignored(path):
    source = (JAVA / path).read_text()
    start = source.index('final IMapsProvider.IMapView map = mapView;')
    end = source.index('        });', source.index('mapsInitialized = true;', start))
    init = source[start:end]
    assert init.count('map.onCreate(null)') == 1
    assert source.count('map.onCreate(null)') == 1
    assert 'new Thread' not in init
    assert init.index('AndroidUtilities.runOnUIThread(') < init.index('map.onCreate(null)')
    assert init.index('initializeMaps(') < init.index('map.onCreate(null)')
    assert init.count('if (mapView != map || getParentActivity() == null)') == 2
    assert 'finally {\n            mapView = null;\n            mapsInitialized = false;' in source


def test_prerequisites_and_provider_selection_without_android(tmp_path):
    javac, java = shutil.which('javac'), shutil.which('java')
    if not javac or not java:
        pytest.skip('JDK unavailable')
    loader = (JAVA / 'org/telegram/messenger/ApplicationLoader.java').read_text()
    get_provider = method(loader, 'public static synchronized IMapsProvider getMapsProvider()')
    reset = method(loader, 'public static synchronized void resetMapsProvider()')
    sources = {
        'org/telegram/messenger/MapsAvailability.java':
            (JAVA / 'org/telegram/messenger/MapsAvailability.java').read_text(),
        'android/os/Bundle.java': '''package android.os;
            public class Bundle {
                public String key;
                public String getString(String name) { return key; }
            }''',
        'android/content/pm/ApplicationInfo.java': '''package android.content.pm;
            public class ApplicationInfo { public android.os.Bundle metaData; }''',
        'android/content/pm/PackageManager.java': '''package android.content.pm;
            public class PackageManager {
                public static final int GET_META_DATA = 128;
                public ApplicationInfo info = new ApplicationInfo();
                public boolean fail;
                public ApplicationInfo getApplicationInfo(String name, int flags) {
                    if (fail) throw new IllegalStateException("test failure");
                    if (flags != GET_META_DATA) throw new AssertionError("Missing metadata flag");
                    return info;
                }
            }''',
        'android/content/Context.java': '''package android.content;
            public class Context {
                public final android.content.pm.PackageManager pm = new android.content.pm.PackageManager();
                public android.content.pm.PackageManager getPackageManager() { return pm; }
                public String getPackageName() { return "app.regram.android"; }
            }''',
        'com/google/android/gms/common/ConnectionResult.java': '''package com.google.android.gms.common;
            public class ConnectionResult { public static final int SUCCESS = 0; }''',
        'com/google/android/gms/common/GoogleApiAvailability.java': '''package com.google.android.gms.common;
            public class GoogleApiAvailability {
                private static final GoogleApiAvailability INSTANCE = new GoogleApiAvailability();
                public static int status;
                public static boolean linkageFailure;
                public static GoogleApiAvailability getInstance() {
                    if (linkageFailure) throw new NoClassDefFoundError("test failure");
                    return INSTANCE;
                }
                public int isGooglePlayServicesAvailable(android.content.Context context) { return status; }
            }''',
        'org/telegram/messenger/FileLog.java': '''package org.telegram.messenger;
            public class FileLog {
                public static void d(String message) {
                    if (message.contains("secret-test-key")) throw new AssertionError("Key leaked");
                }
            }''',
        'org/telegram/messenger/ApplicationLoader.java': '''package org.telegram.messenger;
            interface IMapsProvider {}
            class GoogleMapsProvider implements IMapsProvider {}
            class OSMDroidMapsProvider implements IMapsProvider {}
            class NekoConfig {
                static final Config useOSMDroidMap = new Config();
                static class Config { boolean value; boolean Bool() { return value; } }
            }
            public class ApplicationLoader {
                static android.content.Context applicationContext;
                static IMapsProvider mapsProvider;
            ''' + get_provider + reset + '}',
        'org/telegram/messenger/CheckMaps.java': '''package org.telegram.messenger;
            import android.content.Context;
            import android.os.Bundle;
            import com.google.android.gms.common.GoogleApiAvailability;
            public class CheckMaps {
                static void expect(boolean available, Context context) {
                    if (MapsAvailability.canUseGoogleMaps(context) != available)
                        throw new AssertionError("Prerequisite result");
                }
                static void provider(boolean google) {
                    ApplicationLoader.resetMapsProvider();
                    if ((ApplicationLoader.getMapsProvider() instanceof GoogleMapsProvider) != google)
                        throw new AssertionError("Provider result");
                }
                public static void main(String[] args) {
                    expect(false, null);
                    Context context = new Context();
                    expect(false, context);
                    context.pm.info.metaData = new Bundle();
                    expect(false, context);
                    context.pm.info.metaData.key = "   ";
                    expect(false, context);
                    context.pm.info.metaData.key = "secret-test-key";
                    expect(true, context);
                    for (int status : new int[]{1, 2, 3, 9}) {
                        GoogleApiAvailability.status = status;
                        expect(false, context);
                    }
                    GoogleApiAvailability.status = 0;
                    context.pm.fail = true;
                    expect(false, context);
                    context.pm.fail = false;
                    GoogleApiAvailability.linkageFailure = true;
                    expect(false, context);
                    GoogleApiAvailability.linkageFailure = false;
                    ApplicationLoader.applicationContext = context;
                    provider(true);
                    NekoConfig.useOSMDroidMap.value = true;
                    provider(false);
                    NekoConfig.useOSMDroidMap.value = false;
                    context.pm.info.metaData.key = "";
                    provider(false);
                    if (NekoConfig.useOSMDroidMap.value) throw new AssertionError("Preference changed");
                    context.pm.info.metaData.key = "secret-test-key";
                    GoogleApiAvailability.status = 2;
                    provider(false);
                    GoogleApiAvailability.status = 0;
                    provider(true);
                }
            }''',
    }
    files = []
    for name, content in sources.items():
        path = tmp_path / name
        path.parent.mkdir(parents=True, exist_ok=True)
        path.write_text(content)
        files.append(str(path))
    subprocess.run([javac, '-d', str(tmp_path), *files], check=True, capture_output=True, text=True)
    subprocess.run([java, '-cp', str(tmp_path), 'org.telegram.messenger.CheckMaps'],
                   check=True, capture_output=True, text=True)
