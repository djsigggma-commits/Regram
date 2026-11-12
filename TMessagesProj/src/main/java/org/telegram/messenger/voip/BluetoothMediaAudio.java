package org.telegram.messenger.voip;

import android.media.AudioAttributes;
import android.media.AudioDeviceInfo;
import android.media.AudioManager;
import android.media.AudioRecord;
import android.media.AudioTrack;
import android.media.MediaRecorder;
import android.os.Build;

import org.telegram.messenger.FileLog;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Set;
import java.util.WeakHashMap;

/** Per-call opt-in A2DP output + phone microphone. Never activates on a later connection. */
public final class BluetoothMediaAudio {
    private static volatile BluetoothMediaAudio session;
    private final Object owner;
    private final AudioManager manager;
    private final Set<AudioTrack> tracks = Collections.newSetFromMap(new WeakHashMap<>());
    private volatile int route = VoIPService.AUDIO_ROUTE_BLUETOOTH;

    private BluetoothMediaAudio(Object owner, AudioManager manager) {
        this.owner = owner;
        this.manager = manager;
    }

    /** Snapshot taken before VoIP starts changing the system audio mode or Bluetooth route. */
    public static void begin(Object owner, AudioManager manager, boolean enabled) {
        if (!enabled || Build.VERSION.SDK_INT < 23 || manager == null) return;
        BluetoothMediaAudio candidate = new BluetoothMediaAudio(owner, manager);
        try {
            if (candidate.bluetoothOutput() != null && candidate.phoneMicrophone() != null) {
                session = candidate;
            }
        } catch (RuntimeException e) {
            FileLog.e(e);
        }
    }

    public static void end(Object owner) {
        BluetoothMediaAudio current = session;
        if (current != null && current.owner == owner) {
            session = null;
            synchronized (current) {
                current.tracks.clear();
            }
        }
    }

    public static boolean isActive() { return session != null; }
    public static int streamType(int fallback) { return isActive() ? AudioManager.STREAM_MUSIC : fallback; }
    public static int usage(int fallback) { return isActive() ? AudioAttributes.USAGE_MEDIA : fallback; }
    public static int recordingSource(int fallback) { return isActive() ? MediaRecorder.AudioSource.MIC : fallback; }

    public static AudioAttributes playbackAttributes(int fallbackUsage) {
        AudioAttributes.Builder builder = new AudioAttributes.Builder()
                .setUsage(usage(fallbackUsage)).setContentType(AudioAttributes.CONTENT_TYPE_SPEECH);
        // USAGE_MEDIA must not make private call audio eligible for playback capture
        // by another app, or feed it back into our own screen-sharing capture.
        if (isActive() && Build.VERSION.SDK_INT >= 29) {
            builder.setAllowedCapturePolicy(AudioAttributes.ALLOW_CAPTURE_BY_NONE);
        }
        return builder.build();
    }

    public static void configureSystem(AudioManager manager) {
        if (!isActive()) return;
        // Do not select an Android communication device: that would force HFP/SCO again.
        if (Build.VERSION.SDK_INT >= 31) manager.clearCommunicationDevice();
        manager.stopBluetoothSco();
        manager.setBluetoothScoOn(false);
        manager.setSpeakerphoneOn(false);
        manager.setMode(AudioManager.MODE_NORMAL);
    }

    public static boolean hasBluetoothOutput() {
        BluetoothMediaAudio current = session;
        return current != null && current.bluetoothOutput() != null;
    }

    public static String deviceName() {
        BluetoothMediaAudio current = session;
        AudioDeviceInfo device = current == null ? null : current.bluetoothOutput();
        return device == null ? null : device.getProductName().toString();
    }

    public static int currentRoute() {
        BluetoothMediaAudio current = session;
        return current == null ? VoIPService.AUDIO_ROUTE_EARPIECE : current.route;
    }

    public static void selectRoute(int route) {
        BluetoothMediaAudio current = session;
        if (current == null) return;
        synchronized (current) {
            current.route = route;
            current.updateTracks();
        }
    }

    public static void devicesChanged() {
        BluetoothMediaAudio current = session;
        if (current == null) return;
        synchronized (current) {
            if (current.route == VoIPService.AUDIO_ROUTE_BLUETOOTH && current.bluetoothOutput() == null) {
                current.route = VoIPService.AUDIO_ROUTE_EARPIECE;
            }
            current.updateTracks();
        }
    }

    /** Called for each actual call AudioTrack, including legacy and recreated native tracks. */
    public static void bindPlayback(AudioTrack track) {
        BluetoothMediaAudio current = session;
        if (current == null) return;
        synchronized (current) {
            current.tracks.add(track);
            current.routeTrack(track);
        }
    }

    /** Fail recording initialization rather than silently using a headset microphone. */
    public static boolean bindMicrophone(AudioRecord record) {
        BluetoothMediaAudio current = session;
        if (current == null) return true;
        try {
            AudioDeviceInfo microphone = current.phoneMicrophone();
            return microphone != null && record.setPreferredDevice(microphone);
        } catch (RuntimeException e) {
            FileLog.e(e);
            return false;
        }
    }

    private void updateTracks() {
        for (AudioTrack track : new ArrayList<>(tracks)) {
            if (track == null) continue;
            try {
                routeTrack(track);
            } catch (IllegalStateException e) {
                // Native teardown may have released a weakly tracked AudioTrack already.
                tracks.remove(track);
            }
        }
    }

    private void routeTrack(AudioTrack track) {
        // Mute first: loss of Bluetooth must not deliberately fall back to the loudspeaker.
        track.setVolume(0f);
        AudioDeviceInfo output = outputDevice();
        if (output != null && track.setPreferredDevice(output)) {
            track.setVolume(1f);
        }
        // No earpiece (e.g. a tablet), or a rejected route: remain muted until the
        // user explicitly chooses the speaker or Bluetooth in the normal route sheet.
    }

    private AudioDeviceInfo outputDevice() {
        if (route == VoIPService.AUDIO_ROUTE_BLUETOOTH) return bluetoothOutput();
        if (route == VoIPService.AUDIO_ROUTE_SPEAKER) return find(AudioManager.GET_DEVICES_OUTPUTS, AudioDeviceInfo.TYPE_BUILTIN_SPEAKER);
        AudioDeviceInfo wired = find(AudioManager.GET_DEVICES_OUTPUTS, AudioDeviceInfo.TYPE_WIRED_HEADSET);
        if (wired == null) wired = find(AudioManager.GET_DEVICES_OUTPUTS, AudioDeviceInfo.TYPE_WIRED_HEADPHONES);
        return wired != null ? wired : find(AudioManager.GET_DEVICES_OUTPUTS, AudioDeviceInfo.TYPE_BUILTIN_EARPIECE);
    }

    private AudioDeviceInfo bluetoothOutput() {
        return find(AudioManager.GET_DEVICES_OUTPUTS, AudioDeviceInfo.TYPE_BLUETOOTH_A2DP);
    }

    private AudioDeviceInfo phoneMicrophone() {
        return find(AudioManager.GET_DEVICES_INPUTS, AudioDeviceInfo.TYPE_BUILTIN_MIC);
    }

    private AudioDeviceInfo find(int flags, int type) {
        for (AudioDeviceInfo device : manager.getDevices(flags)) {
            if (device.getType() == type) return device;
        }
        return null;
    }
}
