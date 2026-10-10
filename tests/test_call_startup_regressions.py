"""Call/startup regression checks; do not replace real-device call tests."""
from pathlib import Path
import shutil
import subprocess

import pytest

ROOT = Path(__file__).resolve().parents[1]
JAVA = ROOT / 'TMessagesProj/src/main/java'
JNI = ROOT / 'TMessagesProj/jni/voip/org_telegram_messenger_voip_Instance.cpp'


def test_empty_account_slots_are_not_eagerly_initialized():
    code = (JAVA / 'org/telegram/messenger/ApplicationLoader.java').read_text()
    guard = 'if (a != UserConfig.selectedAccount && !UserConfig.getInstance(a).isClientActivated()) continue;'
    assert code.count(guard) == 3
    assert 'UserConfig.getInstance(a).loadConfig();\n            ' + guard in code
    assert guard + '\n                        ConnectionsManager.getInstance(a).checkConnection();' in code
    assert guard + '\n            ContactsController.getInstance(a).checkAppAccount();' in code
    assert 'applicationHandler.post(() -> app.regram.plugins.PluginsController.getInstance().init(applicationContext))' in code


def test_python_startup_does_not_force_ui_gil_acquisition():
    code = (JAVA / 'app/regram/plugins/PythonPluginsEngine.java').read_text()
    assert 'runOnUIThread(PythonThreadState::pinCurrentThread)' not in code
    task = code.split('executor.execute(() -> {', 1)[1]
    assert task.index('if (started || startFailed)') < task.index('Python.start(')
    assert 'onCall()' in code


def test_voip_native_factory_is_checked_before_dereferencing():
    code = JNI.read_text()
    factory = code.split('holder->nativeInstance = tgcalls::Meta::Create', 1)[1]
    assert factory.index('if (!holder->nativeInstance)') < factory.index('holder->nativeInstance->setIncomingVideoOutput')
    assert 'std::make_unique<InstanceHolder>()' in code
    assert 'holder.release()' in code
    assert 'GetArrayLength(valueByteArray) != 256' in code
    assert 'GetArrayLength(peerTag) != 16' in code
    assert 'const std::lock_guard<std::mutex> lock(initializationMutex)' in code
    init = code.split('void initWebRTC', 1)[1].split('extern "C"', 1)[0]
    assert init.index('webrtcLoaded = true') > init.index('FinalStateInitMethod =')
    assert '"Unknown data saving constant: " + std::to_string(dataSaving)' in code


def test_call_bridge_rejects_null_handles_and_short_amplitude_updates():
    code = (JAVA / 'org/telegram/messenger/voip/NativeInstance.java').read_text()
    assert 'encryptionKey.value.length != 256' in code
    assert 'if (instance.nativePtr == 0)' in code
    assert 'levels.length < 2' in code
    service = (JAVA / 'org/telegram/messenger/voip/VoIPService.java').read_text()
    assert 'Instance.selectVersion(privateCall.protocol.library_versions)' in service
    assert 'Instance.makeInstance(libraryVersion, config,' in service
    assert 'catch (Exception | LinkageError x)' in service
    assert 'sharedInstance != VoIPService.this' in service


def test_both_voip_engines_publish_vm_before_starting_callbacks():
    modern = JNI.read_text().split('void initWebRTC', 1)[1].split('extern "C"', 1)[0]
    assert 'if (!sharedJVM)' in modern
    assert modern.index('sharedJVM = vm;') < modern.index('webrtc::InitAndroid(vm)')
    legacy = (ROOT / 'TMessagesProj/jni/voip/tg_voip_jni.cpp').read_text()
    load = legacy.split('int tgvoipOnJNILoad', 1)[1]
    assert load.index('sharedJVM = vm;') < load.index('env->FindClass')


def test_ffmpeg_jni_classes_are_retained_and_unverified_modules_rejected():
    rules = (ROOT / 'TMessagesProj/proguard-rules.pro').read_text()
    assert '-keep class androidx.media3.decoder.ffmpeg.** { *; }' in rules
    restore = (ROOT / 'tools/restore_submodules.sh').read_text()
    unverified = restore.split('if [ -d "$path" ]', 1)[1].split('echo "FETCH', 1)[0]
    assert 'return 1' in unverified
    assert 'SKIP' not in unverified


def test_cached_native_library_is_bound_to_installed_apk():
    code = (JAVA / 'org/telegram/messenger/NativeLoader.java').read_text()
    assert 'apk.length() + "_" + apk.lastModified()' in code
    assert 'new File(destDir, cacheName)' in code
    assert 'Thread.yield()' not in code
    assert 'new byte[64 * 1024]' in code
    assert 'return nativeLoaded;' in code


def test_version_selection_with_actual_java_method(tmp_path):
    javac, java = shutil.which('javac'), shutil.which('java')
    if not javac or not java:
        pytest.skip('JDK unavailable')
    source = (JAVA / 'org/telegram/messenger/voip/Instance.java').read_text()
    method = source.split('    public static String selectVersion(', 1)[1]
    method = 'public static String selectVersion(' + method.split('    public static NativeInstance makeInstance', 1)[0]
    test = '''import java.util.*;
class NativeInstance {
    static String[] getAllVersions() { return new String[]{"2.7.7", "5.0.0"}; }
}
public class VersionTest {
METHOD
    public static void main(String[] args) {
        assert selectVersion(Arrays.asList("unsupported", "5.0.0", "2.7.7")).equals("5.0.0");
        assert selectVersion(Arrays.asList(null, "2.7.7")).equals("2.7.7");
        assert selectVersion(Arrays.asList("2.7.7", "5.0.0")).equals("2.7.7");
        for (List<String> versions : Arrays.asList(Collections.<String>emptyList(), Arrays.asList("unsupported"))) {
            try { selectVersion(versions); throw new AssertionError("unsupported version accepted"); }
            catch (IllegalArgumentException expected) {}
        }
        try { selectVersion(null); throw new AssertionError("null accepted"); }
        catch (IllegalArgumentException expected) {}
    }
}
'''.replace('METHOD', method)
    path = tmp_path / 'VersionTest.java'
    path.write_text(test)
    subprocess.run([javac, str(path)], check=True, capture_output=True, text=True)
    subprocess.run([java, '-ea', '-cp', str(tmp_path), 'VersionTest'], check=True,
                   capture_output=True, text=True)
