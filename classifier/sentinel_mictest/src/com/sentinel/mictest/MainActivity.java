package com.sentinel.mictest;

import android.Manifest;
import android.app.Activity;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.media.AudioAttributes;
import android.media.AudioDeviceInfo;
import android.media.AudioFormat;
import android.media.AudioManager;
import android.media.AudioRecord;
import android.media.MediaPlayer;
import android.media.MediaRecorder;
import android.media.MicrophoneInfo;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.util.Log;
import android.view.WindowManager;
import android.widget.Button;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.RandomAccessFile;
import java.util.List;

/**
 * Minimal AudioRecord microphone test.
 *
 * Manual: tap a device button, then Start / Stop / Play.
 * Automated (adb):
 *   am start -n com.sentinel.mictest/.MainActivity --es device back --ei seconds 10 --es source MIC --es tag back_mic [--ei rate 48000]
 *   device: default | bottom | back | <AudioDeviceInfo id>
 *   source: MIC | VOICE_RECOGNITION | UNPROCESSED | CAMCORDER | VOICE_COMMUNICATION
 * Output: files/<tag>.wav and files/<tag>.json (read with `run-as com.sentinel.mictest`).
 */
public class MainActivity extends Activity {
    static final String TAG = "MicTest";
    static final int[] RATES = {16000, 48000, 44100, 8000};

    TextView log;
    AudioManager am;
    volatile boolean recording;
    Thread worker;
    File lastWav;
    String selDevice = "default";
    String selSource = "MIC";
    int prefRate = 16000;
    final Handler ui = new Handler(Looper.getMainLooper());

    @Override
    protected void onCreate(Bundle b) {
        super.onCreate(b);
        getWindow().addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);
        am = getSystemService(AudioManager.class);

        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        LinearLayout row = new LinearLayout(this);
        root.addView(row);
        addButton(row, "Start", v -> startRec(selDevice, selSource, 30, "manual"));
        addButton(row, "Stop", v -> recording = false);
        addButton(row, "Play", v -> play());
        LinearLayout devRow = new LinearLayout(this);
        root.addView(devRow);
        addButton(devRow, "Default", v -> { selDevice = "default"; out("device=default"); });
        addButton(devRow, "Bottom", v -> { selDevice = "bottom"; out("device=bottom"); });
        addButton(devRow, "Back", v -> { selDevice = "back"; out("device=back"); });
        log = new TextView(this);
        log.setTextIsSelectable(true);
        ScrollView sv = new ScrollView(this);
        sv.addView(log);
        root.addView(sv);
        setContentView(root);

        if (checkSelfPermission(Manifest.permission.RECORD_AUDIO) != PackageManager.PERMISSION_GRANTED) {
            requestPermissions(new String[]{Manifest.permission.RECORD_AUDIO}, 1);
        }
        dumpDevices();
        handleIntent(getIntent());
    }

    @Override
    protected void onNewIntent(Intent i) {
        super.onNewIntent(i);
        handleIntent(i);
    }

    void handleIntent(Intent i) {
        if (i == null || !i.hasExtra("seconds")) return;
        String dev = i.getStringExtra("device");
        String src = i.getStringExtra("source");
        String tag = i.getStringExtra("tag");
        prefRate = i.getIntExtra("rate", 16000);
        startRec(dev == null ? "default" : dev, src == null ? "MIC" : src,
                i.getIntExtra("seconds", 10), tag == null ? "auto" : tag);
    }

    void addButton(LinearLayout parent, String label, android.view.View.OnClickListener l) {
        Button btn = new Button(this);
        btn.setText(label);
        btn.setOnClickListener(l);
        parent.addView(btn);
    }

    void out(String s) {
        Log.i(TAG, s);
        ui.post(() -> log.append(s + "\n"));
    }

    static String typeName(int t) {
        switch (t) {
            case AudioDeviceInfo.TYPE_BUILTIN_MIC: return "BUILTIN_MIC";
            case AudioDeviceInfo.TYPE_WIRED_HEADSET: return "WIRED_HEADSET";
            case AudioDeviceInfo.TYPE_USB_DEVICE: return "USB_DEVICE";
            case AudioDeviceInfo.TYPE_USB_HEADSET: return "USB_HEADSET";
            case AudioDeviceInfo.TYPE_BLUETOOTH_SCO: return "BLUETOOTH_SCO";
            case AudioDeviceInfo.TYPE_TELEPHONY: return "TELEPHONY";
            case AudioDeviceInfo.TYPE_FM_TUNER: return "FM_TUNER";
            case AudioDeviceInfo.TYPE_REMOTE_SUBMIX: return "REMOTE_SUBMIX";
            default: return "TYPE_" + t;
        }
    }

    static String locName(int l) {
        switch (l) {
            case MicrophoneInfo.LOCATION_MAINBODY: return "MAINBODY";
            case MicrophoneInfo.LOCATION_MAINBODY_MOVABLE: return "MAINBODY_MOVABLE";
            case MicrophoneInfo.LOCATION_PERIPHERAL: return "PERIPHERAL";
            default: return "UNKNOWN";
        }
    }

    static String micJson(MicrophoneInfo m) {
        return String.format(java.util.Locale.US,
                "{\"id\":%s,\"address\":%s,\"type\":\"%s\",\"location\":\"%s\",\"group\":%d,\"index_in_group\":%d,"
                        + "\"sensitivity_dbfs\":%.2f,\"directionality\":%d,\"pos\":[%.4f,%.4f,%.4f]}",
                q(m.getDescription()), q(m.getAddress()), typeName(m.getType()), locName(m.getLocation()),
                m.getGroup(), m.getIndexInTheGroup(), m.getSensitivity(), m.getDirectionality(),
                m.getPosition().x, m.getPosition().y, m.getPosition().z);
    }

    static String q(String s) {
        return s == null ? "null" : "\"" + s.replace("\\", "\\\\").replace("\"", "\\\"") + "\"";
    }

    void dumpDevices() {
        StringBuilder sb = new StringBuilder("{\"inputs\":[");
        AudioDeviceInfo[] devs = am.getDevices(AudioManager.GET_DEVICES_INPUTS);
        for (int k = 0; k < devs.length; k++) {
            AudioDeviceInfo d = devs[k];
            String line = String.format("INPUT id=%d type=%s addr=%s name=%s rates=%s chans=%s",
                    d.getId(), typeName(d.getType()), d.getAddress(), d.getProductName(),
                    java.util.Arrays.toString(d.getSampleRates()), java.util.Arrays.toString(d.getChannelCounts()));
            out(line);
            if (k > 0) sb.append(',');
            sb.append(String.format("{\"id\":%d,\"type\":\"%s\",\"address\":%s,\"name\":%s}",
                    d.getId(), typeName(d.getType()), q(d.getAddress()), q(String.valueOf(d.getProductName()))));
        }
        sb.append("],\"microphones\":[");
        try {
            List<MicrophoneInfo> mics = am.getMicrophones();
            for (int k = 0; k < mics.size(); k++) {
                String j = micJson(mics.get(k));
                out("MIC " + j);
                if (k > 0) sb.append(',');
                sb.append(j);
            }
        } catch (IOException e) {
            out("getMicrophones failed: " + e);
        }
        sb.append("]}");
        writeText(new File(getFilesDir(), "devices.json"), sb.toString());
    }

    AudioDeviceInfo pickDevice(String sel) {
        if (sel == null || sel.equals("default")) return null;
        for (AudioDeviceInfo d : am.getDevices(AudioManager.GET_DEVICES_INPUTS)) {
            if (String.valueOf(d.getId()).equals(sel) || sel.equalsIgnoreCase(d.getAddress())) return d;
        }
        out("WARN: no input device matches '" + sel + "', using default");
        return null;
    }

    static int sourceId(String s) {
        switch (s) {
            case "VOICE_RECOGNITION": return MediaRecorder.AudioSource.VOICE_RECOGNITION;
            case "UNPROCESSED": return MediaRecorder.AudioSource.UNPROCESSED;
            case "CAMCORDER": return MediaRecorder.AudioSource.CAMCORDER;
            case "VOICE_COMMUNICATION": return MediaRecorder.AudioSource.VOICE_COMMUNICATION;
            default: return MediaRecorder.AudioSource.MIC;
        }
    }

    void startRec(String devSel, String src, int seconds, String tag) {
        if (recording) { out("already recording"); return; }
        if (checkSelfPermission(Manifest.permission.RECORD_AUDIO) != PackageManager.PERMISSION_GRANTED) {
            out("ERROR: RECORD_AUDIO not granted");
            return;
        }
        recording = true;
        worker = new Thread(() -> record(devSel, src, seconds, tag), "rec");
        worker.start();
    }

    void record(String devSel, String src, int seconds, String tag) {
        AudioRecord rec = null;
        int rate = 0;
        int minBuf = 0;
        int[] order = new int[RATES.length + 1];
        order[0] = prefRate;
        System.arraycopy(RATES, 0, order, 1, RATES.length);
        for (int r : order) {
            minBuf = AudioRecord.getMinBufferSize(r, AudioFormat.CHANNEL_IN_MONO, AudioFormat.ENCODING_PCM_16BIT);
            if (minBuf <= 0) { out("rate " + r + " unsupported (minBuf=" + minBuf + ")"); continue; }
            try {
                AudioRecord a = new AudioRecord(sourceId(src), r, AudioFormat.CHANNEL_IN_MONO,
                        AudioFormat.ENCODING_PCM_16BIT, Math.max(minBuf, r / 5 * 2));
                if (a.getState() == AudioRecord.STATE_INITIALIZED) { rec = a; rate = r; break; }
                out("rate " + r + " init failed state=" + a.getState());
                a.release();
            } catch (Exception e) {
                out("rate " + r + " exception " + e);
            }
        }
        if (rec == null) { out("ERROR: AudioRecord could not be initialized"); recording = false; return; }

        AudioDeviceInfo pref = pickDevice(devSel);
        boolean prefOk = pref == null || rec.setPreferredDevice(pref);
        out(String.format("INIT tag=%s source=%s requestedDevice=%s preferredSet=%b sampleRate=%d minBuf=%d",
                tag, src, devSel, prefOk, rec.getSampleRate(), minBuf));

        File wav = new File(getFilesDir(), tag + ".wav");
        long total = 0, sumSq = 0;
        int peak = 0, readErrors = 0, nearSilent = 0;
        int lastErr = 0;
        String routed = "unknown";
        StringBuilder active = new StringBuilder();
        try (RandomAccessFile f = new RandomAccessFile(wav, "rw")) {
            f.setLength(0);
            f.write(new byte[44]);
            short[] buf = new short[rate / 10];
            byte[] bytes = new byte[buf.length * 2];
            rec.startRecording();
            out("recordingState=" + rec.getRecordingState());
            long target = (long) rate * seconds;
            long t0 = System.currentTimeMillis();
            while (recording && total < target) {
                int n = rec.read(buf, 0, buf.length);
                if (n < 0) {
                    readErrors++;
                    lastErr = n;
                    if (readErrors > 20) { out("too many read errors, last=" + n); break; }
                    continue;
                }
                for (int k = 0; k < n; k++) {
                    int s = buf[k];
                    int a = Math.abs(s);
                    if (a > peak) peak = a;
                    if (a < 33) nearSilent++; // below ~-60 dBFS
                    sumSq += (long) s * s;
                    bytes[2 * k] = (byte) (s & 0xff);
                    bytes[2 * k + 1] = (byte) ((s >> 8) & 0xff);
                }
                f.write(bytes, 0, n * 2);
                total += n;
                if (routed.equals("unknown") && total > rate / 2) {
                    AudioDeviceInfo rd = rec.getRoutedDevice();
                    if (rd != null) routed = rd.getId() + ":" + typeName(rd.getType()) + ":" + rd.getAddress();
                    try {
                        for (MicrophoneInfo m : rec.getActiveMicrophones()) {
                            if (active.length() > 0) active.append(',');
                            active.append(micJson(m));
                        }
                    } catch (IOException e) {
                        out("getActiveMicrophones failed: " + e);
                    }
                    out("ROUTED " + routed + " activeMics=[" + active + "]");
                }
            }
            rec.stop();
            out("elapsed ms=" + (System.currentTimeMillis() - t0));
            writeWavHeader(f, rate, total);
        } catch (IOException e) {
            out("ERROR io " + e);
        } finally {
            rec.release();
            recording = false;
        }
        lastWav = wav;
        double rms = total > 0 ? Math.sqrt((double) sumSq / total) : 0;
        double rmsDb = rms > 0 ? 20 * Math.log10(rms / 32768.0) : -999;
        double peakDb = peak > 0 ? 20 * Math.log10(peak / 32768.0) : -999;
        String json = String.format(java.util.Locale.US,
                "{\"tag\":%s,\"source\":%s,\"requested_device\":%s,\"preferred_set\":%b,\"routed_device\":%s,"
                        + "\"active_microphones\":[%s],\"sample_rate\":%d,\"samples\":%d,\"read_errors\":%d,\"last_error\":%d,"
                        + "\"rms\":%.2f,\"rms_dbfs\":%.2f,\"peak\":%d,\"peak_dbfs\":%.2f,\"near_silent_pct\":%.2f}",
                q(tag), q(src), q(devSel), prefOk, q(routed), active, rate, total, readErrors, lastErr,
                rms, rmsDb, peak, peakDb, total > 0 ? 100.0 * nearSilent / total : 0);
        writeText(new File(getFilesDir(), tag + ".json"), json);
        out("RESULT " + json);
        out("DONE " + wav.getAbsolutePath());
    }

    static void writeWavHeader(RandomAccessFile f, int rate, long samples) throws IOException {
        long dataLen = samples * 2;
        java.nio.ByteBuffer h = java.nio.ByteBuffer.allocate(44).order(java.nio.ByteOrder.LITTLE_ENDIAN);
        h.put("RIFF".getBytes()).putInt((int) (36 + dataLen)).put("WAVE".getBytes());
        h.put("fmt ".getBytes()).putInt(16).putShort((short) 1).putShort((short) 1)
                .putInt(rate).putInt(rate * 2).putShort((short) 2).putShort((short) 16);
        h.put("data".getBytes()).putInt((int) dataLen);
        f.seek(0);
        f.write(h.array());
    }

    void writeText(File file, String s) {
        try (FileOutputStream o = new FileOutputStream(file)) {
            o.write(s.getBytes());
        } catch (IOException e) {
            out("write failed " + e);
        }
    }

    void play() {
        if (lastWav == null || !lastWav.exists()) { out("nothing to play"); return; }
        try {
            MediaPlayer mp = new MediaPlayer();
            mp.setAudioAttributes(new AudioAttributes.Builder().setUsage(AudioAttributes.USAGE_MEDIA).build());
            mp.setDataSource(lastWav.getAbsolutePath());
            mp.setOnCompletionListener(MediaPlayer::release);
            mp.prepare();
            mp.start();
            out("playing " + lastWav.getName());
        } catch (IOException e) {
            out("play failed " + e);
        }
    }
}
