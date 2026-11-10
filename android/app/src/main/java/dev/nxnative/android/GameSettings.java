package dev.nxnative.android;

import android.app.AlertDialog;
import android.text.InputType;
import android.widget.EditText;
import android.widget.LinearLayout;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

/**
 * A game's own settings (its Manage sheet › Game settings…): resolution, frame rate cap, GPU driver, GPU timing and extra environment lines, stored per title ID (Prefs.gameKey,
 * GpuDriver.choice). Each starts as "Default", which follows the main screen's Settings (global); a value set here wins for this game only. The in-game panel's frame
 * rate cap is this game's too. GameActivity reads them at start.
 */
final class GameSettings {
    private GameSettings() {}

    static void dialog(MainActivity a, Game g) {
        LinearLayout box = new LinearLayout(a);
        box.setOrientation(LinearLayout.VERTICAL);
        AlertDialog[] d = new AlertDialog[1];
        Runnable again = () -> { d[0].dismiss(); dialog(a, g); };
        String id = g.titleId;
        box.addView(Ui.small(a, Lang.t(a, "For this game only, from its next start. Default follows Settings.")));
        Profile prof = Profile.of(id);
        if (prof != null && prof.hasResolution())   // only where it does something: a profile with window_fn or graphics_scale rows
            Ui.add(box, new Ui.Setting(a, "🖼️", Lang.t(a, "Game resolution"), v -> resolution(a, g, again)).value(windowLabel(a, id)).chevron(a).view, 8);
        if (Prefs.ownRate(a, id)) {   // the host found no limiter it can hold for this game: choices would do nothing
            Ui.Setting fixed = new Ui.Setting(a, "⏱️", Lang.t(a, "Frame rate cap"), null).value(Lang.t(a, "This game picks its own frame rate"));
            fixed.view.setClickable(false);
            fixed.view.setAlpha(0.6f);
            Ui.add(box, fixed.view, 0);
        } else Ui.add(box, new Ui.Setting(a, "⏱️", Lang.t(a, "Frame rate cap"), v -> cap(a, g, again)).value(capLabel(a, id)).chevron(a).view, 0);
        Ui.add(box, new Ui.Setting(a, "🎮", Lang.t(a, "GPU driver"), v -> driver(a, g, again)).value(driverLabel(a, id)).chevron(a).view, 0);
        Ui.add(box, new Ui.Setting(a, "⏲️", Lang.t(a, "GPU timing"), v -> gpuTiming(a, g, again)).value(gpuTsLabel(a, id)).chevron(a).view, 0);
        Ui.Setting ret = new Ui.Setting(a, "⏸️", Lang.t(a, "Pause on return"), null).toggle(a, Prefs.pauseOnReturn(a, id), (b, on) -> Prefs.pauseOnReturn(a, id, on));
        ret.value(Lang.t(a, "Coming back from the background, the game waits paused for a tap"));
        Ui.add(box, ret.view, 0);
        Ui.Setting skip = new Ui.Setting(a, "⏩", Lang.t(a, "Intro skip (debug)"), null).toggle(a, Prefs.introSkip(a, id), (b, on) -> Prefs.introSkip(a, id, on));
        skip.value(Lang.t(a, "In the game, hold − and + together for 1 s: presses A through movies and menus, ends movies early, stops at gameplay. Hold again to stop"));
        Ui.add(box, skip.view, 0);
        Ui.Setting ram = new Ui.Setting(a, "💾", Lang.t(a, "RAM saver (uses storage)"), null).toggle(a, Prefs.ramSaver(a, id), (b, on) -> Prefs.ramSaver(a, id, on));
        ram.value(Lang.t(a, "Keeps the game's texture data in a file instead of compressed RAM: about 1 GB more free RAM where the phone runs short, but each area load writes ")
                + Lang.t(a, "that data to storage (about 2 GB in Ocarina of Time) and needs that much free space. Off: as before"));
        Ui.add(box, ram.view, 0);
        if (prof != null && prof.hasScene()) {   // only where the profile names the palette's scale (game.tsv scene_scale_palette; SceneOverlay)
            boolean sceneOn = Prefs.scene720(a, id);
            Ui.Setting sc = new Ui.Setting(a, "🎞️", Lang.t(a, "Scene resolution"), v -> scene(a, g, again))
                    .value(Lang.t(a, "Game default (the game's own palette)") + Lang.t(a, " · 720p patches that palette: the scene costs less, the upscale stays sharp"));
            if (sceneOn) sc.value(Lang.t(a, "720p"));
            Ui.add(box, sc.chevron(a).view, 0);
            if (sceneOn) sceneCheck(a, g, prof, sc);
        }
        String env = Prefs.gameEnv(a, id);
        Ui.add(box, new Ui.Setting(a, "🧪", Lang.t(a, "Extra environment"), v -> env(a, g, again))
                .value(env.isEmpty() ? Lang.t(a, "None (KEY=VALUE lines for the host, after env.txt)") : env.split("\n").length + Lang.t(a, " line(s): ") + env.replace('\n', ' ')).chevron(a).view, 0);
        d[0] = Ui.sheet(a, Lang.t(a, "Game settings · ") + g.name, box).setPositiveButton(Lang.t(a, "Done"), null).show();
    }

    static String windowLabel(MainActivity a, String id) {
        String w = Prefs.gameWindow(a, id);
        String l = Prefs.windowName(w, false);
        return Prefs.ownWindow(a, id) ? l : Lang.t(a, "Default · ") + l;
    }

    static String capLabel(MainActivity a, String id) {
        String l = MainActivity.capLabel(Prefs.fpsCap(a, id));
        return Prefs.ownFpsCap(a, id) ? l : Lang.t(a, "Default · ") + l;
    }

    static String driverLabel(MainActivity a, String id) {
        GpuDriver.Info i = GpuDriver.selected(a, id);
        String l = i == null ? Lang.t(a, "System (the phone's own driver)") : i.label();
        return GpuDriver.choice(a, id) != null ? l : Lang.t(a, "Default · ") + l;
    }

    static final String[] GPU_TS = {"segment", "submission"};

    private static String gpuTsName(android.content.Context c, String mode) { return mode.equals("segment") ? Lang.t(c, "Per segment") : Lang.t(c, "Per submission"); }

    static String gpuTsLabel(MainActivity a, String id) {
        String m = Prefs.gpuTs(a, id);
        Profile p = Profile.of(id);
        return m != null ? gpuTsName(a, m) : Lang.t(a, "Game default · ") + gpuTsName(a, p == null ? "submission" : p.gpuTs);
    }

    /**
     * GPU timing: what the game's GPU timestamps measure (DM_NVN_GPU_TS; game.tsv gpu_timestamps is the default). Per segment: each timestamp gets the GPU time of
     * its place in the submission, so a game's dynamic resolution and frame pacing see the real GPU time; per submission: every timestamp of a submission gets
     * its completion time (the older behaviour)
     */
    private static void gpuTiming(MainActivity a, Game g, Runnable done) {
        Profile p = Profile.of(g.titleId);
        String[] labels = {Lang.t(a, "Game default (") + gpuTsName(a, p == null ? "submission" : p.gpuTs) + ")", Lang.t(a, "Per segment"), Lang.t(a, "Per submission")};
        String m = Prefs.gpuTs(a, g.titleId);
        int checked = m == null ? 0 : 1 + Math.max(0, Arrays.asList(GPU_TS).indexOf(m));
        LinearLayout box = new LinearLayout(a);
        box.setOrientation(LinearLayout.VERTICAL);
        AlertDialog[] d = new AlertDialog[1];
        box.addView(Ui.small(a, Lang.t(a, "Per segment: the game's timestamps see the real GPU time, so its dynamic resolution can adapt. Per submission: one time for the whole ")
                + Lang.t(a, "submission (the older behaviour). A DM_NVN_GPU_TS line in env.txt wins.")));
        android.widget.RadioGroup rg = new android.widget.RadioGroup(a);
        for (int k = 0; k < labels.length; k++) {
            android.widget.RadioButton rb = new android.widget.RadioButton(a);
            rb.setText(labels[k]);
            rb.setId(k + 1);
            rg.addView(rb);
        }
        rg.check(checked + 1);
        rg.setOnCheckedChangeListener((x, w) -> {
            Prefs.gpuTs(a, g.titleId, w <= 1 ? null : GPU_TS[w - 2]);
            d[0].dismiss();
            done.run();
        });
        Ui.add(box, rg, 8);
        d[0] = Ui.sheet(a, Lang.t(a, "GPU timing · ") + g.name, box).setNegativeButton(Lang.t(a, "Cancel"), null).show();
    }

    /** Scene resolution: the game's own palette (the default) or 720p, whose RomFS overlay is made from the player's files by SceneService when it is not there yet */
    private static void scene(MainActivity a, Game g, Runnable done) {
        Profile prof = Profile.of(g.titleId);
        if (prof == null || !prof.hasScene()) return;
        String[] labels = {Lang.t(a, "Game default (the game's own palette)"), Lang.t(a, "720p")};
        int checked = Prefs.scene720(a, g.titleId) ? 1 : 0;
        new AlertDialog.Builder(a).setTitle(Lang.t(a, "Scene resolution · ") + g.name).setSingleChoiceItems(labels, checked, (x, w) -> {
            x.dismiss();
            if (w == 0) { sceneWant(g.titleId, false); Prefs.scene720(a, g.titleId, false); done.run(); }
            else if (!Ondevice.available()) a.error(Lang.t(a, "This app build has no on-phone packer, so it cannot make the overlay: run the game's own scene resolution"));
            else makeScene(a, g, prof, done);
        }).setNegativeButton(Lang.t(a, "Cancel"), null).show();
    }

    /** The pack's version and the RomFS as the host takes it ({version, romfs}), or null with no pack or RomFS. Reads pack.tsv and the .nup: off the main thread */
    private static String[] sceneInputs(Game g) {
        try {
            Pack p = Pack.read(new File(g.packDir));
            String romfs = p.romfsPath(new File(g.gameDir));
            return romfs == null ? null : new String[] {p.version, romfs};
        } catch (IOException | RuntimeException e) { return null; }
    }

    /** A 720p row whose overlay no longer matches the pack (a game update, another scale in the profile): the row says so; choosing 720p again makes it again */
    private static void sceneCheck(MainActivity a, Game g, Profile prof, Ui.Setting row) {
        File files = a.getExternalFilesDir(null);
        new Thread(() -> {
            String[] in = sceneInputs(g);
            boolean ok = files != null && in != null && SceneOverlay.current(files, g.titleId, prof.sceneScale(), prof.sceneExpect(), in[0]);
            if (!ok) a.runOnUiThread(() -> row.value(Lang.t(a, "720p (the overlay no longer matches this game: choose 720p again to make it)")));
        }, "nx-scene-check").start();
    }

    /**
     * The player's last Scene resolution choice per game in this process (true: 720p): an overlay's answer turns 720p on only while that is still the choice, so
     * "Game default" while it is being made wins, and a second 720p (answered RUNNING) is turned on by the first run's answer
     */
    private static final java.util.Map<String, Boolean> SCENE_WANT = new java.util.HashMap<>();

    private static void sceneWant(String id, boolean on) {
        synchronized (SCENE_WANT) { SCENE_WANT.put(id, on); }
    }

    private static boolean sceneWanted(String id) {
        synchronized (SCENE_WANT) { return Boolean.TRUE.equals(SCENE_WANT.get(id)); }
    }

    /**
     * 720p chosen: the overlay is made from the player's own RomFS by gsys_dynres.py in the packer's process (SceneService) unless the one there already matches,
     * behind a wait dialog; the setting turns on only once the overlay is there. Started while the launcher is in front; one run at a time, and behind a Prepare step when one runs
     */
    private static void makeScene(MainActivity a, Game g, Profile prof, Runnable done) {
        File files = a.getExternalFilesDir(null);
        sceneWant(g.titleId, true);
        // Back closes the dialog (the :pack process could end without an answer: the first run there also unpacks the packer); the answer still turns 720p on and says so
        AlertDialog wait = new AlertDialog.Builder(a).setMessage(Lang.t(a, "Making the 720p scene overlay…")).setCancelable(true).create();
        wait.show();
        String id = g.titleId, scale = prof.sceneScale(), expect = prof.sceneExpect();
        new Thread(() -> {
            String[] in = sceneInputs(g);
            boolean have = files != null && in != null && SceneOverlay.current(files, id, scale, expect, in[0]);
            a.runOnUiThread(() -> {
                boolean gone = a.isFinishing() || a.isDestroyed();
                if (in == null || files == null || have) {
                    if (!gone) { try { wait.dismiss(); } catch (RuntimeException ignored) { /* its window is gone */ } }
                    if (have) { if (sceneWanted(id)) { Prefs.scene720(a, id, true); toast(a, Lang.t(a, "The 720p scene overlay is ready")); } }
                    else if (gone) toast(a, Lang.t(a, "Scene 720p failed: this game has no pack or RomFS to make the overlay from"));
                    else a.error(Lang.t(a, "Scene 720p failed: this game has no pack or RomFS to make the overlay from"));
                    if (!gone && sceneWanted(id)) done.run();
                    return;
                }
                SceneService.start(a, id, in[1], scale, expect, in[0], new android.os.ResultReceiver(new android.os.Handler(android.os.Looper.getMainLooper())) {
                    @Override protected void onReceiveResult(int code, android.os.Bundle data) {
                        String msg = data == null ? null : data.getString(SceneService.MESSAGE);
                        boolean end = a.isFinishing() || a.isDestroyed();
                        if (!end) { try { wait.dismiss(); } catch (RuntimeException ignored) { /* its window is gone */ } }
                        if (code == 0) {
                            // made, but turned on only when the player has not chosen otherwise meanwhile (the overlay stays: a later 720p takes it at once)
                            if (sceneWanted(id)) { Prefs.scene720(a, id, true); toast(a, Lang.t(a, "The 720p scene overlay is ready")); }
                        } else if (code == SceneService.RUNNING) {
                            toast(a, Lang.t(a, "The 720p scene overlay is already being made: it turns on when it is ready"));
                        } else {
                            String why = msg != null ? msg : "exit " + code;
                            if (end) toast(a, Lang.t(a, "Scene 720p failed: ") + why);
                            else a.error(Lang.t(a, "Scene 720p failed: ") + why);
                        }
                        if (!end) done.run();
                    }
                });
            });
        }, "nx-scene-make").start();
    }

    private static void toast(MainActivity a, String msg) {
        a.runOnUiThread(() -> android.widget.Toast.makeText(a, msg, android.widget.Toast.LENGTH_LONG).show());
    }

    private static void resolution(MainActivity a, Game g, Runnable done) {
        String[] labels = new String[Prefs.WINDOWS.length + 1];
        labels[0] = Lang.t(a, "Default (") + Prefs.windowName(Prefs.defaultWindow(a, g.titleId), false) + ")";
        System.arraycopy(Prefs.WINDOW_NAMES, 0, labels, 1, Prefs.WINDOW_NAMES.length);
        int checked = Prefs.ownWindow(a, g.titleId) ? 1 + Math.max(0, Arrays.asList(Prefs.WINDOWS).indexOf(Prefs.gameWindow(a, g.titleId))) : 0;
        new AlertDialog.Builder(a).setTitle(Lang.t(a, "Game resolution · ") + g.name).setSingleChoiceItems(labels, checked, (x, w) -> {
            Prefs.gameWindow(a, g.titleId, w == 0 ? null : Prefs.WINDOWS[w - 1]);
            x.dismiss();
            done.run();
        }).setNegativeButton(Lang.t(a, "Cancel"), null).show();
    }

    private static void cap(MainActivity a, Game g, Runnable done) {
        int n = Prefs.FPS_CAPS.length;
        String[] labels = new String[n + 2];
        labels[0] = Lang.t(a, "Default (Settings: ") + MainActivity.capLabel(Prefs.fpsCap(a)) + ")";
        labels[1] = MainActivity.capLabel(-1);
        int checked = 0;
        boolean own = Prefs.ownFpsCap(a, g.titleId);
        int cur = Prefs.fpsCap(a, g.titleId);
        if (own && cur < 0) checked = 1;
        for (int k = 0; k < n; k++) {
            labels[k + 2] = MainActivity.capLabel(Prefs.FPS_CAPS[k]);
            if (own && Prefs.FPS_CAPS[k] == cur) checked = k + 2;
        }
        new AlertDialog.Builder(a).setTitle(Lang.t(a, "Frame rate cap · ") + g.name).setSingleChoiceItems(labels, checked, (x, w) -> {
            Prefs.fpsCap(a, g.titleId, w == 0 ? null : w == 1 ? -1 : Prefs.FPS_CAPS[w - 2]);
            x.dismiss();
            done.run();
        }).setNegativeButton(Lang.t(a, "Cancel"), null).show();
    }

    private static void driver(MainActivity a, Game g, Runnable done) {
        List<GpuDriver.Info> inst = GpuDriver.installed(a);
        GpuDriver.Info def = GpuDriver.selected(a);
        List<String> labels = new ArrayList<>();
        labels.add(Lang.t(a, "Default (the main screen's: ") + (def == null ? Lang.t(a, "System") : def.label()) + ")");
        labels.add(Lang.t(a, "System (the phone's own driver)"));
        String c = GpuDriver.choice(a, g.titleId);
        int checked = c == null ? 0 : c.equals(GpuDriver.SYSTEM) ? 1 : 0;
        for (int k = 0; k < inst.size(); k++) {
            labels.add(inst.get(k).label());
            if (c != null && inst.get(k).dir.getName().equals(c)) checked = k + 2;
        }
        new AlertDialog.Builder(a).setTitle(Lang.t(a, "GPU driver · ") + g.name).setSingleChoiceItems(labels.toArray(new String[0]), checked, (x, w) -> {
            GpuDriver.choice(a, g.titleId, w == 0 ? null : w == 1 ? GpuDriver.SYSTEM : inst.get(w - 2).dir.getName());
            x.dismiss();
            done.run();
        }).setNegativeButton(Lang.t(a, "Cancel"), null).show();
    }

    private static void env(MainActivity a, Game g, Runnable done) {
        EditText t = new EditText(a);
        t.setInputType(InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_FLAG_MULTI_LINE | InputType.TYPE_TEXT_FLAG_NO_SUGGESTIONS);
        t.setMinLines(4);
        t.setTypeface(android.graphics.Typeface.MONOSPACE);
        t.setTextSize(13);
        t.setHint("DM_WATCH_STALL=25");
        t.setText(Prefs.gameEnv(a, g.titleId));
        LinearLayout box = new LinearLayout(a);
        box.setOrientation(LinearLayout.VERTICAL);
        box.addView(Ui.small(a, Lang.t(a, "KEY=VALUE lines for the host, one a line (# starts a comment). They come after env.txt and win over it, for ") + g.name + Lang.t(a, " only.")));
        Ui.add(box, t, 8);
        Ui.sheet(a, Lang.t(a, "Extra environment · ") + g.name, box).setPositiveButton(Lang.t(a, "Save"), (x, w) -> { Prefs.gameEnv(a, g.titleId, t.getText().toString()); done.run(); })
                .setNeutralButton(Lang.t(a, "Clear"), (x, w) -> { Prefs.gameEnv(a, g.titleId, null); done.run(); }).setNegativeButton(Lang.t(a, "Cancel"), null).show();
    }
}
