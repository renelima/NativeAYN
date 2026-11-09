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
 * the intro skip and Stop). Every change applies at once, without going back to the launcher, and is kept for the next start. Opened by its handle at the right
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
        scrim.setBackgroundColor(0x8A000000);   // a deep charcoal dim: the game reads as "paused behind a menu"
        scrim.setOnClickListener(v -> close());
        addView(scrim, new LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.MATCH_PARENT));
        box = new LinearLayout(a);
        box.setOrientation(LinearLayout.VERTICAL);
        int p = Math.round(20 * dp);
        box.setPadding(p, Math.round(14 * dp), p, Math.round(24 * dp));
        ScrollView sv = new ScrollView(a);
        sv.setBackgroundColor(0xF41A1A1A);   // the charcoal sheet, nearly opaque, like a console quick menu
        sv.addView(box);
        int w = Math.round(Math.min(420 * dp, a.getResources().getDisplayMetrics().widthPixels * 0.55f));
        addView(sv, new LayoutParams(w, LayoutParams.MATCH_PARENT, Gravity.END));
        View edge = new View(a);   // the grey edge: this menu belongs to NativeAYN, the same quiet mark as the panel's texts
        edge.setBackground(new GradientDrawable(GradientDrawable.Orientation.TOP_BOTTOM, new int[]{0xFFB9B9B9, 0x00B9B9B9}));
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
        // The menu's header: the NativeAYN mark, its name, the running game's name, the grey rule
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
        col.addView(Ui.text(act, name != null && !name.isEmpty() ? name : Lang.t(act, "In-game menu"), 12, R.color.bn_text_2, 600));   // the game name sits back (grey), the same subtitle look as Home
        h.addView(col, Ui.weight());
        box.addView(h);
        View rule = new View(act);
        rule.setBackground(new GradientDrawable(GradientDrawable.Orientation.LEFT_RIGHT, new int[]{0xFFB9B9B9, 0x00B9B9B9}));   // the same soft grey as the panel's texts
        LinearLayout.LayoutParams rp = Ui.fill();
        rp.height = Ui.dp(act, 2);
        rp.topMargin = Ui.dp(act, 4);
        box.addView(rule, rp);

        // The language, a quiet line under the rule: the two names, the current one raised. PT and EN are the same words in both languages.
        LinearLayout langRow = new LinearLayout(act);
        langRow.setOrientation(LinearLayout.HORIZONTAL);
        langRow.setGravity(Gravity.END);
        langRow.addView(Ui.small(act, "Idioma / Language:"));
        langRow.addView(Ui.button(act, "PT", Lang.pt(act) ? Ui.Kind.TONAL : Ui.Kind.QUIET, 34, v -> setLang("pt")));
        langRow.addView(Ui.button(act, "EN", !Lang.pt(act) ? Ui.Kind.TONAL : Ui.Kind.QUIET, 34, v -> setLang("en")));
        box.addView(langRow);

        String[] f = Native.fpsCapStatus().split(" ");   // the cap in effect, the rates this build has, and what the game runs at now
        int cap = Integer.parseInt(f[0]), now = Integer.parseInt(f[2]);
        menuRow(R.drawable.ic_p_reticle, Lang.t(act, "Controller mapping"), Lang.t(act, "The gamepad's buttons, per controller"), padSub, v -> padDialog());
        menuRow(R.drawable.ic_p_dpad, Lang.t(act, "On-screen controls"), Lang.t(act, "Buttons, opacity, layout"), ctlSub, v -> controlsDialog());
        menuRow(R.drawable.ic_p_eye, Lang.t(act, "Overlay"), Prefs.overlay(act) ? Lang.t(act, "Shown over the game") : Lang.t(act, "Hidden"), ovlSub, v -> overlayDialog());
        menuRow(R.drawable.ic_p_hourglass, Lang.t(act, "Frame rate cap"), capNow(cap, now), capSub, v -> fpsDialog());
        menuRow(R.drawable.ic_p_triforce, Lang.t(act, "Frame generation"), Lsfg.on(act) ? Lang.t(act, "On") : Lang.t(act, "Off"), genSub, v -> genDialog());
        menuRow(R.drawable.ic_p_piece, Lang.t(act, "Upscaling"), Fsr.on(act) ? Lang.t(act, "FSR 1 on") : Lang.t(act, "Off"), upSub, v -> upDialog());
        int sk = Native.skipStatus();   // the intro skip (DM_SKIP): offered where it is armed (Game settings › Intro skip) and in the test app
        if (sk >= 0 && ((sk & 2) != 0 || GameActivity.testApp()))
            menuRow(R.drawable.ic_p_ffwd, Lang.t(act, "Intro skip"), (sk & 1) != 0 ? Lang.t(act, "Skipping the intro now") : Lang.t(act, "Off (hold \u2212 and + for 1 s in the game)"), skipSub, v -> skipDialog());
        menuRow(R.drawable.ic_p_stop, Lang.t(act, "Stop"), Lang.t(act, "End this session, with a confirmation"), gameSub, v -> stopConfirm());
    }

    private void setLang(String lang) {
        Prefs.lang(act, lang);
        box.removeAllViews();   // the menu rebuilds itself in the new language right away; the other screens follow on being opened again
        build();
    }

    /** What the cap line says: the live status of the running game */
    private String capNow(int cap, int now) {
        if (cap < 0) return Lang.t(act, "This build has no frame rate cap");
        if (now <= 0) return Lang.t(act, "Starting\u2026");
        if (Integer.parseInt(Native.fpsCapStatus().split(" ")[1]) == 0) return Lang.t(act, "This game picks its own frame rate");   // rates == 0 and presenting: the game's own
        return Lang.t(act, "Now ") + now + " fps" + (cap > 0 ? Lang.t(act, ", capped at ") + cap : Lang.t(act, ", the game's own"));
    }

    // ---- The dialogs: one per row, the same rows the main screen's settings use ----

    private void fpsDialog() {
        LinearLayout b = dialogBox();
        String[] f = Native.fpsCapStatus().split(" ");
        int live = Integer.parseInt(f[0]), cur = live >= 0 ? live : Prefs.fpsCap(act, game());   // the cap in effect (env.txt or DM_CTL may have set another)
        String[] labels = new String[Prefs.FPS_CAPS.length];
        for (int i = 0; i < Prefs.FPS_CAPS.length; i++) labels[i] = Prefs.FPS_CAPS[i] == 0 ? Lang.t(act, "Auto") : Prefs.FPS_CAPS[i] + "";
        int curIdx = -1;
        for (int i = 0; i < Prefs.FPS_CAPS.length; i++) if (Prefs.FPS_CAPS[i] == cur) curIdx = i;
        if (cur < 0) curIdx = 0;   // no cap set: Auto (the game's own)
        TextView st = Ui.small(act, capNow(live, Integer.parseInt(f[2])));
        b.addView(Ui.body(act, Lang.t(act, "The cap from now on (Auto = the game's own rate)")));
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
        Ui.sheet(act, Lang.t(act, "Frame rate cap"), b).setPositiveButton(Lang.t(act, "Done"), null).show();
    }

    private void genDialog() {
        LinearLayout b = dialogBox();
        TextView st = Lsfg.rows(act, b, Lang.t(act, "Now: ") + Native.frameGenStatus(), () -> Lsfg.push(act), false);
        Ui.sheet(act, Lang.t(act, "Frame generation"), b).setPositiveButton(Lang.t(act, "Done"), null)
                .setOnDismissListener(d -> { setSub(genSub, Lsfg.on(act) ? Lang.t(act, "On") : Lang.t(act, "Off")); }).show();
    }

    private void upDialog() {
        LinearLayout b = dialogBox();
        Fsr.rows(act, b, false);
        Ui.sheet(act, Lang.t(act, "Upscaling"), b).setPositiveButton(Lang.t(act, "Done"), null)
                .setOnDismissListener(d -> { setSub(upSub, Fsr.on(act) ? Lang.t(act, "FSR 1 on") : Lang.t(act, "Off")); }).show();
    }

    private void overlayDialog() {
        LinearLayout b = dialogBox();
        Overlay.rows(act, b, null);
        Ui.sheet(act, Lang.t(act, "Overlay"), b).setPositiveButton(Lang.t(act, "Done"), null)
                .setOnDismissListener(d -> { setSub(ovlSub, Prefs.overlay(act) ? Lang.t(act, "Shown over the game") : Lang.t(act, "Hidden")); }).show();
    }

    private void controlsDialog() {
        LinearLayout b = dialogBox();
        fill.accept(b);   // GameActivity: TouchControls.controlRows
        Ui.sheet(act, Lang.t(act, "On-screen controls"), b).setPositiveButton(Lang.t(act, "Done"), null).show();
    }

    private void padDialog() {
        LinearLayout b = dialogBox();
        ControllerControls.rows(act, b);
        Ui.sheet(act, Lang.t(act, "Controller mapping"), b).setPositiveButton(Lang.t(act, "Done"), null)
                .setOnDismissListener(d -> ControllerControls.endCapture())   // a row still waiting for a key gives up when the sheet goes
                .show();
    }

    private void skipDialog() {
        LinearLayout b = dialogBox();
        b.addView(Ui.small(act, Lang.t(act, "Presses A every few frames and ends movies early until gameplay, then stops by itself. In the game: hold \u2212 and + together for 1 s.")));
        b.addView(Ui.button(act, (Native.skipStatus() & 1) != 0 ? Lang.t(act, "Stop the intro skip") : Lang.t(act, "Skip the intro now"), Ui.Kind.TONAL,
                v -> { Native.skipSet((Native.skipStatus() & 1) != 0 ? 0 : 1); setSub(skipSub, (Native.skipStatus() & 1) != 0 ? Lang.t(act, "Skipping the intro now") : Lang.t(act, "Off (hold \u2212 and + for 1 s in the game)")); }), Ui.fill());
        Ui.sheet(act, Lang.t(act, "Intro skip (debug)"), b).setPositiveButton(Lang.t(act, "Done"), null).show();
    }

    /** The Stop row: one confirmation, then the session ends (the game's pause lives on its own, in the settings and on background return) */
    private void stopConfirm() {
        new AlertDialog.Builder(act).setTitle(Lang.t(act, "Stop the game?")).setMessage(Lang.t(act, "The game ends; unsaved progress is lost. The log stays for Share log."))
                .setPositiveButton(Lang.t(act, "Stop"), (d, w) -> { Native.note("stopped by the user"); if (act instanceof GameActivity) ((GameActivity) act).backToFrontend(); android.os.Process.killProcess(android.os.Process.myPid()); })
                .setNegativeButton(Lang.t(act, "Keep running"), null).show();
    }

    /** The column a dialog's rows go into (the sheet scrolls it; its padding is the sheet's) */
    private LinearLayout dialogBox() {
        LinearLayout b = new LinearLayout(act);
        b.setOrientation(LinearLayout.VERTICAL);
        b.setPadding(Ui.dp(act, 4), 0, Ui.dp(act, 4), 0);
        return b;
    }
}

