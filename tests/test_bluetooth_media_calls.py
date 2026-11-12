"""Executable routing policy with Android doubles; not a hardware A2DP quality test."""
from pathlib import Path
import xml.etree.ElementTree as ET

from test_native_visual_settings import compile_run, method

ROOT = Path(__file__).resolve().parents[1]
JAVA = ROOT / 'TMessagesProj/src/main/java'
VOIP = JAVA / 'org/telegram/messenger/voip'


def test_real_bluetooth_media_route_lifecycle(tmp_path):
    compile_run(tmp_path, {
        'org/telegram/messenger/voip/BluetoothMediaAudio.java': (VOIP / 'BluetoothMediaAudio.java').read_text(),
        'org/telegram/messenger/voip/VoipAudioManager.java': (VOIP / 'VoipAudioManager.java').read_text(),
        'android/content/Context.java': '''package android.content;
public class Context {
 public static final String AUDIO_SERVICE="audio";
 public static Object audio;
 public Object getSystemService(String name){return audio;}
}
''',
        'androidx/annotation/RequiresApi.java': '''package androidx.annotation;
public @interface RequiresApi { int api(); }
''',
        'org/telegram/messenger/ApplicationLoader.java': '''package org.telegram.messenger;
public class ApplicationLoader { public static android.content.Context applicationContext=new android.content.Context(); }
''',
        'org/telegram/messenger/AndroidUtilities.java': '''package org.telegram.messenger;
public class AndroidUtilities { public static void runOnUIThread(Runnable r){r.run();} }
''',
        'org/telegram/messenger/Utilities.java': '''package org.telegram.messenger;
public class Utilities {
 public interface Callback2<A,B>{void run(A a,B b);}
 public static class Queue{public void postRunnable(Runnable r){r.run();}}
 public static Queue globalQueue=new Queue();
}
''',
        'org/telegram/messenger/voip/VoIPService.java': '''package org.telegram.messenger.voip;
public class VoIPService { public static final int AUDIO_ROUTE_EARPIECE=0,AUDIO_ROUTE_SPEAKER=1,AUDIO_ROUTE_BLUETOOTH=2; }
''',
        'org/telegram/messenger/FileLog.java': '''package org.telegram.messenger;
public class FileLog { public static void e(Exception e) {} }
''',
        'android/os/Build.java': '''package android.os;
public class Build {
 public static class VERSION { public static int SDK_INT=31; }
 public static class VERSION_CODES { public static final int M=23,P=28,S=31; }
}
''',
        'android/media/AudioAttributes.java': '''package android.media;
public class AudioAttributes {
 public static final int USAGE_MEDIA=1,CONTENT_TYPE_SPEECH=1,ALLOW_CAPTURE_BY_NONE=3;
 public int usage,capturePolicy;
 public static class Builder {
  AudioAttributes a=new AudioAttributes();
  public Builder setUsage(int v){a.usage=v;return this;}
  public Builder setContentType(int v){return this;}
  public Builder setAllowedCapturePolicy(int v){a.capturePolicy=v;return this;}
  public AudioAttributes build(){return a;}
 }
}
''',
        'android/media/MediaRecorder.java': '''package android.media;
public class MediaRecorder { public static class AudioSource { public static final int MIC=1; } }
''',
        'android/media/AudioDeviceInfo.java': '''package android.media;
public class AudioDeviceInfo {
 public static final int TYPE_BUILTIN_EARPIECE=1,TYPE_BUILTIN_SPEAKER=2,TYPE_WIRED_HEADSET=3,
 TYPE_WIRED_HEADPHONES=4,TYPE_BLUETOOTH_SCO=7,TYPE_BLUETOOTH_A2DP=8,TYPE_BUILTIN_MIC=15,
 TYPE_HEARING_AID=23,TYPE_BLE_HEADSET=26,TYPE_BLE_SPEAKER=27;
 int type; public AudioDeviceInfo(int type) {this.type=type;}
 public int getType(){return type;}
 public CharSequence getProductName(){return "Headphones";}
}
''',
        'android/media/AudioManager.java': '''package android.media;
public class AudioManager {
 public static final int GET_DEVICES_OUTPUTS=2,GET_DEVICES_INPUTS=1,STREAM_MUSIC=3,MODE_NORMAL=0;
 public AudioDeviceInfo[] outputs={},inputs={};
 public int mode=-1,clear,scoStop,scoStart,communicationSelections; public boolean sco=true,speaker=true;
 public AudioDeviceInfo communication;
 public AudioDeviceInfo[] getDevices(int flags){return flags==2?outputs:inputs;}
 public java.util.List<AudioDeviceInfo> getAvailableCommunicationDevices(){return java.util.Arrays.asList(outputs);}
 public AudioDeviceInfo getCommunicationDevice(){return communication;}
 public boolean setCommunicationDevice(AudioDeviceInfo d){communicationSelections++;communication=d;return true;}
 public void startBluetoothSco(){scoStart++;}
 public boolean isBluetoothScoOn(){return sco;}
 public boolean isSpeakerphoneOn(){return speaker;}
 public void clearCommunicationDevice(){clear++;}
 public void stopBluetoothSco(){scoStop++;}
 public void setBluetoothScoOn(boolean value){sco=value;}
 public void setSpeakerphoneOn(boolean value){speaker=value;}
 public void setMode(int value){mode=value;}
}
''',
        'android/media/AudioTrack.java': '''package android.media;
public class AudioTrack {
 public float volume=1; public boolean reject; public AudioDeviceInfo device;
 public void setVolume(float v){volume=v;}
 public boolean setPreferredDevice(AudioDeviceInfo d){device=d;return !reject;}
}
''',
        'android/media/AudioRecord.java': '''package android.media;
public class AudioRecord {
 public boolean reject; public AudioDeviceInfo device;
 public boolean setPreferredDevice(AudioDeviceInfo d){device=d;return !reject;}
}
''',
        'PolicyTest.java': '''
import android.media.*;
import android.os.Build;
import org.telegram.messenger.voip.BluetoothMediaAudio;
import org.telegram.messenger.voip.VoipAudioManager;
public class PolicyTest {
 static AudioDeviceInfo device(int type){return new AudioDeviceInfo(type);}
 public static void main(String[] args){
  AudioManager am=new AudioManager(); Object owner=new Object();
  android.content.Context.audio=am;
  AudioDeviceInfo bt=device(8), mic=device(15), ear=device(1), speaker=device(2), sco=device(7);
  am.outputs=new AudioDeviceInfo[]{bt,ear,speaker}; am.inputs=new AudioDeviceInfo[]{sco,mic};
  BluetoothMediaAudio.begin(owner,am,false); assert !BluetoothMediaAudio.isActive();
  assert BluetoothMediaAudio.streamType(0)==0 && BluetoothMediaAudio.recordingSource(7)==7;
  BluetoothMediaAudio.configureSystem(am); assert am.mode==-1;
  am.outputs=new AudioDeviceInfo[]{ear,speaker};
  BluetoothMediaAudio.begin(owner,am,true); assert !BluetoothMediaAudio.isActive();
  am.outputs=new AudioDeviceInfo[]{bt,ear,speaker}; BluetoothMediaAudio.devicesChanged();
  assert !BluetoothMediaAudio.isActive(); // connecting later cannot opt into this call
  Build.VERSION.SDK_INT=22; BluetoothMediaAudio.begin(owner,am,true); assert !BluetoothMediaAudio.isActive();
  Build.VERSION.SDK_INT=31; BluetoothMediaAudio.begin(owner,am,true); assert BluetoothMediaAudio.isActive();
  assert BluetoothMediaAudio.streamType(0)==3 && BluetoothMediaAudio.usage(2)==1;
  assert BluetoothMediaAudio.recordingSource(7)==1;
  AudioAttributes attrs=BluetoothMediaAudio.playbackAttributes(2);
  assert attrs.usage==1 && attrs.capturePolicy==AudioAttributes.ALLOW_CAPTURE_BY_NONE;
  BluetoothMediaAudio.configureSystem(am);
  assert am.mode==0 && am.clear==1 && am.scoStop==1 && !am.sco && !am.speaker;
  AudioTrack track=new AudioTrack(); BluetoothMediaAudio.bindPlayback(track);
  assert track.device==bt && track.volume==1;
  VoipAudioManager vam=VoipAudioManager.get();
  vam.stopBluetooth(); assert !vam.isBluetoothOn() && track.device==ear;
  vam.startBluetooth(); assert vam.isBluetoothOn() && track.device==bt;
  vam.setSpeakerphoneOn(true); assert vam.isSpeakerphoneOn() && track.device==speaker;
  vam.setBluetoothOn(true); vam.setSpeakerphoneOn(false);
  assert vam.isBluetoothOn() && !vam.isSpeakerphoneOn() && track.device==bt;
  vam.isBluetoothAndSpeakerOnAsync((b,s)->{assert b && !s;});
  assert am.scoStart==0 && am.communicationSelections==0 && am.scoStop==1;
  AudioRecord record=new AudioRecord(); assert BluetoothMediaAudio.bindMicrophone(record);
  assert record.device==mic; // even with a Bluetooth mic available, select the phone
  record.reject=true; assert !BluetoothMediaAudio.bindMicrophone(record);
  BluetoothMediaAudio.selectRoute(1); assert track.device==speaker;
  BluetoothMediaAudio.selectRoute(0); assert track.device==ear;
  BluetoothMediaAudio.selectRoute(2); assert track.device==bt;
  am.outputs=new AudioDeviceInfo[]{ear,speaker}; BluetoothMediaAudio.devicesChanged();
  assert track.device==ear && BluetoothMediaAudio.currentRoute()==0;
  am.outputs=new AudioDeviceInfo[]{bt,ear,speaker}; BluetoothMediaAudio.devicesChanged();
  assert BluetoothMediaAudio.currentRoute()==0; // no unexpected automatic switch after reconnection
  BluetoothMediaAudio.selectRoute(2); assert track.device==bt;
  am.outputs=new AudioDeviceInfo[]{speaker}; BluetoothMediaAudio.devicesChanged();
  assert track.volume==0; // tablet with no earpiece: never intentionally leak speech to speaker
  BluetoothMediaAudio.selectRoute(1); assert track.device==speaker && track.volume==1;
  track.reject=true; BluetoothMediaAudio.selectRoute(1); assert track.volume==0;
  BluetoothMediaAudio.end(new Object()); assert BluetoothMediaAudio.isActive();
  BluetoothMediaAudio.end(owner); assert !BluetoothMediaAudio.isActive();
  assert BluetoothMediaAudio.streamType(0)==0 && BluetoothMediaAudio.recordingSource(7)==7;
  BluetoothMediaAudio.begin(owner,am,true); assert !BluetoothMediaAudio.isActive();
  am.outputs=new AudioDeviceInfo[]{bt}; am.inputs=new AudioDeviceInfo[]{sco};
  BluetoothMediaAudio.begin(owner,am,true); assert !BluetoothMediaAudio.isActive(); // no built-in mic
  am.outputs=new AudioDeviceInfo[]{sco}; vam.startBluetooth();
  assert am.communicationSelections==1; // standard SCO route still works when HD mode is inactive
  am.outputs=new AudioDeviceInfo[]{bt,ear}; am.inputs=new AudioDeviceInfo[]{mic};
  Build.VERSION.SDK_INT=23; BluetoothMediaAudio.begin(owner,am,true);
  BluetoothMediaAudio.configureSystem(am); assert am.clear==1; // no Android 12 API on Android 6
  BluetoothMediaAudio.end(owner);
 }
}
''',
    }, 'PolicyTest')


def test_service_latches_at_start_and_all_call_engines_bind_actual_devices():
    service = (VOIP / 'VoIPService.java').read_text()
    create = method(service, 'public void onCreate()')
    assert create.index('BluetoothMediaAudio.begin(') < create.index('checkUpdateBluetoothHeadset();')
    assert 'NekoConfig.regramHdBluetoothCalls.Bool() && !USE_CONNECTION_SERVICE' in create
    configure = method(service, 'private void configureDeviceForCall()')
    assert 'if (hasRtmpStream()) BluetoothMediaAudio.end(this);' in configure
    assert 'BluetoothMediaAudio.configureSystem(am)' in configure
    assert 'BluetoothMediaAudio.streamType(AudioManager.STREAM_VOICE_CALL)' in configure
    assert 'BluetoothMediaAudio.end(this);' in method(service, 'public void onDestroy()')
    assert 'BluetoothMediaAudio.selectRoute(route)' in method(service, 'public void setAudioOutput(int which)')
    toggle = method(service, 'public void toggleSpeakerphoneOrShowRouteSheet(Context context, boolean fromOverlayWindow, Integer selectedPos)')
    assert 'BluetoothMediaAudio.hasBluetoothOutput() ? 2 : 1' in toggle
    assert 'if (BluetoothMediaAudio.isActive()) return;' in method(service, 'public void switchToSpeaker()')
    assert 'BluetoothMediaAudio.devicesChanged();' in method(service, 'private void checkUpdateBluetoothHeadset()')
    for track in (VOIP / 'AudioTrackJNI.java', JAVA / 'org/webrtc/voiceengine/WebRtcAudioTrack.java'):
        assert 'BluetoothMediaAudio.bindPlayback(audioTrack)' in track.read_text()
        assert 'BluetoothMediaAudio.playbackAttributes(' in track.read_text()
    for record in (VOIP / 'AudioRecordJNI.java', JAVA / 'org/webrtc/voiceengine/WebRtcAudioRecord.java'):
        text = record.read_text()
        assert 'BluetoothMediaAudio.bindMicrophone(audioRecord)' in text
        assert 'BluetoothMediaAudio.recordingSource(' in text
    voice_record = (JAVA / 'org/webrtc/voiceengine/WebRtcAudioRecord.java').read_text()
    assert 'captureType == 0 && !org.telegram.messenger.voip.BluetoothMediaAudio.bindMicrophone' in voice_record
    manager = (VOIP / 'VoipAudioManager.java').read_text()
    for signature in ('public void startBluetooth()', 'public void stopBluetooth()', 'public void setBluetoothOn(boolean on)'):
        assert 'BluetoothMediaAudio.isActive()' in method(manager, signature)
    for file in ('LaunchActivity.java', 'GroupCallActivity.java'):
        assert 'BluetoothMediaAudio.streamType(AudioManager.STREAM_VOICE_CALL)' in (JAVA / 'org/telegram/ui' / file).read_text()


def test_hd_bluetooth_setting_defaults_off_and_is_navigable_and_localized():
    config = (JAVA / 'tw/nekomimi/nekogram/NekoConfig.java').read_text()
    assert 'regramHdBluetoothCalls = addConfig("RegramHdBluetoothCalls", configTypeBool, false)' in config
    hub = (JAVA / 'app/regram/settings/RegramFeaturesActivity.java').read_text()
    assert 'presentFragment(new BluetoothCallAudioSettingsActivity())' in hub
    registry = (JAVA / 'app/regram/settings/utils/SettingsRegistry.java').read_text()
    assert 'icons.put(app.regram.settings.BluetoothCallAudioSettingsActivity.class' in registry
    settings = (JAVA / 'app/regram/settings/BluetoothCallAudioSettingsActivity.java').read_text()
    assert 'NekoConfig.regramHdBluetoothCalls.toggleConfigBool()' in settings
    names=[]
    for locale in ('values', 'values-ru'):
        resources = ET.parse(ROOT / f'TMessagesProj/src/main/res/{locale}/regram_bluetooth_calls.xml').getroot()
        strings={e.get('name'):e.text for e in resources}
        names.append(set(strings))
        assert 'A2DP' in strings['RegramHdBluetoothCallsInfo']
    assert names[0] == names[1]
