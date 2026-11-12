"""Built-in WSS: executable wire-framing tests plus integration/security guards."""
from pathlib import Path
import os
import shutil
import subprocess
import xml.etree.ElementTree as ET

import pytest

ROOT = Path(__file__).resolve().parents[1]
JAVA = ROOT / 'TMessagesProj/src/main/java'
NATIVE = ROOT / 'TMessagesProj/jni'


def text(path):
    return (ROOT / path).read_text()


def test_real_obfuscated_framing(tmp_path):
    home = Path(os.environ.get('JAVA_HOME', Path.home() / '.gradle/jdks/jetbrains_s_r_o_-21-amd64-linux.2'))
    javac = shutil.which('javac') or str(home / 'bin/javac')
    java = shutil.which('java') or str(home / 'bin/java')
    if not Path(javac).exists():
        pytest.skip('JDK is needed for executable AES-CTR framing vectors')
    subprocess.run([javac, '-d', str(tmp_path),
                    str(JAVA / 'org/telegram/tgnet/WebSocketPacketFramer.java'),
                    str(ROOT / 'tests/java/WebSocketPacketFramerTest.java')], check=True)
    result = subprocess.run([java, '-cp', str(tmp_path), 'org.telegram.tgnet.WebSocketPacketFramerTest'],
                            check=True, capture_output=True, text=True)
    assert 'WebSocketPacketFramer: OK' in result.stdout


def test_transport_is_in_process_opt_in_and_does_not_weaken_tls():
    source = (JAVA / 'org/telegram/tgnet/TelegramWebSocketTransport.java').read_text()
    for key in ('enabled', 'tcp_fallback'):
        assert f'getBoolean("{key}", false)' in source
    assert 'DEFAULT_CLOUDFLARE = true' in source
    assert 'getBoolean("cloudflare", DEFAULT_CLOUDFLARE)' in source
    assert 'putBoolean("cloudflare"' not in source  # preserve an explicit saved false
    assert 'createSocketPair()' in source and 'detachFd()' in source
    assert 'newWebSocket(request, this)' in source
    assert 'header("Sec-WebSocket-Protocol", "binary")' in source
    assert '"https://" + host' in source
    assert '"/apiws_test" : "/apiws"' in source
    assert 'dc == 203 ? 2 : dc' in source
    assert 'if (fallback && !closed.get()) bridgeTcp()' in source
    for forbidden in ('ServerSocket', 'startForeground', 'sslSocketFactory(', 'hostnameVerifier(',
                      'TrustAll', 'WebView', 'http://', 'ws://'):
        assert forbidden not in source
    assert 'followRedirects(false)' in source
    assert 'Semaphore(MAX_SESSIONS)' in source and 'slots.release()' in source
    assert 'events.size() == 80' in source
    assert 'Os.shutdown(fd, OsConstants.SHUT_RDWR)' in source
    assert 'queueSize() + bytes.length > MAX_QUEUE' in source
    assert 'SO_SNDTIMEO' in source
    assert 'rateLimited' in source and '+ 45000' in source


def test_real_warm_pool_lifecycle(tmp_path):
    home = Path(os.environ.get('JAVA_HOME', Path.home() / '.gradle/jdks/jetbrains_s_r_o_-21-amd64-linux.2'))
    javac = shutil.which('javac') or str(home / 'bin/javac')
    java = shutil.which('java') or str(home / 'bin/java')
    if not Path(javac).exists():
        pytest.skip('JDK is needed for executable pool lifecycle tests')
    subprocess.run([javac, '-d', str(tmp_path),
                    str(JAVA / 'org/telegram/tgnet/WebSocketWarmPool.java'),
                    str(ROOT / 'tests/java/WebSocketWarmPoolTest.java')], check=True)
    result = subprocess.run([java, '-cp', str(tmp_path), 'org.telegram.tgnet.WebSocketWarmPoolTest'],
                            check=True, capture_output=True, text=True, timeout=15)
    assert 'WebSocketWarmPool: OK' in result.stdout


def test_pool_defaults_ui_and_proxy_shutdown():
    source = (JAVA / 'org/telegram/tgnet/TelegramWebSocketTransport.java').read_text()
    ui = (JAVA / 'app/regram/settings/TelegramWebSocketSettingsActivity.java').read_text()
    manager = (JAVA / 'org/telegram/tgnet/ConnectionsManager.java').read_text()
    assert 'DEFAULT_POOL_SIZE = 4' in source
    assert 'getInt("pool_size", DEFAULT_POOL_SIZE)' in source
    assert 'new WebSocketWarmPool<>(16,' in source
    assert 'warmPool.take(route)' in source and 'preparePool(route)' in source
    assert 'if (!ready.attach(this))' in source
    assert 'if (session == null || bytes.size()' in source
    assert 'warmPool.reset()' in source and 'channel.closeIfIdle()' in source
    assert 'if (!warming || owner != null) return' in source  # timer cannot cancel a cold active dial
    assert 'TelegramWebSocketTransport.stopPool()' in manager
    assert ui.count('getBoolean(key, TelegramWebSocketTransport.defaultBoolean(key))') == 2
    assert 'putInt("pool_size", sizes[which])' in ui
    assert 'new ThreadPoolExecutor(2, 2' in source
    assert 'new ArrayBlockingQueue<>(16)' in source


def test_native_path_preserves_proxy_priority_and_obfuscation():
    socket = (NATIVE / 'tgnet/ConnectionSocket.cpp').read_text()
    connection = (NATIVE / 'tgnet/Connection.cpp').read_text()
    wrapper = (NATIVE / 'TgNetWrapper.cpp').read_text()
    manager = (JAVA / 'org/telegram/tgnet/ConnectionsManager.java').read_text()
    assert 'wsDc != 0 && overrideProxyAddress.empty() && ConnectionsManager::getInstance(instanceNum).proxyAddress.empty()' in socket
    assert 'if (fd != -1)' in socket  # allocation failure must not silently fall through to TCP
    assert 'webSocketTransport && !webSocketReady' in socket
    assert '(!webSocketTransport || webSocketReady)' in socket
    assert 'if (webSocketTransport && (errno == EAGAIN || errno == EINTR))' in socket
    assert '!currentDatacenter->isCdnDatacenter' in connection
    assert 'connectionType != ConnectionTypeTemp && connectionType != ConnectionTypeProxy' in connection
    assert 'if (webSocketTransport) {\n            // WSS' in connection
    assert 'if (useSecret != 0 || webSocketTransport)' in connection
    assert 'bool proxyTestDc = !webSocketTransport &&' in connection
    assert 'webSocketTransport = webSocketReady = false' in socket
    assert '"(IZZLjava/lang/String;I)I"' in wrapper
    assert '"native_onTransportChanged", "(I)V"' in wrapper
    assert 'native void native_onTransportChanged(int currentAccount)' in manager
    assert 'public static int openTelegramWebSocket(int dc, boolean media, boolean test, String address, int port)' in manager


def test_settings_entry_translations_and_source_notices():
    hub = (JAVA / 'app/regram/settings/RegramFeaturesActivity.java').read_text()
    assert 'presentFragment(new TelegramWebSocketSettingsActivity())' in hub
    assert 'webSocketRow = addRow("regramWebSocket", "RegramWsTitle")' in hub
    translations = []
    for locale in ('values', 'values-ru'):
        root = ET.parse(ROOT / f'TMessagesProj/src/main/res/{locale}/regram_websocket.xml').getroot()
        translations.append({item.attrib['name']: item.text for item in root})
    assert translations[0].keys() == translations[1].keys()
    assert translations[1]['RegramWsTitle'] == 'Обход блокировки'
    assert translations[1]['RegramWsEnabled'] == 'Использовать обход'
    assert translations[0]['RegramWsTitle'] == 'Bypass blocking'
    assert translations[0]['RegramWsEnabled'] == 'Use bypass'
    ui = (JAVA / 'app/regram/settings/TelegramWebSocketSettingsActivity.java').read_text()
    assert 'getActionBarTitle() { return getString(R.string.RegramWsTitle); }' in ui
    assert 'position == enabled ? R.string.RegramWsEnabled' in ui
    assert 'ws_info' not in ui
    assert 'RegramWsInfo' not in ui
    assert 'TextInfoPrivacyCell' not in ui
    assert 'TYPE_INFO_PRIVACY' not in ui
    assert 'source = addRow("ws_source")' in ui
    for strings in translations:
        assert 'CDN' in strings['RegramWsInfo'] and 'VPN' in strings['RegramWsInfo']
        assert 'GPLv3' in strings['RegramWsInfo'] and 'MIT' in strings['RegramWsInfo']
