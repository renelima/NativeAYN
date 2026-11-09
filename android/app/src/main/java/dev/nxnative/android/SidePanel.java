package dev.nxnative.android;

import android.app.Activity;
import android.app.AlertDialog;
import android.graphics.drawable.GradientDrawable;
import android.view.Gravity;
import android.view.KeyEvent;
import android.view.View;
import android.widget.Button;
import android.widget.FrameLayout;
import android.widget.LinearLayout;
import android.widget.RadioGroup;
import android.widget.ScrollView;
import android.widget.TextView;

/**
 * The in-game panel: slides in from the right over the running game, which goes on behind it. It holds the frame rate cap (Native.fpsCapSet), frame generation
 * (Lsfg.rows, Native.frameGenSet), FSR 1 upscaling (Fsr.rows), the overlay's items (Overlay.rows), the on-screen controls' rows (TouchControls.controlRows), the intro skip (Native.skipSet, where it is armed and in the test app) and Stop. Every change applies at once,
 * without going back to the launcher, and is kept for the next start. Opened by its handle at the right edge, by Back (the phone's button or its edge swipe) or a gamepad's Home /
 * guide button (KEYCODE_BUTTON_MODE); closed the same ways, by a tap beside it, or by B on a gamepad, whose D-pad and A move
 * through it meanwhile (GameActivity.dispatchKeyEvent).
 */
final class SidePanel extends FrameLayout {
    private final Activity act;
    private final LinearLayout box;
    private final View sheet;
    private final java.util.function.Consumer<LinearLayout> fill;
    private TextView fpsNow, lsfgNow;
    private RadioGroup caps;
    private boolean open;
    private final Runnable tick = this::refresh;

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
        View edge = new View(a);   // the teal edge: this panel belongs to NativeAYN, the same accent rule as the Home header
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
        removeCallbacks(tick);
        refresh();
    }

    void close() {
        ControllerControls.endCapture();   // a Controller row that was waiting for a key gives up when the panel goes
        if (!open) return;
        open = false;
        sheet.animate().translationX(sheet.getLayoutParams().width).setDuration(150).withEndAction(() -> { if (!open) setVisibility(GONE); }).start();
    }

    /** A gamepad key while the panel is open: B closes it, A presses what has focus, the D-pad moves the focus (the view system's own handling) */
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

    private TextView head(String t) {
        TextView h = Ui.section(act, t);   // the same bracket label the launcher's sections use: 12 sp caps, teal bracket
        h.setPadding(h.getPaddingLeft(), Math.round(20 * getResources().getDisplayMetrics().density), 0, 0);
        box.addView(h);
        return h;
    }

    private TextView note(String t) {
        TextView n = Ui.small(act, t);
        n.setPadding(Ui.dp(act, 2), 0, 0, 0);
        box.addView(n);
        return n;
    }

    /** The running game's title ID (GameActivity's intent): the panel's settings are that game's own */
    private String game() { return String.valueOf(act.getIntent().getStringExtra("title_id")); }

    private void build() {
        // The panel's header: the NativeAYN mark over the game, its name, the running game's title, the same teal rule as the launcher's headers
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
        note("The game runs on behind this panel; every change applies now and is kept.");

        head("Frame rate cap");   // the game's own frame rate; frame generation (below, its own heading) adds frames on top
        int live = Integer.parseInt(Native.fpsCapStatus().split(" ")[0]);
        int cur = live >= 0 ? live : Prefs.fpsCap(act, game());   // the cap in effect (env.txt or DM_CTL may have set another)
        String[] capLabels = new String[Prefs.FPS_CAPS.length];
        for (int i = 0; i < Prefs.FPS_CAPS.length; i++) capLabels[i] = Prefs.FPS_CAPS[i] == 0 ? "Auto" : Prefs.FPS_CAPS[i] + "";
        int curIdx = -1;
        for (int i = 0; i < Prefs.FPS_CAPS.length; i++) if (Prefs.FPS_CAPS[i] == cur) curIdx = i;   // a cap off the list (40, 45): none selected, the line below says it
        if (cur < 0) curIdx = 0;   // no cap set: Auto (the game's own)
        caps = Ui.pillGroup(act, capLabels, curIdx, (g, id) -> {
            int v = Prefs.FPS_CAPS[id - 1000];
            Prefs.fpsCap(act, game(), v);   // the running game's own cap from now on (the main screen's stays the default of the others)
            Native.fpsCapSet(v);
            refresh();
        });
        box.addView(caps);
        fpsNow = note("");

        lsfgNow = Lsfg.rows(act, box, "", () -> { Lsfg.push(act); refresh(); });

        Fsr.rows(act, box);

        head("Overlay");
        Overlay.rows(act, box, null);

        head("On-screen controls");
        fill.accept(box);   // GameActivity: TouchControls.controlRows

        head("Controller");   // the physical gamepad's buttons, per controller: rows that capture the next key, kept for the next start (ControllerControls)
        ControllerControls.rows(act, box);

        int sk = Native.skipStatus();   // the intro skip (DM_SKIP): offered where it is armed (Game settings › Intro skip) and in the test app
        if (sk >= 0 && ((sk & 2) != 0 || GameActivity.testApp())) {
            head("Intro skip (debug)");
            note("Presses A every few frames and ends movies early until gameplay, then stops by itself. In the game: hold − and + together for 1 s.");
            box.addView(Ui.button(act, (sk & 1) != 0 ? "Stop the intro skip" : "Skip the intro now", Ui.Kind.TONAL,
                    v -> { Native.skipSet((Native.skipStatus() & 1) != 0 ? 0 : 1); close(); }), Ui.fill());
        }

        Button pause = Ui.button(act, "Pause the game", Ui.Kind.TONAL,   // the game suspended (clock, graphics, threads, audio) until a tap on the screen, or A / Start: GameActivity.pause
                v -> { close(); if (act instanceof GameActivity) ((GameActivity) act).pause(); });
        LinearLayout.LayoutParams pl = Ui.fill();
        pl.topMargin = Math.round(24 * getResources().getDisplayMetrics().density);
        box.addView(pause, pl);
        box.addView(Ui.toggle(act, "Pause when the game comes back from the background", Prefs.pauseOnReturn(act, game()),
                (b, on) -> Prefs.pauseOnReturn(act, game(), on)));
        box.addView(Ui.button(act, "Stop the game", Ui.Kind.DANGER, v -> new AlertDialog.Builder(act).setTitle("Stop the game?").setMessage("The game ends; unsaved progress is lost. The log stays for Share log.")
                .setPositiveButton("Stop", (d, w) -> { Native.note("stopped by the user"); if (act instanceof GameActivity) ((GameActivity) act).backToFrontend(); android.os.Process.killProcess(android.os.Process.myPid()); })
                .setNegativeButton("Keep running", null).show()), Ui.fill());
        box.addView(Ui.button(act, "Back to the game", Ui.Kind.PRIMARY, v -> close()), Ui.fill());
    }

    /** Twice a second while open: what the game runs at, which caps it can hold, and the frame generation's state */
    private void refresh() {
        if (!open) return;
        String[] f = Native.fpsCapStatus().split(" ");
        int cap = Integer.parseInt(f[0]), rates = Integer.parseInt(f[1]), now = Integer.parseInt(f[2]);
        for (int i = 0; i < Prefs.FPS_CAPS.length; i++) {
            int v = Prefs.FPS_CAPS[i], bit = v == 15 ? 0 : v == 20 ? 1 : v == 30 ? 2 : 5;   // the host's bits: 15, 20, 30, (40, 45,) 60
            caps.getChildAt(i).setEnabled(v == 0 || cap >= 0 && (rates & 1 << bit) != 0);
        }
        // No cap in this build, or a game that picks its own frame rate: no choices (they would do nothing), one line instead
        // (rates is 0 too until the game's main is loaded: only a game that presents, now > 0, is known to pick its own)
        boolean own = cap >= 0 && rates == 0 && now > 0, none = cap < 0 || own;
        caps.setVisibility(none ? GONE : VISIBLE);
        if (cap >= 0 && now > 0) Prefs.ownRate(act, game(), own);   // its Game settings hide the cap too
        String s = cap < 0 ? "This build has no frame rate cap."
                : now <= 0 ? "Starting…"
                : own ? "This game picks its own frame rate."
                : "Now " + now + " fps" + (cap > 0 ? ", capped at " + cap : ", the game's own") + ".";
        fpsNow.setText(s);
        if (lsfgNow != null) { String st = Native.frameGenStatus(); int sp = st.indexOf(' '); lsfgNow.setText("Now: " + (sp > 0 ? st.substring(sp + 1) : st)); }
        removeCallbacks(tick);   // a change calls refresh too: one loop, not one more per change
        postDelayed(tick, 500);
    }
}
