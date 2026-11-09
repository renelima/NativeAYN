package dev.nxnative.android;

import android.content.Context;
import android.widget.LinearLayout;
import android.widget.TextView;

import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.util.List;

/**
 * FSR 1 upscaling at the present (DM_VK_FSR, DM_VK_FSR_SHARP: dm/gfx/vk_android/vka_present.c), set from the in-game panel. The choice is kept for the next starts (env)
 * and sent to the running game as lines of its live control file (DM_CTL, android/TESTING.md "Live control"), which the host reads within 100 ms and applies at the next
 * frame; with DM_CTL= in env.txt (no reader) it takes effect at the next start
 */
final class Fsr {
    private Fsr() {}

    /** RCAS sharpness in stops: 0 is the strongest, each stop halves it (AMD's default 0.25) */
    static final float[] SHARPS = {0f, 0.25f, 0.5f, 1f};

    static boolean on(Context c) { return Prefs.of(c).getBoolean("fsr", false); }
    static float sharp(Context c) { return Prefs.of(c).getFloat("fsr_sharp", 0.25f); }

    /** The host's switches for a run (env.txt lines come after these and win) */
    static void env(Context c, List<String> env) {
        env.add("DM_VK_FSR=" + (on(c) ? 1 : 0));
        env.add("DM_VK_FSR_SHARP=" + sharp(c));
    }

    private static void push(Context c) {
        File ext = c.getExternalFilesDir(null);   // GameActivity's DM_CTL: <app files>/ctl.txt
        if (ext == null) return;
        try (FileWriter w = new FileWriter(new File(ext, "ctl.txt"), true)) {
            w.write("env DM_VK_FSR=" + (on(c) ? 1 : 0) + "\nenv DM_VK_FSR_SHARP=" + sharp(c) + "\n");
        } catch (IOException e) { Native.note("[RED] FSR: the setting could not be sent to the game (" + e.getMessage() + "); it applies at the next start"); }
    }

    /** The in-game panel's rows: on / off and the sharpness, each applied at once. showHead adds the section label (the panel draws its own) */
    static void rows(Context c, LinearLayout box, boolean showHead) {
        if (showHead) box.addView(Ui.section(c, "Upscaling"));
        box.addView(Ui.toggle(c, "FSR 1 (sharper when the picture is scaled up to the screen)", on(c),
                (b, v) -> { Prefs.of(c).edit().putBoolean("fsr", v).commit(); push(c); }));
        box.addView(Ui.body(c, "Sharpness"));
        String[] labels = {"Max", "Default", "Soft", "Softer"};
        int cur = 1;
        for (int i = 0; i < SHARPS.length; i++) if (SHARPS[i] == sharp(c)) cur = i;
        box.addView(Ui.pillGroup(c, labels, cur,
                (gr, id) -> { Prefs.of(c).edit().putFloat("fsr_sharp", SHARPS[id - 1000]).commit(); push(c); }));
    }
}
