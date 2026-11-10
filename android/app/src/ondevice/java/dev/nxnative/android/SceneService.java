package dev.nxnative.android;

import android.app.Service;
import android.content.Context;
import android.content.Intent;
import android.os.Bundle;
import android.os.IBinder;
import android.os.ResultReceiver;
import android.util.Log;

import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.StandardOpenOption;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.List;
import java.util.Locale;

/**
 * Makes the Scene resolution's 720p overlay (SceneOverlay) in the packer's process (:pack, where the embedded Python runs; the launcher's process never loads it):
 * dm/tools/gsys_dynres.py on the player's RomFS, a few seconds (the first run in the process also unpacks the packer). Started by the game's settings with
 * startService while the launcher is in front; one run at a time. A Prepare step may be using the interpreter: libnxpack serializes the runs (dm_pack.c's lock),
 * so this one waits behind it. It writes into scene_720p.tmp/, which becomes scene_720p/ with its stamp once the tool has checked its own output, and answers on
 * the caller's ResultReceiver: 0 and "message" the overlay's folder, else the exit code (or RUNNING) and "message" why (SceneOverlay.why from its log).
 */
public final class SceneService extends Service {
    static final String EXTRA_ID = "title_id", EXTRA_ROMFS = "romfs", EXTRA_SCALE = "scale", EXTRA_EXPECT = "expect", EXTRA_VERSION = "version",
            EXTRA_RECEIVER = "receiver", MESSAGE = "message";
    /** Another SceneService run is still making an overlay (one at a time: they share the tool and their folders) */
    static final int RUNNING = 76;
    private static final java.util.concurrent.atomic.AtomicBoolean ACTIVE = new java.util.concurrent.atomic.AtomicBoolean();
    private static final String TAG = "nxscene", TOOL = "dm/tools/gsys_dynres.py";

    /** Starts a run (the launcher, in front); the answer comes on r */
    static void start(Context c, String titleId, String romfs, String scale, String expect, String version, ResultReceiver r) {
        c.startService(new Intent(c, SceneService.class).putExtra(EXTRA_ID, titleId).putExtra(EXTRA_ROMFS, romfs).putExtra(EXTRA_SCALE, scale)
                .putExtra(EXTRA_EXPECT, expect).putExtra(EXTRA_VERSION, version).putExtra(EXTRA_RECEIVER, r));
    }

    @Override public IBinder onBind(Intent i) { return null; }

    @Override public int onStartCommand(Intent intent, int flags, int startId) {
        if (intent == null) { stopSelf(startId); return START_NOT_STICKY; }
        new Thread(() -> {
            ResultReceiver r = intent.getParcelableExtra(EXTRA_RECEIVER, ResultReceiver.class);
            int code;
            String msg;
            try {
                String[] got = run(intent.getStringExtra(EXTRA_ID), intent.getStringExtra(EXTRA_ROMFS), intent.getStringExtra(EXTRA_SCALE),
                        intent.getStringExtra(EXTRA_EXPECT), intent.getStringExtra(EXTRA_VERSION));
                code = Integer.parseInt(got[0]);
                msg = got[1];
            } catch (Throwable e) {   // never this process's end: a Prepare may share it
                Log.e(TAG, "scene overlay", e);
                code = 1;
                msg = String.valueOf(e);
            }
            Bundle b = new Bundle();
            b.putString(MESSAGE, msg);
            if (r != null) r.send(code, b);
            stopSelf(startId);
        }, "nx-scene").start();
        return START_NOT_STICKY;
    }

    /** {exit code, message}: RUNNING before any file work while another run is on (it owns scene_720p.tmp/ and the log) */
    private String[] run(String id, String romfs, String scale, String expect, String version) throws IOException {
        if (id == null || romfs == null || scale == null || version == null) return new String[] {"2", "incomplete request"};
        if (!ACTIVE.compareAndSet(false, true)) return new String[] {String.valueOf(RUNNING), "the 720p overlay is already being made"};
        try { return runOne(id, romfs, scale, expect, version); } finally { ACTIVE.set(false); }
    }

    private String[] runOne(String id, String romfs, String scale, String expect, String version) throws IOException {
        if (expect == null) expect = "";
        File files = getExternalFilesDir(null);
        if (files == null) return new String[] {"1", "the app's files folder is not available"};
        File out = SceneOverlay.dir(files, id), tmp = new File(out.getPath() + ".tmp"), stamp = SceneOverlay.stamp(files, id), log = SceneOverlay.log(files, id);
        Job.deleteTree(tmp);
        if (!tmp.mkdirs()) return new String[] {"1", "cannot create " + tmp};
        String[] argv = SceneOverlay.argv(romfs, scale, expect, tmp);
        String head = "== " + new SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.ROOT).format(new Date()) + " " + Prefs.buildTag(this) + ": " + TOOL + " " + String.join(" ", argv) + "\n";
        Files.write(log.toPath(), head.getBytes(StandardCharsets.UTF_8), StandardOpenOption.CREATE, StandardOpenOption.TRUNCATE_EXISTING);
        PackEngine e = PackEngine.create(this);
        PackEngine.setenv("TMPDIR", getCacheDir().getPath());
        int code = e.run(TOOL, argv, log.getPath());   // the native engine waits behind a running Prepare step (dm_pack.c serializes)
        Log.i(TAG, TOOL + ": exit " + code + " (log " + log + ")");
        String[] made = tmp.list();
        if (code != 0 || made == null || made.length == 0) {
            Job.deleteTree(tmp);
            List<String> l = Files.readAllLines(log.toPath(), StandardCharsets.UTF_8);
            String why = SceneOverlay.why(l);
            return new String[] {String.valueOf(code == 0 ? 1 : code), (why != null ? why : "exit " + code) + " (log: " + log + ")"};
        }
        // the stamp goes first, so a folder without a matching stamp is never taken (SceneOverlay.current), then the new folder in place of the old
        stamp.delete();
        Job.deleteTree(out);
        if (!tmp.renameTo(out)) return new String[] {"1", "cannot rename " + tmp + " to " + out};
        Files.write(stamp.toPath(), SceneOverlay.stampText(scale, expect, version).getBytes(StandardCharsets.UTF_8));
        return new String[] {"0", out.getPath()};
    }
}
