package dev.nxnative.android;

import android.app.Activity;
import android.content.Context;
import android.content.SharedPreferences;
import android.view.InputDevice;
import android.view.KeyEvent;
import android.view.View;
import android.widget.Button;
import android.widget.LinearLayout;
import android.widget.SeekBar;
import android.widget.TextView;

import java.util.ArrayList;

/**
 * The in-game panel's Controller section (SidePanel): map one button of a physical gamepad to each game target. A row shows the Android key the target uses now, or
 * "default" (the host's fixed table, input_map.h nxin_key_button); pressing a row starts a capture, and GameActivity.dispatchKeyEvent hands the next physical key to
 * capture() instead of the host, which becomes that row's key (Back or Escape cancels, Home closes the panel). The map is kept per controller in its own preferences
 * file, and sent again every time the device connects (GameActivity.onInputDeviceAdded → onDeviceAdded): the host clears a device's map when it goes
 * (input_android.h, "Per-device key map"). A controller that never sets a map behaves exactly as before: with nothing set, setKeyMap is never called. The stick
 * dead zone (Native.deadzone, every pad) is a row of its own. All of it runs on the UI thread (the panel, dispatchKeyEvent, the InputManager listener).
 */
final class ControllerControls {
    // DM_BTN_* (dm/host/dm_input.h): the game targets a row sets, in the order the panel lists them; the int is the button bit as Native.setKeyMap takes it
    private static final String[] TARGET_NAME = {"A", "B", "X", "Y", "L", "R", "ZL", "ZR", "\u2212 (Minus)", "+ (Plus)", "L3 (left stick click)", "R3 (right stick click)",
            "D-pad up", "D-pad down", "D-pad left", "D-pad right"};
    private static final int[] TARGET_BTN = {1, 1 << 1, 1 << 2, 1 << 3, 1 << 6, 1 << 7, 1 << 8, 1 << 9, 1 << 11, 1 << 10, 1 << 4, 1 << 5,
            1 << 13, 1 << 15, 1 << 12, 1 << 14};
    private static final int ROWS = TARGET_NAME.length;

    /** The key codes' names in the rows, the Android gamepad keys and the D-pad first; the code number still follows, an unusual key is still identifiable */
    private static String keyName(int code) {
        switch (code) {
            case KeyEvent.KEYCODE_DPAD_UP: return "D-pad up";
            case KeyEvent.KEYCODE_DPAD_DOWN: return "D-pad down";
            case KeyEvent.KEYCODE_DPAD_LEFT: return "D-pad left";
            case KeyEvent.KEYCODE_DPAD_RIGHT: return "D-pad right";
            case KeyEvent.KEYCODE_DPAD_CENTER: return "D-pad centre";
            case KeyEvent.KEYCODE_BUTTON_A: return "Button A (bottom)";
            case KeyEvent.KEYCODE_BUTTON_B: return "Button B (right)";
            case KeyEvent.KEYCODE_BUTTON_X: return "Button X (left)";
            case KeyEvent.KEYCODE_BUTTON_Y: return "Button Y (top)";
            case KeyEvent.KEYCODE_BUTTON_L1: return "Button L1";
            case KeyEvent.KEYCODE_BUTTON_R1: return "Button R1";
            case KeyEvent.KEYCODE_BUTTON_L2: return "Button L2";
            case KeyEvent.KEYCODE_BUTTON_R2: return "Button R2";
            case KeyEvent.KEYCODE_BUTTON_THUMBL: return "Left stick click";
            case KeyEvent.KEYCODE_BUTTON_THUMBR: return "Right stick click";
            case KeyEvent.KEYCODE_BUTTON_START: return "Start";
            case KeyEvent.KEYCODE_BUTTON_SELECT: return "Select";
            case KeyEvent.KEYCODE_BUTTON_MODE: return "Home / guide";
            case KeyEvent.KEYCODE_BUTTON_C: return "Button C";
            case KeyEvent.KEYCODE_BUTTON_Z: return "Button Z";
            case KeyEvent.KEYCODE_BACK: return "Back";
            case KeyEvent.KEYCODE_ESCAPE: return "Escape";
            default: return "key " + code;
        }
    }

    // ---- The keys' per-controller map, in its own preferences file (read again when the other process wrote it, Prefs.open; commit: the game's process may end at any time) ----
    /** The file's "map@<key>" value: "<code> <button bit>" pairs separated by spaces, the rows' order (NXIN_MAP_MAX 64 pairs is far above these rows) */
    private static SharedPreferences prefs(Context c) { return Prefs.open(c, "controller_map"); }

    /** A controller's preferences key, stable across its disconnects: its name, vendor and product ids, with the characters that would break the key ("@", spaces) replaced */
    static String deviceKey(InputDevice d) {
        StringBuilder sb = new StringBuilder();
        String n = d.getName() == null ? "" : d.getName();
        for (int i = 0; i < n.length() && sb.length() < 40; i++) {
            char ch = n.charAt(i);
            sb.append((ch >= 'a' && ch <= 'z') || (ch >= 'A' && ch <= 'Z') || (ch >= '0' && ch <= '9') || ch == '.' || ch == '-' || ch == '_' ? ch : '_');
        }
        return sb.append('@').append(d.getVendorId()).append('/').append(d.getProductId()).toString();
    }

    /** The map saved for a controller: each entry "code button"; empty when it has none (the host's fixed table) */
    static ArrayList<String> savedMap(Context c, String devKey) {
        ArrayList<String> out = new ArrayList<>();
        if (devKey == null) return out;
        String v = prefs(c).getString("map@" + devKey, "");
        for (String e : v.split(" ")) {
            String[] p = e.split(":");
            if (p.length != 2) continue;
            try {
                int code = Integer.parseInt(p[0]);
                long bit = Long.parseLong(p[1]);
                if (code > 0 && bit > 0) out.add(code + ":" + bit);
            } catch (NumberFormatException ignored) { /* half a saved pair: drop it */ }
        }
        return out;
    }

    /** Whether the saved map already binds this key to another button (a key cannot do two buttons: the capture refuses and says so) */
    static boolean bindsOther(ArrayList<String> map, int keyCode, long wantBit) {
        for (String e : map) {
            String[] p = e.split(":");
            if (Integer.parseInt(p[0]) == keyCode && Long.parseLong(p[1]) != wantBit) return true;
        }
        return false;
    }

    /** Add or replace this button's entry, and drop every other entry of the same key (a key can do only one button): the map stays the rows' order */
    static void bindKey(ArrayList<String> map, int keyCode, long bit, int rowIndex) {
        map.removeIf(e -> Integer.parseInt(e.split(":")[0]) == keyCode);
        String entry = keyCode + ":" + bit;
        int insertAt = Math.min(rowIndex, map.size());
        for (int i = 0; i < map.size(); i++) {
            String[] p = map.get(i).split(":");
            if (Long.parseLong(p[1]) > bit) { insertAt = i; break; }   // keep the map sorted by button bit, the rows' order
        }
        map.add(insertAt, entry);
    }

    static String encodeMap(ArrayList<String> map) { return String.join(" ", map); }

    /** The dense arrays Native.setKeyMap wants from a saved map; empty when there is nothing to send (then the caller sends no map, the host's fixed table stands) */
    static int[] codesOf(ArrayList<String> map) {
        int[] a = new int[map.size()];
        for (int i = 0; i < a.length; i++) a[i] = Integer.parseInt(map.get(i).split(":")[0]);
        return a;
    }
    static int[] bitsOf(ArrayList<String> map) {
        int[] a = new int[map.size()];
        for (int i = 0; i < a.length; i++) a[i] = (int) Long.parseLong(map.get(i).split(":")[1]);   // the pad buttons are far below 2^31 (dm_input.h)
        return a;
    }

    /** Save one row's change and send the whole map now: setKeyMap with what is left, or clearKeyMap when nothing is left. devId < 0: no pad is connected, the file is enough. */
    static void applyMap(Context c, String devKey, int devId, ArrayList<String> map) {
        SharedPreferences.Editor ed = prefs(c).edit();
        if (map.isEmpty()) ed.remove("map@" + devKey); else ed.putString("map@" + devKey, encodeMap(map));
        ed.commit();
        if (devId < 0) return;
        if (map.isEmpty()) Native.clearKeyMap(devId);
        else Native.setKeyMap(devId, codesOf(map), bitsOf(map));   // the host replaces its whole table for this device at once
    }

    /** A controller connected: send back its saved map (the host cleared it when the device went), and the dead zone if the user set one (the host's default is the game profile's, DM_PAD_DEADZONE). */
    static void reapply(Context c, InputDevice d, int devId) {
        String devKey = deviceKey(d);
        SharedPreferences sp = prefs(c);
        sp.edit().putString("name@" + devKey, d.getName()).commit();   // its name for the panel's rows
        if (sp.contains("deadzone")) Native.deadzone(sp.getFloat("deadzone", 0.1f));
        ArrayList<String> map = savedMap(c, devKey);
        if (!map.isEmpty() && Native.setKeyMap(devId, codesOf(map), bitsOf(map)))
            Native.note("[controller] " + d.getName() + ": key map sent (" + map.size() + " keys)");
    }

    /** The row's Android key from a saved map: 0 when the button is on the host's fixed table (nothing bound) */
    static int boundKey(ArrayList<String> map, int rowIndex) {
        for (String e : map) if (Long.parseLong(e.split(":")[1]) == TARGET_BTN[rowIndex]) return Integer.parseInt(e.split(":")[0]);
        return 0;
    }

    /** A row's "Restore defaults": drop this button's entry and re-send what is left (the host's fixed table covers the key again) */
    static void unbindRow(Context c, String devKey, int devId, int rowIndex) {
        ArrayList<String> map = savedMap(c, devKey);
        map.removeIf(e -> Long.parseLong(e.split(":")[1]) == TARGET_BTN[rowIndex]);
        applyMap(c, devKey, devId, map);
    }

    /** "Clear all": the whole saved map goes for this controller, and the host's fixed table comes back */
    static void clearAll(Context c, String devKey, int devId) {
        applyMap(c, devKey, devId, new ArrayList<>());
    }

    // ---- The stick dead zone (Native.deadzone: one value for every pad). Unset: the host's own default, the game profile's DM_PAD_DEADZONE or 0.1 ----
    static boolean deadzoneSet(Context c) { return prefs(c).contains("deadzone"); }
    static float deadzonePref(Context c) { return prefs(c).getFloat("deadzone", 0.1f); }
    static void setDeadzone(Context c, float dz) { prefs(c).edit().putFloat("deadzone", dz).commit(); }

    // ---- The panel's rows (built by SidePanel.build; the capture lives here, dispatchKeyEvent asks it) ----
    private static ControllerControls panel;   // the open panel's rows, null while none is open (UI thread only)
    private final Activity act;
    private final Button[] row = new Button[ROWS];
    private final int[] rowCode = new int[ROWS];   // the Android key each row uses now, 0 = default
    private String devKey;   // the controller the rows edit
    private int devId = -1;  // its Android id while it is connected (-1: it is not, changes are only saved)
    private int captureRow = -1;   // the row waiting for a key
    private int captureKey;        // the key just assigned, whose release ends the capture
    private TextView status, deadLabel;

    /** The rows of the panel's Controller section, under its own heading (SidePanel.build) */
    static void rows(Activity a, LinearLayout box) {
        new ControllerControls(a, box);
    }

    private ControllerControls(Activity a, LinearLayout box) {
        act = a;
        Integer pad = act instanceof GameActivity ? ((GameActivity) act).currentPadDevice() : null;
        devId = pad == null ? -1 : pad;
        devKey = devId >= 0 ? deviceKey(InputDevice.getDevice(devId)) : null;
        String name = devKey == null ? null : prefs(act).getString("name@" + devKey, null);
        if (devKey == null) {
            box.addView(Ui.small(a, "No controller is connected. Connect one and press its buttons here to map them; the map is kept for it."));
        } else {
            status = Ui.small(a, "");
            box.addView(status);
            for (int i = 0; i < ROWS; i++) {
                LinearLayout line = new LinearLayout(a);
                line.setOrientation(LinearLayout.HORIZONTAL);
                row[i] = Ui.button(a, "", Ui.Kind.SOFT, null);
                row[i].setTextSize(13);
                final int r = i;   // the loop variable is not effectively final: the rows' lambdas use this copy
                row[i].setOnClickListener(v -> startCapture(r));
                Button reset = Ui.button(a, "Reset", Ui.Kind.QUIET, v -> { unbindRow(act, devKey, devId, r); reloadRows(); });
                LinearLayout.LayoutParams grow = new LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f);
                line.addView(row[i], grow);
                line.addView(reset, new LinearLayout.LayoutParams(LinearLayout.LayoutParams.WRAP_CONTENT, LinearLayout.LayoutParams.WRAP_CONTENT));
                box.addView(line);
            }
            box.addView(Ui.button(a, "Clear all (back to the default table)", Ui.Kind.SOFT, v -> { clearAll(act, devKey, devId); reloadRows(); }), Ui.fill());
            deadLabel = Ui.body(a, "");
            SeekBar dz = new SeekBar(a);
            Ui.seek(a, dz);
            dz.setMax(40);   // 0..40 steps of 1% (0..0.4), enough for a stick's noise; the host default is 0.1
            dz.setProgress(Math.round(deadzonePref(act) * 100));
            deadLabel.setText("Stick dead zone " + dz.getProgress() + " %");
            dz.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener() {
                @Override public void onProgressChanged(SeekBar b, int v, boolean user) {
                    deadLabel.setText("Stick dead zone " + v + " %");
                    if (!user) return;
                    setDeadzone(act, v / 100f);
                    Native.deadzone(v / 100f);   // every pad, from now on (input_android.h nxhost_input_deadzone)
                }
                @Override public void onStartTrackingTouch(SeekBar b) {}
                @Override public void onStopTrackingTouch(SeekBar b) {}
            });
            box.addView(deadLabel);
            box.addView(dz);
            reloadRows();
        }
        panel = this;
    }

    /** The rows' texts and the status line, as the saved map says now */
    private void reloadRows() {
        if (devKey == null) return;   // no controller this run: the section is one note, no rows exist
        ArrayList<String> map = savedMap(act, devKey);
        for (int i = 0; i < ROWS; i++) {
            rowCode[i] = boundKey(map, i);
            row[i].setText(TARGET_NAME[i] + ": " + (rowCode[i] == 0 ? "default" : keyName(rowCode[i])));
        }
        if (status == null) return;
        String name = prefs(act).getString("name@" + devKey, "the controller");
        status.setText(captureRow >= 0 ? "Press a button on " + name + " for \"" + TARGET_NAME[captureRow] + "\" (Back or Escape cancels)"
                : "Tap a row, then press the button you want for it on " + name + ".");
    }

    private void startCapture(int i) {
        captureRow = i;
        captureKey = 0;
        reloadRows();
    }

    /** Whether a row is waiting for its key: GameActivity.dispatchKeyEvent sends physical key events to capture() while this is true */
    static boolean capturing() { return panel != null && panel.captureRow >= 0; }

    /** A key event while a row is capturing (GameActivity.dispatchKeyEvent): true when the capture consumed it, so the game never sees it. Home passes through and closes the panel, which ends the capture. */
    static boolean capture(KeyEvent e) {
        ControllerControls p = panel;
        if (p == null || p.captureRow < 0) return false;
        int code = e.getKeyCode();
        if (code == KeyEvent.KEYCODE_BUTTON_MODE) return false;   // the panel's open / close key
        if (code == KeyEvent.KEYCODE_BACK || code == KeyEvent.KEYCODE_ESCAPE) {   // cancel; consume both actions so Back does not also close the panel
            p.cancel();
            return true;
        }
        // A physical pad or keyboard only: the phone's virtual keys and the touch layers' events are not controller buttons
        InputDevice d = e.getDevice();
        if (d == null || d.isVirtual()) return false;
        int action = e.getAction();
        if (action == KeyEvent.ACTION_MULTIPLE) return true;
        if (action == KeyEvent.ACTION_UP) {
            if (code == p.captureKey) p.captureRow = -1;   // the assigned button released: the capture is over
            p.reloadRows();
            return true;   // the matching press was consumed: its release must not reach the game either
        }
        if (action != KeyEvent.ACTION_DOWN) return true;
        if (e.getRepeatCount() > 0) return true;   // a held D-pad key repeats: only the first press assigns, the repeats die here
        long want = TARGET_BTN[p.captureRow];
        ArrayList<String> map = savedMap(p.act, p.devKey);
        if (bindsOther(map, code, want)) {
            if (p.status != null) p.status.setText("Key " + keyName(code) + " already does another button: press a different key, or Reset that row first.");
            return true;   // still capturing, waiting for another press
        }
        bindKey(map, code, want, p.captureRow);
        applyMap(p.act, p.devKey, p.devId, map);
        Native.note("[controller] " + TARGET_NAME[p.captureRow] + " = " + keyName(code) + (p.devId < 0 ? " (saved: no pad connected)" : " (sent to the pad)"));
        p.captureKey = code;   // ends on the release, so a held button cannot assign twice
        p.reloadRows();
        return true;
    }

    /** Back / Escape, or the panel going (close, Stop): the row gives up waiting */
    static void endCapture() {
        ControllerControls p = panel;
        if (p == null) return;
        p.cancel();
    }

    private void cancel() {
        if (captureRow < 0) return;
        captureRow = -1;
        reloadRows();
    }
}
