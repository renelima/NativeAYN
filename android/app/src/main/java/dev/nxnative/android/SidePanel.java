package dev.nxnative.android;

import android.app.Activity;
import android.app.AlertDialog;
import android.graphics.drawable.GradientDrawable;
import android.view.Gravity;
import android.view.KeyEvent;
import android.view.View;
import android.widget.FrameLayout;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;

/**
 * The in-game menu: slides in from the right over the running game, which goes on behind it. It is a game pause menu in look and shape — one row per setting, each with an
 * icon tile, its name, its current state as a subtitle and a chevron; the row opens a dialog with the full controls (the frame rate cap, frame generation (Lsfg.rows),
 * FSR 1 upscaling (Fsr.rows), the overlay's items (Overlay.rows), the on-screen controls' rows (TouchControls.controlRows), the controller mapping (ControllerControls),
 * the intro skip and Pause / Stop). Every change applies at once, without going back to the launcher, and is kept for the next start. Opened by its handle at the right
 * edge, by Back (the phone's button or its edge swipe) or a gamepad's Home / guide button (KEYCODE_BUTTON_MODE); closed the same ways, by a tap beside it, or by B on a
 * gamepad, whose D-pad and A move through it meanwhile (GameActivity.dispatchKeyEvent).
 */
final class SidePanel extends FrameLayout {
    private final Activity act;
    private final LinearLayout box;
    private final View sheet;
    private final java.util.function.Consumer<LinearLayout> fill;   // GameActivity: TouchControls.controlRows, which the on-screen controls dialog reuses
    private final TextView[] capSub = new TextView[1], genSub = new TextView[1], upSub = new TextView[1], ovlSub = new TextView[1], ctlSub = new TextView[1], padSub = new TextView[1], skipSub = new TextView[1], gameSub = new TextView[1];
    private boolean open;

    SidePanel(Activity a, java.util.function.Consumer<LinearLayout> fill) {
        super(a);
        act = a;
        this.fill = fill;
        float dp = getResources().getDisplayMetrics().density;
        View scrim = new View(a);
        scrim.setBackgroundColor(0x8A04070D);   // a deeper navy dim: the game reads as "paused behind a menu"
        scrim.setOnClickListener(v -> close());
        addView(scrim, new LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.MATCH_PARENT));
        box = new LinearLayout(a);
        box.setOrientation(LinearLayout.VERTICAL);
        int p = Math.round(20 * dp);
        box.setPadding(p, Math.round(14 * dp), p, Math.round(24 * dp));
        ScrollView sv = new ScrollView(a);
        sv.setBackgroundColor(0xF40C1322);   // the deep navy sheet, nearly opaque, like a console quick menu
        sv.addView(box);
        int w = Math.round(Math.min(420 * dp, a.getResources().getDisplayMetrics().widthPixels * 0.55f));
        addView(sv, new LayoutParams(w, LayoutParams.MATCH_PARENT, Gravity.END));
        View edge = new View(a);   // the teal edge: this menu belongs to NativeAYN, the same accent rule as the Home header
        edge.setBackground(new GradientDrawable(GradientDrawable.Orientation.TOP_BOTTOM, new int[]{0xFF2DD4BF, 0x002DD4BF}));
        addView(edge, new LayoutParams(Math.round(2 * dp), LayoutParams.MATCH_PARENT, Gravity.END));
        sheet = sv;
        setVisibility(GONE);
    }

    boolean isOpen() { return open; }
    void toggle() { if (open) close(); else open(); }

    void open() {
        if (open) return;
        open = true;
        box.removeAllViews();
        build();
        setVisibility(VISIBLE);
        sheet.setTranslationX(sheet.getLayoutParams().width);
        sheet.animate().translationX(0).setDuration(180).start();
        box.post(() -> { View f = box.focusSearch(View.FOCUS_DOWN); if (f != null) f.requestFocus(); else box.requestFocus(); });
    }

    void close() {
        ControllerControls.endCapture();   // a Controller row that was waiting for a key gives up when the panel goes
        if (!open) return;
        open = false;
        sheet.animate().translationX(sheet.getLayoutParams().width).setDuration(150).withEndAction(() -> { if (!open) setVisibility(GONE); }).start();
    }

    /** A gamepad key while the menu is open: B closes it, A presses what has focus, the D-pad moves the focus (the view system's own handling) */
    boolean padKey(KeyEvent e) {
        int k = e.getKeyCode();
        if (k == KeyEvent.KEYCODE_BUTTON_B) { if (e.getAction() == KeyEvent.ACTION_UP) close(); return true; }
        if (k == KeyEvent.KEYCODE_BUTTON_A) {
            View f = findFocus();
            if (e.getAction() == KeyEvent.ACTION_UP && f != null) f.performClick();
            return true;
        }
        return false;
    }

    /** The running game's title ID (GameActivity's intent): the menu's settings are that game's own */
    private String game() { return String.valueOf(act.getIntent().getStringExtra("title_id")); }

    /** One menu row; keep[0] receives its subtitle so a dialog can refresh it when the state changes */
    private void menuRow(int icon, String title, String sub, TextView[] keep, View.OnClickListener l) {
        View r = Ui.panelRow(act, icon, title, sub, l);
        keep[0] = (TextView) ((LinearLayout) ((LinearLayout) r).getChildAt(1)).getChildAt(1);   // the subtitle line: the column's second child (panelRow adds it only with a sub)
        box.addView(r, rowGap());
    }

    private void setSub(TextView[] keep, CharSequence s) { if (keep[0] != null) keep[0].setText(s); }

    private LinearLayout.LayoutParams rowGap() {
        LinearLayout.LayoutParams lp = Ui.fill();
        lp.topMargin = Ui.dp(act, 12);
        return lp;
    }

    private void build() {
        // The menu's header: the NativeAYN mark, its name, the running game's name, the teal rule
        LinearLayout h = Ui.row(act);
        h.setMinimumHeight(Ui.dp(act, 52));
        h.addView(Ui.badge(act, 40));
        LinearLayout col = new LinearLayout(act);
        col.setOrientation(LinearLayout.VERTICAL);
        col.setPadding(Ui.dp(act, 12), 0, 0, 0);
        TextView t = Ui.text(act, "NativeAYN", 20, R.color.bn_text, 800);
        t.setLetterSpacing(-0.01f);
        col.addView(t);
        String name = act.getIntent().getStringExtra("name");
        col.addView(Ui.text(act, name != null && !name.isEmpty() ? name : "In-game menu", 12, R.color.bn_accent, 600));
        h.addView(col, Ui.weight());
        box.addView(h);
        View rule = new View(act);
        rule.setBackground(new GradientDrawable(GradientDrawable.Orientation.LEFT_RIGHT, new int[]{0xFF2DD4BF, 0x002DD4BF}));
        LinearLayout.LayoutParams rp = Ui.fill();
        rp.height = Ui.dp(act, 2);
        rp.topMargin = Ui.dp(act, 4);
        box.addView(rule, rp);

        String[] f = Native.fpsCapStatus().split(" ");   // the cap in effect, the rates this build has, and what the game runs at now
        int cap = Integer.parseInt(f[0]), now = Integer.parseInt(f[2]);
        menuRow(R.drawable.ic_p_hourglass, "Frame rate cap", capNow(cap, now), capSub, v -> fpsDialog());
        menuRow(R.drawable.ic_p_triforce, "Frame generation", Lsfg.on(act) ? "On" : "Off", genSub, v -> genDialog());
        menuRow(R.drawable.ic_p_piece, "Upscaling", Fsr.on(act) ? "FSR 1 on" : "Off", upSub, v -> upDialog());
        menuRow(R.drawable.ic_p_dpad, "On-screen controls", "Buttons, opacity, layout", ctlSub, v -> controlsDialog());
        menuRow(R.drawable.ic_p_eye, "Overlay", Prefs.overlay(act) ? "Shown over the game" : "Hidden", ovlSub, v -> overlayDialog());
        menuRow(R.drawable.ic_p_reticle, "Controller mapping", "The gamepad's buttons, per controller", padSub, v -> padDialog());
        int sk = Native.skipStatus();   // the intro skip (DM_SKIP): offered where it is armed (Game settings › Intro skip) and in the test app
        if (sk >= 0 && ((sk & 2) != 0 || GameActivity.testApp()))
            menuRow(R.drawable.ic_p_ffwd, "Intro skip", (sk & 1) != 0 ? "Skipping the intro now" : "Off (hold \u2212 and + for 1 s in the game)", skipSub, v -> skipDialog());
        menuRow(R.drawable.ic_p_pause, "Pause or stop", "Suspend the game, or end the session", gameSub, v -> gameDialog());
    }

    /** What the cap line says: the live status of the running game */
    private String capNow(int cap, int now) {
        if (cap < 0) return "This build has no frame rate cap";
        if (now <= 0) return "Starting\u2026";
        if (Integer.parseInt(Native.fpsCapStatus().split(" ")[1]) == 0) return "This game picks its own frame rate";   // rates == 0 and presenting: the game's own
        return "Now " + now + " fps" + (cap > 0 ? ", capped at " + cap : ", the game's own");
    }

    // ---- The dialogs: one per row, the same rows the main screen's settings use ----

    private void fpsDialog() {
        LinearLayout b = dialogBox();
        String[] f = Native.fpsCapStatus().split(" ");
        int live = Integer.parseInt(f[0]), cur = live >= 0 ? live : Prefs.fpsCap(act, game());   // the cap in effect (env.txt or DM_CTL may have set another)
        String[] labels = new String[Prefs.FPS_CAPS.length];
        for (int i = 0; i < Prefs.FPS_CAPS.length; i++) labels[i] = Prefs.FPS_CAPS[i] == 0 ? "Auto" : Prefs.FPS_CAPS[i] + "";
        int curIdx = -1;
        for (int i = 0; i < Prefs.FPS_CAPS.length; i++) if (Prefs.FPS_CAPS[i] == cur) curIdx = i;
        if (cur < 0) curIdx = 0;   // no cap set: Auto (the game's own)
        TextView st = Ui.small(act, capNow(live, Integer.parseInt(f[2])));
        b.addView(Ui.body(act, "The cap from now on (Auto = the game's own rate)"));
        LinearLayout.LayoutParams pgp = Ui.fill();
        pgp.topMargin = Ui.dp(act, 8);
        b.addView(Ui.pillGroup(act, labels, curIdx, (g, id) -> {
            int v = Prefs.FPS_CAPS[id - 1000];
            Prefs.fpsCap(act, game(), v);   // the running game's own cap from now on (the main screen's stays the default of the others)
            Native.fpsCapSet(v);
            String[] s = Native.fpsCapStatus().split(" ");
            st.setText(capNow(Integer.parseInt(s[0]), Integer.parseInt(s[2])));
            setSub(capSub, st.getText());
        }), pgp);
        b.addView(st);
        Ui.sheet(act, "Frame rate cap", b).setPositiveButton("Done", null).show();
    }

    private void genDialog() {
        LinearLayout b = dialogBox();
        TextView st = Lsfg.rows(act, b, "Now: " + Native.frameGenStatus(), () -> Lsfg.push(act), false);
        Ui.sheet(act, "Frame generation", b).setPositiveButton("Done", null)
                .setOnDismissListener(d -> { setSub(genSub, Lsfg.on(act) ? "On" : "Off"); }).show();
    }

    private void upDialog() {
        LinearLayout b = dialogBox();
        Fsr.rows(act, b, false);
        Ui.sheet(act, "Upscaling", b).setPositiveButton("Done", null)
                .setOnDismissListener(d -> { setSub(upSub, Fsr.on(act) ? "FSR 1 on" : "Off"); }).show();
    }

    private void overlayDialog() {
        LinearLayout b = dialogBox();
        Overlay.rows(act, b, null);
        Ui.sheet(act, "Overlay", b).setPositiveButton("Done", null)
                .setOnDismissListener(d -> { setSub(ovlSub, Prefs.overlay(act) ? "Shown over the game" : "Hidden"); }).show();
    }

    private void controlsDialog() {
        LinearLayout b = dialogBox();
        fill.accept(b);   // GameActivity: TouchControls.controlRows
        Ui.sheet(act, "On-screen controls", b).setPositiveButton("Done", null).show();
    }

    private void padDialog() {
        LinearLayout b = dialogBox();
        ControllerControls.rows(act, b);
        Ui.sheet(act, "Controller mapping", b).setPositiveButton("Done", null).show();
    }

    private void skipDialog() {
        LinearLayout b = dialogBox();
        b.addView(Ui.small(act, "Presses A every few frames and ends movies early until gameplay, then stops by itself. In the game: hold \u2212 and + together for 1 s."));
        b.addView(Ui.button(act, (Native.skipStatus() & 1) != 0 ? "Stop the intro skip" : "Skip the intro now", Ui.Kind.TONAL,
                v -> { Native.skipSet((Native.skipStatus() & 1) != 0 ? 0 : 1); setSub(skipSub, (Native.skipStatus() & 1) != 0 ? "Skipping the intro now" : "Off (hold \u2212 and + for 1 s in the game)"); }), Ui.fill());
        Ui.sheet(act, "Intro skip (debug)", b).setPositiveButton("Done", null).show();
    }

    private AlertDialog sub;   // the open row's dialog (one at a time): a row that leaves the game (Pause) closes it too

    private void gameDialog() {
        LinearLayout b = dialogBox();
        b.addView(Ui.button(act, "Pause the game", Ui.Kind.TONAL,   // the game suspended (clock, graphics, threads, audio) until a tap on the screen, or A / Start: GameActivity.pause
                v -> { if (sub != null) sub.dismiss(); close(); if (act instanceof GameActivity) ((GameActivity) act).pause(); }), Ui.fill());
        b.addView(Ui.toggle(act, "Pause when the game comes back from the background", Prefs.pauseOnReturn(act, game()),
                (cb, on) -> Prefs.pauseOnReturn(act, game(), on)));
        b.addView(Ui.button(act, "Stop the game", Ui.Kind.DANGER, v -> new AlertDialog.Builder(act).setTitle("Stop the game?").setMessage("The game ends; unsaved progress is lost. The log stays for Share log.")
                .setPositiveButton("Stop", (d, w) -> { Native.note("stopped by the user"); if (act instanceof GameActivity) ((GameActivity) act).backToFrontend(); android.os.Process.killProcess(android.os.Process.myPid()); })
                .setNegativeButton("Keep running", null).show()), Ui.fill());
        sub = Ui.sheet(act, "Pause or stop", b).setPositiveButton("Done", null).create();
        sub.show();
    }

    /** The column a dialog's rows go into (the sheet scrolls it; its padding is the sheet's) */
    private LinearLayout dialogBox() {
        LinearLayout b = new LinearLayout(act);
        b.setOrientation(LinearLayout.VERTICAL);
        b.setPadding(Ui.dp(act, 4), 0, Ui.dp(act, 4), 0);
        return b;
    }
}

