package dev.nxnative.android;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.ClipData;
import android.content.Intent;
import android.content.res.Configuration;
import android.net.Uri;
import android.os.Bundle;
import android.os.Environment;
import android.provider.DocumentsContract;
import android.provider.Settings;
import android.text.TextUtils;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.view.WindowManager;
import android.widget.Button;
import android.widget.FrameLayout;
import android.widget.HorizontalScrollView;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

/**
 * The launcher. Two pages in one activity (so every picker's result comes back here): Home, the short path to playing (pick your dump, prepare it, Play), and Settings
 * (the defaults of every game, GPU driver, folders, the Readiness check, the self-test, logs, the build), opened with the gear in Home's header; Back returns to Home.
 * <p>
 * Home: the All files access card while it is missing, a game being prepared (Ondevice: shown while there is a job), then the games, each a card with its state and
 * one big button: Play, or Prepare when its pack does not fit yet; ⋯ (or a long press) opens Manage (its folders, its own settings, saves, Prepare again, removal),
 * kept away from Play. With no game, a single card says what to do and has Add game. Lang.t(this, "Add game") takes a dump folder or .nup of any game this APK has a profile for
 * (Profile: one host per game), or a folder that holds several (all added, one summary: addAll): the best pack PackFind finds (the picked pack folder, the app's,
 * beside the game, &lt;id&gt;-work/&lt;any&gt; up to three folders above it) is used when it fits or only needs the automatic shader update, else the app offers to
 * prepare one on the phone (or a card with Prepare, from a folder of games). A game without a profile in this APK is refused with a sentence (Profile.missing),
 * never started.
 * <p>
 * Each page sits in one ScrollView on the flat charcoal backdrop, so it scrolls on any screen (the folded phone's outer display, landscape, a split window), at a
 * readable width, centered on a wide window. The activity handles rotation itself (configChanges), so a size change builds the page again (onConfigurationChanged).
 */
public final class MainActivity extends Activity {
    private static final int PICK_GAME = 1, PICK_PACK = 2, PICK_DLL = 3, PICK_DRIVER = 4, PICK_NUP = 5;

    private final List<Game> games = new ArrayList<>();
    private LinearLayout gameBox;   // the game cards (refresh)
    private View accessCard;
    private FrameLayout frame;   // the root: pages sit directly on the flat charcoal bn_bg (the Console Shelf style, no wallpaper)
    private Ui.Setting overlayRow, resRow, capRow, lsfgRow, gpuRow, updateRow, langRow;
    private boolean resumed, settingsPage;
    private boolean merged;   // the list's duplicates were merged (Game.collapse) since this activity started
    private File pendingGame;   // a game folder (or .nup) waiting for its pack folder
    private String pendingId;
    private File pendingPackPick;   // the pack folder the user just picked (remembered in Prefs once it is accepted)

    @Override protected void onCreate(Bundle saved) {
        super.onCreate(saved);
        getWindow().addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);   // the screen stays lit while the launcher is open (it is dropped when the app leaves the foreground)
        if (saved != null) settingsPage = saved.getBoolean("settings");
        frame = new FrameLayout(this);
        frame.setBackgroundColor(Ui.col(this, R.color.bn_bg));   // the flat charcoal screen of the Console Shelf style
        setContentView(frame);
        build();
        if (saved == null) fromIntent(getIntent());
        Updater.onStart(this);   // the release app: a newer release on GitHub opens the update popup (once per process start: not on a resume or a rotation)
    }

    @Override protected void onSaveInstanceState(Bundle out) {
        super.onSaveInstanceState(out);
        out.putBoolean("settings", settingsPage);
    }

    /** Rotation, a window resize: the same page again */
    @Override public void onConfigurationChanged(Configuration c) {
        super.onConfigurationChanged(c);
        build();
        if (resumed) { onResumeViews(); if (!settingsPage) Ondevice.resume(this); }
    }

    /** Home ↔ Settings */
    private void page(boolean settings) {
        if (settingsPage == settings) return;
        settingsPage = settings;
        if (settings) Ondevice.pause(this);   // its card is on Home only: no updates while Settings shows
        build();
        if (resumed) { onResumeViews(); if (!settings) Ondevice.resume(this); }
    }

    @SuppressWarnings("deprecation")
    @Override public void onBackPressed() {
        if (settingsPage) page(false);
        else super.onBackPressed();
    }

    /** (Re)builds the current page on the charcoal backdrop (Ui.page: the same gutter, width and header spacing as every screen) */
    private void build() {
        while (frame.getChildCount() > 0) frame.removeViewAt(0);
        LinearLayout content = Ui.page(this, frame, 760);
        if (settingsPage) buildSettings(content);
        else buildHome(content);
    }

    private void buildHome(LinearLayout content) {
        overlayRow = null;
        content.addView(Ui.header(this, Ui.badge(this, 48), "NativeAYN", Prefs.buildId(), Ui.iconButton(this, R.drawable.ic_settings, Lang.t(this, "Settings"), Ui.Kind.TONAL, v -> page(true)), false));   // no accent rule on the dashboard (the Console Shelf style); the build identifier under the title (Prefs.buildId, the part of the overlay's build tag after the app's name), so a screenshot of Home names its build; Settings › About has the full tag
        Ondevice.attach(this, content, content.getChildCount());   // a game being prepared and its progress (hidden when there is none)
        fixGap(content, Ui.GAP);
        gameBox = new LinearLayout(this);
        gameBox.setOrientation(LinearLayout.VERTICAL);
        gameBox.setClipChildren(false);   // the raised (scaled) card spills a little past the row: let it
        Ui.add(content, gameBox, 16);
        refresh();
    }

    /** The Ondevice card is added without a margin: give it the cards' gap */
    private void fixGap(LinearLayout col, int gapDp) {
        for (int k = 1; k < col.getChildCount(); k++) {
            View v = col.getChildAt(k);
            ViewGroup.LayoutParams p = v.getLayoutParams();
            if (p instanceof LinearLayout.LayoutParams && ((LinearLayout.LayoutParams) p).topMargin == 0) ((LinearLayout.LayoutParams) p).topMargin = dp(gapDp);
        }
    }

    /**
     * The file-access request as a tile of the shelf (refresh adds it as the row's first tile): the same size and quiet
     * look as the game and plus tiles, so it sits beside them instead of a banner across the page
     */
    private View accessCard() {
        int w = dp(210), h = dp(166);   // a little wider than a game tile, as tall as one
        LinearLayout col = new LinearLayout(this);
        col.setOrientation(LinearLayout.VERTICAL);
        col.setGravity(Gravity.CENTER_HORIZONTAL);
        LinearLayout tile = new LinearLayout(this);
        tile.setOrientation(LinearLayout.VERTICAL);
        tile.setGravity(Gravity.CENTER);
        tile.setBackground(Ui.round(Ui.col(this, R.color.bn_card), dp(14), Ui.col(this, R.color.bn_card_stroke), dp(1)));
        tile.setElevation(dp(3));   // the same light shadow as the plus tile
        tile.setPadding(dp(14), dp(10), dp(14), dp(10));
        TextView t = Ui.small(this, Lang.t(this, "Grant file access to read your game dump."));
        t.setGravity(Gravity.CENTER);
        tile.addView(t, Ui.wrap());
        tile.addView(Ui.button(this, Lang.t(this, "Allow"), Ui.Kind.QUIET, v -> openAccessSettings()), new LinearLayout.LayoutParams(dp(110), dp(40)));   // sized tile: the tap area the user expects at this height
        col.addView(tile, new LinearLayout.LayoutParams(w, h));
        TextView name = Ui.text(this, Lang.t(this, "Allow file access"), 15, R.color.bn_text_2, 700);   // under the tile like a game's name, but grey: it is not something to be proud of
        name.setGravity(Gravity.CENTER);
        LinearLayout.LayoutParams nl = Ui.wrap();
        nl.width = w;
        nl.topMargin = dp(10);
        col.addView(name, nl);
        return col;
    }

    // ---- Settings page ----

    private void buildSettings(LinearLayout content) {
        accessCard = null;
        gameBox = null;
        content.addView(Ui.header(this, Ui.back(this, v -> page(false)), Lang.t(this, "Settings"), null));

        // Updates first, so the version and the check are easy to find (Updater: the release app also checks at every fresh start)
        Ui.add(content, Ui.section(this, Lang.t(this, "Updates")), 16);
        LinearLayout upd = settingsCard(content);
        updateRow = new Ui.Setting(this, "🔄", Lang.t(this, "Check for updates"), v -> Updater.checkNow(this)).chevron(this);
        upd.addView(updateRow.view);

        Ui.add(content, Ui.section(this, Lang.t(this, "Game defaults")), Ui.SECTION_GAP);
        LinearLayout play = settingsCard(content);
        overlayRow = new Ui.Setting(this, "👀", Lang.t(this, "Overlay"), v -> overlayItems())
                .toggle(this, Prefs.overlay(this), (b, on) -> { Prefs.overlay(this, on); refreshSettings(); });
        play.addView(overlayRow.view);
        resRow = new Ui.Setting(this, "🖼️", Lang.t(this, "Resolution"), v -> gameRes()).chevron(this);
        play.addView(resRow.view);
        if (!Profile.anyResolution()) resRow.view.setVisibility(View.GONE);   // no game of this APK has a window_fn or graphics_scale lever: the default would do nothing
        capRow = new Ui.Setting(this, "⏱️", Lang.t(this, "Frame rate cap"), v -> fpsCap()).chevron(this);
        play.addView(capRow.view);
        lsfgRow = new Ui.Setting(this, "✨", Lang.t(this, "Frame generation"), v -> frameGen()).chevron(this);
        play.addView(lsfgRow.view);
        TextView hint = Ui.small(this, Lang.t(this, "A game's own settings (⋯ on its card) override these."));
        hint.setPadding(dp(4), 0, dp(4), 0);
        Ui.add(content, hint, 8);

        Ui.add(content, Ui.section(this, Lang.t(this, "System")), Ui.SECTION_GAP);
        LinearLayout sys = settingsCard(content);
        gpuRow = new Ui.Setting(this, "🎮", Lang.t(this, "GPU driver"), v -> GpuDriver.dialog(this, PICK_DRIVER)).chevron(this);   // custom GPU drivers (GpuDriver.java)
        sys.addView(gpuRow.view);
        langRow = new Ui.Setting(this, "🗣️", Lang.t(this, "Language"), v -> languageSheet()).chevron(this);
        sys.addView(langRow.view);
        sys.addView(new Ui.Setting(this, "📁", Lang.t(this, "Folders"), v -> Folders.dialog(this)).value(Lang.t(this, "Game, pack and save folders")).chevron(this).view);   // the game, pack and save folders, kept across updates (Folders.java)

        Ui.add(content, Ui.section(this, Lang.t(this, "Help")), Ui.SECTION_GAP);
        LinearLayout help = settingsCard(content);
        help.addView(new Ui.Setting(this, "📋", Lang.t(this, "Readiness check"), v -> startActivityForResult(new Intent(this, ReadinessActivity.class), ReadinessActivity.REQUEST))
                .value(Lang.t(this, "Can this phone run your games?")).chevron(this).view);   // (ReadinessActivity.java)
        help.addView(new Ui.Setting(this, "🧪", Lang.t(this, "Graphics self-test"), v -> startActivity(new Intent(this, SelftestActivity.class))).value(Lang.t(this, "Needs no game")).chevron(this).view);
        help.addView(new Ui.Setting(this, "📨", Lang.t(this, "Share log"), v -> shareLog()).value(Lang.t(this, "The newest log")).chevron(this).view);

        Ui.add(content, Ui.section(this, Lang.t(this, "About")), Ui.SECTION_GAP);
        LinearLayout about = Ui.add(content, Ui.card(this), 8);
        TextView tag = Ui.chip(this, Prefs.buildTag(this));   // which build this is (also the first line of every log)
        tag.setTextIsSelectable(true);
        about.addView(tag, Ui.wrap());
        StringBuilder ids = new StringBuilder();
        for (Profile p : Profile.all()) ids.append(ids.length() == 0 ? "" : ", ").append(p.titleId);
        TextView t = Ui.small(this, Lang.t(this, "Title IDs: ") + ids + "\n" + aboutLicense(this) + Lang.t(this, " Credits and the licenses of its parts: NOTICE.md in the source, github.com/") + Updater.REPO + ".");
        t.setTextIsSelectable(true);
        Ui.add(about, t, 10);
        refreshSettings();
    }

    /**
     * The About screen's license line. NativeAYN is GPL-3.0-or-later as a whole: this APK's GPL-3.0-or-later parts are LSFG frame generation
     * (dm/gfx/vk_android/lsfg, unless built with -PnxLsfg=false) and, with the on-phone packer, the shader translator (libdmsh_xlate.so, InstEmitAmpereMesh.cs)
     */
    static String aboutLicense(android.content.Context c) {
        String gpl = BuildConfig.NX_LSFG && "native".equals(BuildConfig.NX_PACK_ENGINE) ? Lang.t(c, "LSFG frame generation and the shader translator")
                : BuildConfig.NX_LSFG ? Lang.t(c, "LSFG frame generation") : "native".equals(BuildConfig.NX_PACK_ENGINE) ? Lang.t(c, "the shader translator") : null;
        return Lang.t(c, "License: GPL-3.0-or-later as a whole") + (gpl == null ? "." : Lang.t(c, " (this build includes GPL-3.0-or-later code: ") + gpl + ").");
    }

    /** A settings group's card of rows, under its section label */
    private LinearLayout settingsCard(LinearLayout content) { return Ui.add(content, Ui.listCard(this), 8); }

    /** The settings rows' current values (after a dialog, on resume) */
    /** Settings > Language: the app's two languages; the choice is kept and the whole screen rebuilds in it right away */
    private void languageSheet() {
        new AlertDialog.Builder(this).setTitle(Lang.t(this, "Language"))
                .setItems(new String[] {"Português", "English"}, (d, w) -> {
                    Prefs.lang(this, w == 0 ? "pt" : "en");
                    recreate();   // every Home screen, card and dialog rebuilds in the new language
                })
                .setNegativeButton(Lang.t(this, "Cancel"), null).show();
    }

    private void refreshSettings() {
        if (overlayRow == null) return;
        langRow.value(Lang.pt(this) ? "Português" : "English");
        updateRow.value("NativeAYN " + Updater.version(this) + (Updater.available(this) ? "" : Lang.t(this, " · checks off")));
        boolean ov = Prefs.overlay(this);
        if (overlayRow.toggle.isChecked() != ov) overlayRow.toggle.setChecked(ov);
        int n = 0;
        for (String[] it : Overlay.ITEMS) if (Prefs.overlayItem(this, it[0])) n++;
        overlayRow.value(ov ? Lang.t(this, "On · ") + n + Lang.t(this, " of ") + Overlay.ITEMS.length + Lang.t(this, " items") : Lang.t(this, "Off"));
        resRow.value(resLabel());
        capRow.value(capLabel(Prefs.fpsCap(this)));
        lsfgRow.value(!BuildConfig.NX_LSFG ? Lang.t(this, "Not in this build") : Lsfg.on(this) ? Lang.t(this, "On · ") + Lsfg.multiplier(this) + "x" + (Lsfg.find(this) == null ? Lang.t(this, " · Lossless.dll missing") : "") : Lang.t(this, "Off"));
        GpuDriver.Info d = GpuDriver.selected(this);
        gpuRow.value(d == null ? Lang.t(this, "System") : d.label());
    }

    @Override public void onWindowFocusChanged(boolean focus) {
        super.onWindowFocusChanged(focus);
        if (focus) refreshSettings();   // back from a dialog (GPU driver, Folders, …)
    }

    @Override protected void onNewIntent(Intent i) {
        super.onNewIntent(i);
        fromIntent(i);
    }

    /** adb: am start -n dev.nxnative.android/.MainActivity --es game <game folder> [--es pack <pack folder>] [--es save <save folder>] [--ez run true] adds the game without
     *  the pickers (android/TESTING.md); --es add_game <folder or .nup> takes Add game's own path instead (gamePicked: all of a folder's games added, Prepare asked); save is the save folder Folders would pick for it (test runs of a play or release app keep away from the user's saves/<id>) */
    private void fromIntent(Intent i) {
        if (settingsPage && (i.hasExtra("game") || i.hasExtra("add_game") || i.hasExtra("ondevice_dump") || i.hasExtra("ondevice_pause"))) page(false);   // these act on Home's cards
        Ondevice.fromIntent(this, i);   // --es ondevice_dump <dump folder> [--ez ondevice_start true] (Ondevice)
        GpuDriver.fromIntent(this, i);   // --es gpu_driver_zip <zip> / --es gpu_driver <name | system>
        PackUi.fromIntent(this, i);   // test app: --es pack_title <id> --es pack_export <folder> | --es pack_import <zip or folder>
        String fg = i.getStringExtra("frontend_game");   // FrontendLaunch: a game it could not start at once
        if (fg != null) { fromFrontend(new File(fg)); return; }
        String ag = i.getStringExtra("add_game");   // Add game's own path for a picked folder or .nup (gamePicked), without the picker
        if (ag != null) { if (!needAccess()) gamePicked(new File(ag)); return; }
        String game = i.getStringExtra("game");
        if (game == null) return;
        File dir = new File(game);   // a game folder or a .nup (NUP.md)
        String id;
        try { id = Pack.titleIdOf(dir); } catch (IOException e) { error(e.getMessage()); return; }
        String p = i.getStringExtra("pack");
        File pack = p != null ? new File(p) : Folders.packFor(this, dir, id);
        if (pack == null) { error(Lang.t(this, "No pack found for ") + id + Lang.t(this, " next to ") + game + "."); return; }
        if (!addWithPack(dir, id, pack)) return;
        String save = i.getStringExtra("save");
        if (save != null) Prefs.folder(this, "save_" + id, new File(save).getPath());
        if (i.getBooleanExtra("run", false)) for (Game g : games) if (GameList.samePath(g.gameDir, dir.getPath())) { launch(g); break; }
    }

    /** A game a frontend sent that needs the user (FrontendLaunch.ready): its card's Play says what is missing and offers the fix; a new game takes the steps of a picked one */
    private void fromFrontend(File game) {
        if (settingsPage) page(false);
        refresh();   // FrontendLaunch may have added its card
        if (needAccess()) return;
        for (Game g : games) if (GameList.samePath(g.gameDir, game.getPath())) { launch(g); return; }
        gamePicked(game);
    }

    @Override protected void onResume() {
        super.onResume();
        resumed = true;
        if (gameBox != null && Game.load(this).size() != games.size()) refresh();   // a card FrontendLaunch added while Home was in the background
        onResumeViews();
        Ondevice.resume(this);
        Updater.resume(this);   // back from "Install unknown apps" with an update download waiting
    }

    private void onResumeViews() {
        if (gameBox != null && (accessCard != null) != !Environment.isExternalStorageManager()) refresh();   // granted or revoked since Home was built: the tile joins or leaves the shelf
        refreshSettings();
    }

    /** Pauses work while the screen is covered (a game, the self-test, settings) or off */
    @Override protected void onPause() {
        resumed = false;
        Ondevice.pause(this);
        super.onPause();
    }

    private int dp(int v) { return Math.round(v * getResources().getDisplayMetrics().density); }

    /** Home's shelf: one card per game, Add game as the last tile, and the file-access tile first while that access is missing */
    void refresh() {
        games.clear();
        games.addAll(Game.load(this));
        if (gameBox == null) return;
        if (!merged && games.size() > 1) {   // a list an older version left with two entries of one game: one card each (GameList.collapse; reads the dumps: off the main thread)
            merged = true;
            new Thread(() -> { if (Game.collapse(this)) runOnUiThread(() -> { if (!isFinishing()) refresh(); }); }, "nx-list-merge").start();
        }
        gameBox.removeAllViews();
        accessCard = null;
        // while a game is being prepared its card says what happens (the steps card would only repeat it); Add game is the last card of the shelf,
        // and with no game at all it is the shelf's only card (the empty state is just the plus tile, the same one the shelf shows)
        HorizontalScrollView hs = new HorizontalScrollView(this);   // the games side by side, console style: box-art tiles in a row you scroll sideways
        hs.setHorizontalScrollBarEnabled(false);
        hs.setClipToPadding(false);
        hs.setPadding(dp(10), dp(14), dp(10), dp(14));   // the raised (scaled) card spills past the row: a ScrollView clips to its bounds, so give it room inside
        hs.setClipChildren(false);   // the selected card grows a little: do not clip it
        hs.setOverScrollMode(View.OVER_SCROLL_NEVER);
        LinearLayout row = new LinearLayout(this);
        row.setOrientation(LinearLayout.HORIZONTAL);
        row.setClipChildren(false);
        hs.addView(row);
        LinearLayout.LayoutParams hpl = Ui.fill();
        hpl.topMargin = dp(2);
        gameBox.addView(hs, hpl);
        if (!Environment.isExternalStorageManager()) {   // the file-access request is a tile of the shelf, beside the plus, not a banner across the page
            accessCard = accessCard();
            row.addView(accessCard, Ui.wrap());
        }
        for (Game g : games) {
            LinearLayout.LayoutParams lp = Ui.wrap();
            if (row.getChildCount() > 0) lp.leftMargin = dp(18);
            row.addView(gameCard(g), lp);
        }
        LinearLayout.LayoutParams apl = Ui.wrap();
        apl.leftMargin = row.getChildCount() > 0 ? dp(18) : 0;
        row.addView(addCard(), apl);
        if (row.getChildCount() > 1) {   // the first game starts selected, like the console shelf (the access tile is not a card: skip to the first focusable one)
            View first = null;
            for (int k = 0; k < row.getChildCount(); k++)
                if (row.getChildAt(k).isFocusable()) { first = row.getChildAt(k); break; }
            if (first != null) hs.post(first::requestFocus);
        }
    }

    /** The shelf's last card: a plus tile that adds a game */
    private View addCard() {
        int w = dp(146), h = dp(166);
        LinearLayout col = new LinearLayout(this);
        col.setOrientation(LinearLayout.VERTICAL);
        col.setGravity(Gravity.CENTER_HORIZONTAL);
        FrameLayout tile = new FrameLayout(this);
        tile.setBackground(Ui.round(Ui.col(this, R.color.bn_card), dp(14), Ui.col(this, R.color.bn_card_stroke), dp(1)));
        tile.setClipToOutline(true);
        tile.setElevation(dp(3));   // the same light shadow as the game tiles
        ImageView plus = new ImageView(this);
        plus.setImageResource(R.drawable.ic_add);
        plus.setColorFilter(Ui.col(this, R.color.bn_text_2));   // a soft grey plus (not the blue accent): the plus tile should not shout
        plus.setImportantForAccessibility(View.IMPORTANT_FOR_ACCESSIBILITY_NO);
        tile.addView(plus, new FrameLayout.LayoutParams(dp(44), dp(44), Gravity.CENTER));
        col.addView(tile, new LinearLayout.LayoutParams(w, h));   // the plus tile is the size of a game card
        col.setContentDescription(Lang.t(this, "Add game"));
        TextView name = Ui.text(this, Lang.t(this, "Add game"), 15, R.color.bn_text, 700);
        name.setGravity(Gravity.CENTER);
        LinearLayout.LayoutParams nl = Ui.wrap();
        nl.width = w;
        nl.topMargin = dp(10);
        col.addView(name, nl);
        col.setClickable(true);
        col.setFocusable(true);
        col.setOnClickListener(v -> addGame());
        return col;
    }

    /**
     * A game's card in the row: a box-art tile — its icon.jpg (the pack's, or one dropped beside the dump / .nup) over the blue-gradient letter when there is
     * none — its name and state under it, and ⋯ Manage at the tile's corner. Tapping the card is the big button (Play, or Prepare when the pack does not fit);
     * a long press opens Manage.
     */
    private View gameCard(Game g) {
        int w = dp(146), h = dp(166);
        LinearLayout col = new LinearLayout(this);
        col.setOrientation(LinearLayout.VERTICAL);
        col.setGravity(Gravity.CENTER_HORIZONTAL);
        FrameLayout tile = new FrameLayout(this);
        tile.setBackground(Ui.round(0xFF000000, dp(14), 0, 0));   // opaque so its rounded outline casts a shadow; the art covers it fully
        tile.setClipToOutline(true);
        tile.setElevation(dp(4));   // the light shadow under the image (Console Shelf style)
        tile.addView(Ui.tile(this, g.name, 146), new FrameLayout.LayoutParams(w, h));
        ImageView art = new ImageView(this);
        art.setScaleType(ImageView.ScaleType.CENTER_CROP);
        art.setVisibility(View.GONE);   // the letter tile shows until the art decodes
        art.setImportantForAccessibility(View.IMPORTANT_FOR_ACCESSIBILITY_NO);
        tile.addView(art, new FrameLayout.LayoutParams(w, h));
        ImageView more = Ui.more(this, Lang.t(this, "Manage ") + g.name, v -> manage(g));
        more.setFocusable(false);   // the card is the focusable unit; ⋯ stays a touch target
        FrameLayout.LayoutParams mp = new FrameLayout.LayoutParams(dp(38), dp(38), Gravity.TOP | Gravity.END);
        mp.topMargin = dp(4);
        mp.rightMargin = dp(4);
        tile.addView(more, mp);
        col.addView(tile, Ui.wrap());
        TextView name = Ui.text(this, g.name, 15, R.color.bn_text, 700);
        name.setMaxLines(2);
        name.setEllipsize(TextUtils.TruncateAt.END);
        name.setGravity(Gravity.CENTER);
        LinearLayout.LayoutParams nl = Ui.wrap();
        nl.width = w;
        nl.topMargin = dp(10);
        col.addView(name, nl);
        boolean other = Profile.of(g.titleId) == null;   // no profile in this APK: it cannot run here (Profile.missing says why)
        TextView st = other ? Ui.status(this, Lang.t(this, "Not supported"), R.color.bn_danger) : Ui.status(this, "Checking…", R.color.bn_text_2);
        LinearLayout.LayoutParams sl = Ui.wrap();
        sl.topMargin = dp(2);
        col.addView(st, sl);
        Ui.Action play = Ui.action(this, R.drawable.ic_play, Lang.t(this, "Play"), Ui.Kind.PRIMARY, v -> launch(g));
        play.setVisibility(View.GONE);   // the card itself is the button; check() keeps this one's label and action for the tap
        col.addView(play, new LinearLayout.LayoutParams(0, 0));
        loadArt(g, art);
        if (!other) check(g, st, play, col);
        col.setClickable(true);
        col.setFocusable(true);
        col.setFocusableInTouchMode(true);   // so one card holds the selection even before a gamepad is used
        // the focused card is the selected one: a deeper shadow and a small raise, like the console shelf this copies (no ring)
        col.setDefaultFocusHighlightEnabled(false);   // no system highlight box around the whole card
        col.setOnFocusChangeListener((v, focused) -> {
            tile.animate().translationZ(focused ? dp(8) : 0f).setDuration(130).start();
            col.animate().scaleX(focused ? 1.06f : 1f).scaleY(focused ? 1.06f : 1f).setDuration(130).start();
        });
        col.setOnClickListener(v -> play.performClick());
        col.setOnLongClickListener(v -> { manage(g); return true; });
        return col;
    }

    /** Decoded card art, keyed by file (the row re-decodes on every refresh otherwise) */
    private static final android.util.LruCache<String, android.graphics.Bitmap> ART = new android.util.LruCache<>(16);

    /**
     * The game's box art, decoded off the main thread. In order: <dump folder>/icon.jpg, a JPG beside a .nup named after it (<name>.jpg) or after its
     * title ID (<title ID>.jpg) — box art the user drops in — and the pack's own icon.jpg (a PC pack copies the icon nx_extract read from the control data).
     */
    private void loadArt(Game g, ImageView art) {
        File gd = new File(g.gameDir);
        File side;
        if (Nup.isNup(gd)) {
            String stem = gd.getName().substring(0, gd.getName().length() - 4);
            File dir = gd.getParentFile();
            side = null;
            for (String cand : new String[] {stem + ".jpg", stem + ".png", g.titleId + ".jpg", g.titleId + ".png"}) {   // the box art the user drops in, JPG or PNG
                File t = new File(dir, cand);
                if (t.isFile()) { side = t; break; }
            }
        } else {
            File inDump = new File(gd, "icon.jpg");
            side = inDump.isFile() ? inDump : null;
        }
        File inPack = new File(g.packDir, "icon.jpg");
        File f = side != null && side.isFile() ? side : (inPack.isFile() ? inPack : null);
        if (f == null) return;
        String key = f.getAbsolutePath();
        android.graphics.Bitmap have = ART.get(key);
        if (have != null) { art.setImageBitmap(have); art.setVisibility(View.VISIBLE); return; }
        new Thread(() -> {
            android.graphics.Bitmap b = decodeArt(f);
            if (b == null) return;
            ART.put(key, b);
            runOnUiThread(() -> { art.setImageBitmap(b); art.setVisibility(View.VISIBLE); });
        }, "nx-art").start();
    }

    private static android.graphics.Bitmap decodeArt(File f) {
        android.graphics.BitmapFactory.Options o = new android.graphics.BitmapFactory.Options();
        o.inJustDecodeBounds = true;
        android.graphics.BitmapFactory.decodeFile(f.getPath(), o);
        if (o.outWidth <= 0) return null;
        o.inSampleSize = 1;
        while (o.outWidth / (o.inSampleSize * 2) >= 640) o.inSampleSize *= 2;   // the tile is small: keep a few times its width, no more
        o.inJustDecodeBounds = false;
        try { return android.graphics.BitmapFactory.decodeFile(f.getPath(), o); }
        catch (OutOfMemoryError e) { return null; }
    }

    /** Why a game cannot start as it is, in a few words (unfinished or busy pack code, another game build, no current shader library), and whether Prepare fixes it */
    private static final class Problem {
        final String label;
        final boolean prepare;   // Prepare (on the phone, from the dump) makes what is missing
        boolean shadersOnly;   // only the shader library is missing
        boolean playable;      // ... and an older library of the same ABI plays while the update makes it (Pack.playableLibrary)
        boolean legacy;        // ... or only one made before versioned updates, played with a warning (Pack.legacyLibrary)
        String detail = "";    // what was compared (BuildCheck: the build IDs that differ), shown small under the state and logged

        Problem(String label, boolean prepare) { this.label = label; this.prepare = prepare; }
    }

    /** The card's short state when the game cannot start as it is; null when it can */
    private Problem problem(Game g) {
        File pd = new File(g.packDir), gd = new File(g.gameDir);
        boolean dump = Ondevice.isDump(gd);
        Pack p;
        try { p = Pack.read(pd); } catch (IOException e) { return new Problem(dump ? Lang.t(this, "Needs preparing") : Lang.t(this, "No pack"), dump); }
        if (p.simulated) return new Problem(dump ? Lang.t(this, "Needs preparing") : Lang.t(this, "No pack"), dump);   // a simulated engine's placeholder (0.1.0's Prepare made one): never started
        if (p.unfinishedModule() != null) return new Problem(dump ? Lang.t(this, "Needs preparing") : Lang.t(this, "Pack unfinished"), dump);
        if (PackCheck.rewriting(this, pd, gd, p.titleId) != null) return new Problem("Preparing…", false);
        BuildCheck.Result b = BuildCheck.build(p, gd);
        if (!b.ok()) {
            android.util.Log.w("nxnative", g.titleId + " " + g.gameDir + " with pack " + g.packDir + ": " + b.label() + ": " + b.detail);
            Problem s = new Problem(b.label(), dump && (b.kind == BuildCheck.Kind.OTHER_BUILD || b.kind == BuildCheck.Kind.MISSING));   // Prepare makes a pack of the dump's build; it cannot fix unreadable files, an unsupported build or another version's data
            s.detail = b.detail;
            return s;
        }
        if (Pack.libraryProblem(pd, g.titleId) != null) {
            Problem s = new Problem(Lang.t(this, "Shaders needed"), dump);
            s.shadersOnly = true;
            s.playable = Pack.playableLibrary(pd, g.titleId) != null;
            s.legacy = !s.playable && Pack.legacyLibrary(pd, g.titleId) != null;
            return s;
        }
        return null;
    }

    /** The card's state, checked off the main thread (NSO headers, the stamp, and for a ready pack the rewriter's key, which hashes the modules) */
    private void check(Game g, TextView st, Ui.Action play, LinearLayout info) {
        new Thread(() -> {
            // An APK update whose shaders did not change: the pack's older library becomes this version's by its header (Pack.restamp), before anything else is decided
            String restamped = Pack.restamp(new File(g.packDir), g.titleId);
            if (restamped != null) android.util.Log.i("nxnative", g.titleId + ": shader library v-" + restamped + " taken over as v-" + BuildConfig.NX_DMSH_VK_CHAIN + " (restamp)");
            Problem problem = problem(g);
            Boolean ok = null;
            if (problem == null) try { ok = PackCheck.code(this, Pack.read(new File(g.packDir)), new File(g.gameDir)); } catch (IOException e) { ok = null; }
            Boolean code = ok;
            runOnUiThread(() -> {
                if (info.getParent() == null) return;
                // An APK update's new shaders: made in the background with no question when the pack's older library is one the delta manifest knows (Ondevice.autoUpdate)
                if (problem != null && problem.shadersOnly && problem.prepare) Ondevice.autoUpdate(this, new File(g.gameDir), new File(g.packDir), g.titleId);
                if (problem == null) Ui.status(st, Lang.t(this, "Ready"), R.color.bn_leaf);
                else if (problem.playable) Ui.status(st, Lang.t(this, "Ready · updating shaders"), R.color.bn_leaf);
                else if (problem.legacy) Ui.status(st, Lang.t(this, "Ready · shaders need one update"), R.color.bn_accent);
                else Ui.status(st, problem.label, problem.prepare ? R.color.bn_accent : R.color.bn_danger);
                if (problem != null && !problem.detail.isEmpty()) {   // which build IDs (or which file) the state is about
                    TextView d = Ui.small(this, problem.detail);
                    d.setTextIsSelectable(true);
                    d.setTextSize(11);
                    LinearLayout.LayoutParams dl = Ui.wrap();
                    dl.topMargin = dp(2);
                    info.addView(d, dl);
                }
                if (problem != null && problem.legacy) {   // Play stays Play; a chip says why the shaders want one more update
                    TextView n = Ui.warnChip(this, Lang.t(this, "Shaders need one update"), Lang.t(this, "Shaders need one update"), ShaderLibs.LEGACY_WARNING);
                    LinearLayout.LayoutParams wl = Ui.wrap();
                    wl.topMargin = dp(6);
                    info.addView(n, wl);
                }
                if (problem != null && problem.prepare && !problem.playable && !problem.legacy) {   // the big button prepares (Ondevice asks first and says what it will make)
                    play.setText(problem.shadersOnly ? Lang.t(this, "Prepare shaders") : Lang.t(this, "Prepare"));
                    play.setIcon(R.drawable.ic_build);
                    play.setOnClickListener(v -> { if (!needAccess()) Ondevice.prepare(this, new File(g.gameDir), false); });
                }
                if (!Boolean.FALSE.equals(code)) return;
                // A pack whose code other tools made: a small chip; its tap says what that means
                TextView n = Ui.warnChip(this, Lang.t(this, "Pack from other tools"), Lang.t(this, "Pack from other tools"),
                        Lang.t(this, "This game's pack was made by other tools than this app's. It usually still runs. If the game misbehaves, ⋯ › Prepare again remakes it from your dump."));
                LinearLayout.LayoutParams lp = Ui.wrap();
                lp.topMargin = dp(6);
                info.addView(n, lp);
            });
        }, "nx-pack-check").start();
    }

    /**
     * A game's Manage sheet (⋯ on its card, or a long press): a list of actions (its folders, its settings, its log, its saves, Prepare again for a dump), its facts
     * (title ID, folders) below, and, apart at the bottom, Remove from the list (asked first; it deletes nothing on the storage). Play never sits next to it.
     */
    private void manage(Game g) {
        LinearLayout box = new LinearLayout(this);
        box.setOrientation(LinearLayout.VERTICAL);
        File save = Folders.save(this, g);
        String lp = Pack.libraryProblem(new File(g.packDir), g.titleId);
        String noProfile = Profile.missing(g.titleId, g.name);
        AlertDialog[] d = new AlertDialog[1];
        if (noProfile != null) Ui.add(box, Ui.small(this, noProfile), 0);
        // Saves: backups in <Switch2>/save-backups/<id>/, verified; a restore backs up the current save first (SaveUi, SaveStore)
        if (noProfile == null) Ui.add(box, new Ui.Setting(this, "⚙️", Lang.t(this, "Game settings"), v -> { d[0].dismiss(); GameSettings.dialog(this, g); }).chevron(this).view, 0);
        Ui.add(box, new Ui.Setting(this, "📁", Lang.t(this, "Folders"), v -> { d[0].dismiss(); Folders.dialog(this, g); }).chevron(this).view, 0);
        Ui.add(box, new Ui.Setting(this, "💾", Lang.t(this, "Back up saves"), v -> { d[0].dismiss(); SaveUi.backup(this, g); }).value("To " + SaveUi.backups(g)).chevron(this).view, 0);
        Ui.add(box, new Ui.Setting(this, "↩️", Lang.t(this, "Restore saves"), v -> { d[0].dismiss(); SaveUi.restoreList(this, g); }).chevron(this).view, 0);
        Ui.add(box, new Ui.Setting(this, "📥", Lang.t(this, "Import save"), v -> { d[0].dismiss(); SaveUi.importPick(this, g); }).chevron(this).view, 0);
        if (Ondevice.isDump(new File(g.gameDir)) && noProfile == null)
            Ui.add(box, new Ui.Setting(this, "🔁", Lang.t(this, "Prepare again"), v -> { d[0].dismiss(); Ondevice.prepare(this, new File(g.gameDir), true); }).value("Remakes what does not fit").chevron(this).view, 0);
        // The pack as one .zip, out and in (PackUi, PackZip): an import goes into the app's packs/<id> and is checked before it replaces anything
        Ui.add(box, new Ui.Setting(this, "📦", "Export pack…", v -> { d[0].dismiss(); PackUi.exportPick(this, g); }).value(Lang.t(this, "One .zip, into a folder you pick")).chevron(this).view, 0);
        if (noProfile == null)
            Ui.add(box, new Ui.Setting(this, "📲", "Import pack…", v -> { d[0].dismiss(); PackUi.importPick(this, g); }).value(Lang.t(this, "A .zip or a pack folder")).chevron(this).view, 0);
        Ui.add(box, new Ui.Setting(this, "📨", Lang.t(this, "Share log"), v -> { d[0].dismiss(); shareLog(g.titleId); }).chevron(this).view, 0);
        TextView info = Ui.small(this, Lang.t(this, "Title ID: ") + g.titleId + "\nGame: " + g.gameDir + (Nup.isNup(new File(g.gameDir)) ? " (.nup)" : "") + "\nPack: " + g.packDir
                + "\nShaders: " + (lp == null ? Lang.t(this, "present (") + BuildConfig.NX_DMSH_VK_CHAIN + ")" : lp) + "\nSaves: " + (save != null ? save.getPath() : Lang.t(this, "not set")));
        info.setTextIsSelectable(true);
        info.setTextSize(12);
        info.setPadding(dp(4), 0, dp(4), 0);
        Ui.add(box, info, 16);
        Ui.add(box, Ui.divider(this), 16, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, dp(1)));
        Ui.add(box, Ui.button(this, Lang.t(this, "Remove from list"), Ui.Kind.DANGER, v -> { d[0].dismiss(); confirmRemove(g); }), 16);
        d[0] = Ui.sheet(this, g.name, box).setPositiveButton(Lang.t(this, "Done"), null).show();
    }

    private void openAccessSettings() {
        try {
            startActivity(new Intent(Settings.ACTION_MANAGE_APP_ALL_FILES_ACCESS_PERMISSION, Uri.parse("package:" + getPackageName())));
        } catch (Exception e) {
            startActivity(new Intent(Settings.ACTION_MANAGE_ALL_FILES_ACCESS_PERMISSION));
        }
    }

    boolean needAccess() {
        if (Environment.isExternalStorageManager()) return false;
        new AlertDialog.Builder(this).setTitle(Lang.t(this, "Allow file access")).setMessage("The app needs All files access to read your game dump. Allow it, then come back.")
                .setPositiveButton(Lang.t(this, "Open settings"), (d, w) -> openAccessSettings()).setNegativeButton(Lang.t(this, "Cancel"), null).show();
        return true;
    }

    void pickFolder(int request, String title) {
        Toast.makeText(this, title, Toast.LENGTH_LONG).show();
        Intent i = new Intent(Intent.ACTION_OPEN_DOCUMENT_TREE);
        i.putExtra(DocumentsContract.EXTRA_INITIAL_URI, Uri.parse("content://com.android.externalstorage.documents/document/primary%3A"));
        startActivityForResult(i, request);
    }

    /** Add game: the dump is a folder (base/, updates/) or one .nup file (NUP.md); the user says which, then the system picker opens for it */
    void addGame() {
        if (needAccess()) return;
        new AlertDialog.Builder(this).setTitle("Add game: your dump is…")
                .setItems(new String[] {Lang.t(this, "A folder (with base/, and updates/ if any)"), Lang.t(this, "A .nup file")}, (d, w) -> {
                    if (w == 0) pickFolder(PICK_GAME, Lang.t(this, "Pick your dump folder (with base/, and updates/ if any)"));
                    else pickNup();
                })
                .setNegativeButton(Lang.t(this, "Cancel"), null).show();
    }

    /** The system file picker for a .nup; its document is turned into its /storage path (PathUtil.documentToPath), as a picked folder is */
    private void pickNup() {
        Toast.makeText(this, Lang.t(this, "Pick your game's .nup file"), Toast.LENGTH_LONG).show();
        Intent i = new Intent(Intent.ACTION_OPEN_DOCUMENT).addCategory(Intent.CATEGORY_OPENABLE).setType("*/*");
        i.putExtra(DocumentsContract.EXTRA_INITIAL_URI, Uri.parse("content://com.android.externalstorage.documents/document/primary%3A"));
        startActivityForResult(i, PICK_NUP);
    }

    @Override protected void onActivityResult(int request, int result, Intent data) {
        super.onActivityResult(request, result, data);
        if (Ondevice.activityResult(this, request, result, data)) return;
        if (request == ReadinessActivity.REQUEST) { if (result == ReadinessActivity.FIX_DRIVER) GpuDriver.dialog(this, PICK_DRIVER); return; }   // its "Pick a GPU driver"
        if (result != RESULT_OK || data == null || data.getData() == null) return;
        if (request == PICK_DRIVER) { GpuDriver.picked(this, data.getData()); return; }
        if (PackUi.documentResult(this, request, data.getData())) return;
        if (request == PICK_NUP) {
            String p = PathUtil.documentToPath(data.getData());
            if (p == null) { error(Lang.t(this, "That file is not on this phone's storage by a path the app can read (") + data.getData() + "). Pick it from the phone's storage (☰ › the phone's name), not from Recent or Downloads."); return; }
            gamePicked(new File(p));
            return;
        }
        if (request == PICK_DLL) {
            String err = Lsfg.install(this, data.getData());
            Toast.makeText(this, err == null ? Lang.t(this, "Lossless.dll installed") : Lang.t(this, "Lossless.dll not installed: ") + err, Toast.LENGTH_LONG).show();
            return;
        }
        String path = PathUtil.treeToPath(data.getData());
        if (path == null) { error(Lang.t(this, "That folder is not on this phone's storage (") + data.getData() + ")."); return; }
        File dir = new File(path);
        if (Folders.activityResult(this, request, dir)) return;
        if (SaveUi.activityResult(this, request, dir)) return;
        if (PackUi.activityResult(this, request, dir)) return;
        if (request == PICK_GAME) gamePicked(dir);
        else if (request == PICK_PACK && pendingGame != null) { pendingPackPick = dir; addWithPack(pendingGame, pendingId, dir); }
    }

    /**
     * A picked game: one .nup, one dump folder (base/exefs/main.npdm or exefs/main.npdm), or a folder that holds games (PackFind.gamesIn: its .nup files and
     * dump subfolders), all of which are added (addAll); a folder with one game is that game
     */
    private void gamePicked(File game) {
        if (Nup.isNup(game) || Nup.wrongCase(game)) { nupPicked(game); return; }
        if (Pack.npdmOf(game) != null) { addAll(List.of(game), true); return; }
        List<File> found = PackFind.gamesIn(game);
        if (found.size() == 1) { gamePicked(found.get(0)); return; }
        if (found.isEmpty()) {
            error(game + " has no main.npdm (base/exefs/, updates/<version>/exefs/ or exefs/), no .nup file and no game folder in it. Pick the game's own folder, its .nup file, or the folder that holds your games.");
            return;
        }
        addAll(found, false);
    }

    /** A single .nup (NUP.md): its name and access checked here, the rest as every game (addAll) */
    private void nupPicked(File nup) {
        if (Nup.wrongCase(nup)) { error(nup.getName() + ": rename it so that its name ends in \".nup\" (lower case); the game and the tools read only that name."); return; }
        if (!nup.canRead()) { error(nup + " cannot be read. Check that All files access is on."); return; }
        addAll(List.of(nup), true);
    }

    /** What the background check of Add game found for one game */
    private static final class Pick {
        final File game;
        String id, name, error;   // error: why it cannot be added (unreadable, no profile in this APK), in a sentence
        boolean unsupported;      // no profile in this APK
        boolean dump;             // a dump Prepare starts from (Pack.isDump)
        PackFind.Found pack;      // a pack that plays now or after the automatic shader update (Folders.find), null: none
        DumpLayout.Found found;   // without a pack: the version Prepare would start from, or why it cannot (DumpLayout.find)
        String key;               // its game build (Game.key: title, version, main's build ID), null when it cannot be told
        final List<Game> others = new ArrayList<>();   // Home's entries of the same title that are another dump (GameList.others): the user decides

        Pick(File game) { this.game = game; name = game.getName(); }
    }

    /**
     * Add game for one or more games: each is identified off the main thread (a .nup's TOC is hashed) with its best pack (Folders.find: the picked folder,
     * the app's, beside the game, &lt;id&gt;-work/&lt;any&gt; above it) and checked (PackCheck.blocking), then added on the main thread. A game with a pack that plays
     * now or after the automatic shader update is added with it, with no question. single: one picked game, as before: a dump without such a pack goes to Prepare
     * (Ondevice asks first; it offers an existing pack folder or Import pack… too), a one-version folder to the pack folder picker, and a problem is one message.
     * Several (a picked folder's games): a dump without a usable pack gets a card with its Prepare button (one job at a time is the user's to start), and one
     * message sums up what was added, what this app cannot run and what needs Prepare.
     * One card per game (GameList): the same file or the same title and build replaces its entry; another dump of a title on the list is asked about (single:
     * replace it or keep both) or kept beside it (several, said in the summary)
     */
    private void addAll(List<File> picked, boolean single) {
        Toast.makeText(this, single ? Lang.t(this, "Checking ") + picked.get(0).getName() + "…" : Lang.t(this, "Checking ") + picked.size() + " games…", Toast.LENGTH_SHORT).show();
        new Thread(() -> {
            List<Pick> out = new ArrayList<>();
            for (File g : picked) {
                Pick k = new Pick(g);
                out.add(k);
                if (Nup.wrongCase(g)) { k.error = g.getName() + ": rename it so that its name ends in \".nup\" (lower case)."; continue; }
                try { k.id = Pack.titleIdOf(g); } catch (IOException e) { k.error = e.getMessage(); continue; }
                String why = Profile.missing(k.id, single ? Lang.t(this, "This game") : g.getName());
                if (why != null) { k.unsupported = true; k.error = why; continue; }
                k.name = Profile.of(k.id).name;
                k.dump = Ondevice.isDump(g);   // false when this APK has no packer: then it is added like any folder without a pack
                String pp = Folders.pickedPack(this, k.id);
                File pd = pp == null ? null : new File(pp);
                try { if (pd != null && !Pack.read(pd).titleId.equals(k.id)) pd = null; } catch (IOException e) { pd = null; }
                k.pack = Folders.find(this, pd, g, k.id);
                if (k.pack == null) k.found = DumpLayout.find(g);   // no pack: whether Prepare can start from it (any layout), or why not (another build, incomplete)
                else if (!k.pack.pack.gameVersion(g).equals(k.pack.pack.version))   // the pack's build under another version number of this dump: the same code, played as it is
                    android.util.Log.i("nxnative", "Add game: " + g.getName() + " keeps the pack's build (version " + k.pack.pack.version + ") as version " + k.pack.pack.gameVersion(g) + ": same build IDs, same code");
                k.key = Game.key(g.getPath(), k.id);   // read here, off the main thread: the add takes the keys from the cache
                List<Game> list = Game.load(this);
                for (int i : GameList.others(list, k.id, g.getPath(), k.key, Game::key)) k.others.add(list.get(i));
                android.util.Log.i("nxnative", "Add game: " + g + " (" + k.id + ", " + k.key + "): " + (k.pack == null ? "no usable pack" : "pack " + k.pack.dir + (k.pack.tier == PackFind.READY ? " (ready)" : " (shader update)"))
                        + (k.others.isEmpty() ? "" : "; " + k.others.size() + " other dump(s) of it on the list"));
            }
            runOnUiThread(() -> { if (!isFinishing()) added(out, single); });
        }, "nx-add-game").start();
    }

    private void added(List<Pick> picks, boolean single) {
        if (single) {
            Pick k = picks.get(0);
            if (k.error != null) { error(k.error); return; }
            askReplace(k.name, k.game, k.others, replace -> {
                if (k.pack != null) { addWithPack(k.game, k.id, k.pack.dir, replace); return; }
                if (replace != null) removeEntries(replace);   // the user chose this dump: the other's card goes now (nothing is deleted), this one's comes with its pack
                if (Ondevice.available() && k.found != null && k.found.error == null) {
                    if (k.found.build >= 0 && k.found.build != k.found.version)
                        android.util.Log.i("nxnative", "Add game: " + k.game.getName() + " " + DumpLayout.name(k.found.version) + " is " + Profile.of(k.id).buildName(k.found.build) + " by its build IDs");
                    Ondevice.prepare(this, k.game, false);
                    return;
                }   // a dump of a supported build: Prepare makes its pack
                pendingGame = k.game;
                pendingId = k.id;
                String what = Nup.isNup(k.game) ? ".nup" : "folder";
                // Why Prepare cannot start from it (another build than this app's, an incomplete dump), plainly; a pack made elsewhere can still be picked
                String why = k.found != null && k.found.error != null ? k.found.error
                        : !Ondevice.available() ? Lang.t(this, "No pack found for this ") + what + ". " + Ondevice.UNAVAILABLE
                        : Lang.t(this, "No pack found for this ") + what + Lang.t(this, ", and it is not a dump Prepare can start from.");
                new AlertDialog.Builder(this).setTitle(k.found != null && k.found.version < 0 && k.found.titleId != null && Profile.of(k.found.titleId) != null && !DumpLayout.versions(k.game).isEmpty()
                                ? Lang.t(this, "Another version of ") + k.name : Lang.t(this, "No pack for this ") + what)
                        .setMessage(why + "\n\nIf you have a pack folder made for exactly this " + what + " (with pack.tsv), you can pick it.")
                        .setPositiveButton("OK", null)
                        .setNeutralButton(Lang.t(this, "Pick a pack folder"), (d, w) -> pickFolder(PICK_PACK, Lang.t(this, "Pick the pack folder (android-pack, with pack.tsv)"))).show();
            });
            return;
        }
        List<String> ok = new ArrayList<>(), unsupported = new ArrayList<>(), prepare = new ArrayList<>(), noPack = new ArrayList<>(), failed = new ArrayList<>(), twice = new ArrayList<>();
        for (Pick k : picks) {
            if (k.unsupported) { unsupported.add(k.game.getName() + " (" + k.id + ")"); continue; }
            if (k.error != null) { failed.add(k.error); continue; }
            if (k.pack != null) {
                String err = add(k.game, k.id, k.pack.dir, null);
                if (err == null) ok.add(k.pack.pack.name.isEmpty() ? k.name : k.pack.pack.name);
                else failed.add(k.game.getName() + ": " + err);
            } else if (k.dump) {
                cardFor(k.game, k.id);
                prepare.add(k.name);
            } else noPack.add(k.game.getName());
            if (!k.others.isEmpty() && (k.pack != null || k.dump)) twice.add(k.game.getName());
        }
        refresh();
        StringBuilder m = new StringBuilder();
        if (!ok.isEmpty()) m.append(Lang.t(this, "Added: ")).append(TextUtils.join(", ", ok)).append(".");
        if (!prepare.isEmpty()) m.append(m.length() > 0 ? "\n\n" : "").append(Lang.t(this, "Needs Prepare: ")).append(TextUtils.join(", ", prepare))
                .append(". No pack that fits was found: its card's Prepare makes one on this phone (one game at a time).");
        if (!unsupported.isEmpty()) m.append(m.length() > 0 ? "\n\n" : "").append(Lang.t(this, "Not supported by this app: ")).append(TextUtils.join(", ", unsupported))
                .append(". This app runs: ").append(TextUtils.join(", ", profileIds())).append(".");
        if (!noPack.isEmpty()) m.append(m.length() > 0 ? "\n\n" : "").append(Lang.t(this, "No pack found: ")).append(TextUtils.join(", ", noPack))
                .append(". Add it on its own to pick its pack folder.");
        if (!failed.isEmpty()) m.append(m.length() > 0 ? "\n\n" : "").append(Lang.t(this, "Not added: ")).append(TextUtils.join(" ", failed));
        if (!twice.isEmpty()) m.append(m.length() > 0 ? "\n\n" : "").append(Lang.t(this, "Another dump of the same game was on the list already, so both cards are kept: "))
                .append(TextUtils.join(", ", twice)).append(". ⋯ › Remove from list takes one away (nothing is deleted).");
        android.util.Log.i("nxnative", "Add game: " + m.toString().replace("\n\n", " | "));
        new AlertDialog.Builder(this).setTitle(ok.isEmpty() && prepare.isEmpty() ? Lang.t(this, "No game added") : Lang.t(this, "Games added")).setMessage(m).setPositiveButton("OK", null).show();
    }

    /**
     * Another dump of a game that is on the list (another file, another build or one that cannot be told): asks whether this one replaces it (its list entry
     * only; nothing is deleted) or both are kept. then gets the entries' game paths to replace, or null to keep them; nothing to ask: null at once
     */
    private void askReplace(String name, File game, List<Game> others, java.util.function.Consumer<List<String>> then) {
        if (others.isEmpty()) { then.accept(null); return; }
        List<String> dirs = new ArrayList<>();
        StringBuilder s = new StringBuilder();
        for (Game o : others) { dirs.add(o.gameDir); s.append("\n• ").append(o.gameDir); }
        new AlertDialog.Builder(this).setTitle(Lang.t(this, "Replace ") + name + "?")
                .setMessage(Lang.t(this, "Another dump of ") + name + Lang.t(this, " is on your list:") + s + "\n\nUse " + game.getName() + " instead? Only the list entry changes: no dump, pack or save is deleted.")
                .setPositiveButton(Lang.t(this, "Replace"), (d, w) -> then.accept(dirs))
                .setNeutralButton(Lang.t(this, "Keep both"), (d, w) -> then.accept(null))
                .setNegativeButton(Lang.t(this, "Cancel"), null).show();
    }

    /** These game paths' list entries go (exact paths, as the list keeps them); nothing on the storage changes */
    private void removeEntries(List<String> gameDirs) {
        synchronized (Game.class) {
            List<Game> list = Game.load(this);
            list.removeIf(x -> gameDirs.contains(x.gameDir));
            Game.save(this, list);
        }
        refresh();
    }

    private static List<String> profileIds() {
        List<String> ids = new ArrayList<>();
        for (Profile p : Profile.all()) ids.add(p.titleId);
        return ids;
    }

    /**
     * The list entry of a game, made when it has none: a game that still needs its pack gets a card whose pack folder is the app's packs/&lt;id&gt;, where Prepare
     * and Import pack… put it (the card says Lang.t(this, "Needs preparing") and its button prepares). An existing entry of the same game (the same file, or the same title and
     * build: GameList.matches) is kept as it is
     */
    Game cardFor(File game, String id) {
        List<Game> list = Game.load(this);
        List<Integer> same = GameList.matches(list, id, game.getPath(), Game.key(game.getPath(), id), Game::key);
        if (!same.isEmpty()) return list.get(same.get(0));
        Profile p = Profile.of(id);
        Game g = Game.put(this, new Game(id, p == null ? id : p.name, game.getPath(), AppStorage.pack(this, id).getPath()));
        refresh();
        return g;
    }

    /** Prepare's "Use an existing pack…": the picked pack folder becomes the game's (addWithPack; remembered for this title like a pack picked in Folders) */
    void pickPackFor(File game, String id) {
        pendingGame = game;
        pendingId = id;
        pickFolder(PICK_PACK, "Pick the pack folder (the one with pack.tsv and shaders/)");
    }

    private boolean addWithPack(File game, String id, File packDir) { return addWithPack(game, id, packDir, null); }

    private boolean addWithPack(File game, String id, File packDir, List<String> replace) {
        String err = add(game, id, packDir, replace);
        if (err != null) { error(err); return false; }
        refresh();
        if (packDir.equals(pendingPackPick)) Folders.pickedPack(this, id, packDir.getPath());   // a pack folder the user picked is remembered for this game's next pick and updates
        String note = "";
        String lp = Pack.libraryProblem(packDir, id);
        if (lp != null) note += " " + lp;
        String name;
        try { name = Pack.read(packDir).name; } catch (IOException e) { name = id; }
        Toast.makeText(this, "Added " + (name.isEmpty() ? id : name) + "." + note, Toast.LENGTH_LONG).show();
        return true;
    }

    /**
     * The game into the list with this pack folder (Game.put: replacing the entries of the same file or the same title and build, and those in replace, which
     * the user chose to replace); null when added, else why not in a sentence
     */
    private String add(File game, String id, File packDir, List<String> replace) {
        Pack pack;
        try { pack = Pack.read(packDir); } catch (IOException e) { return e.getMessage(); }
        if (!pack.titleId.equals(id)) return "The pack is for " + pack.titleId + ", the game " + (Nup.isNup(game) ? ".nup" : "folder") + " holds " + id + ".";
        String why = Profile.missing(id, pack.name.isEmpty() ? null : pack.name);
        if (why != null) return why;
        if (Nup.isNup(game)) {
            String romfs;
            try { romfs = pack.romfsPath(game); } catch (IOException e) { return game.getName() + " cannot be used: " + e.getMessage(); }
            if (romfs == null) return game.getName() + " has no " + pack.romfsSelector() + " with a RomFS (the pack is for version " + pack.version + ").";
        } else if (pack.romfs(game) == null) return "No RomFS in " + game + " (looked for " + pack.romfsRel + ", romfs/ and updates/<version>/romfs).";
        Game.put(this, new Game(id, pack.name.isEmpty() ? id : pack.name, game.getPath(), packDir.getPath()), replace);
        return null;
    }

    /** Remove from the list (the Manage sheet): says exactly what goes (the list entry) and what stays (the folders and the saves) */
    private void confirmRemove(Game g) {
        File save = Folders.save(this, g);
        Ui.confirmDanger(this, "Remove " + g.name + "?",
                "Only the list entry goes. Your dump, its pack and your saves" + (save != null ? " (" + save.getPath() + ")" : "") + " stay on the phone; Add game brings it back.",
                "Remove", () -> {
                    List<Game> list = Game.load(this);
                    list.removeIf(x -> x.gameDir.equals(g.gameDir));
                    Game.save(this, list);
                    refresh();
                });
    }

    void launch(Game g) {
        if (needAccess()) return;
        String why = Profile.missing(g.titleId, g.name);
        if (why != null) { error(why); return; }
        if (SaveUi.busy(g.titleId)) { error("A save backup or restore of " + g.name + " is still working: Play waits for it."); return; }
        if (PackUi.busy(g.titleId)) { error("An export or import of " + g.name + "'s pack is still working: Play waits for it."); return; }
        File gd = new File(g.gameDir);
        boolean dump = Ondevice.isDump(gd);
        Runnable pickPack = () -> { pendingGame = gd; pendingId = g.titleId; pickFolder(PICK_PACK, "Pick the pack folder (the one with pack.tsv and shaders/)"); };
        Pack p;
        try {
            p = Pack.read(new File(g.packDir));
        } catch (IOException e) {
            if (dump) packProblem(g, "The pack cannot be used: " + e.getMessage(), pickPack);
            else error(e.getMessage());
            return;
        }
        if (p.simulated) {   // a placeholder of the simulated engine (NativeAYN 0.1.0's Prepare made one): it has no game code and no shaders, so it never starts
            if (dump) packProblem(g, "This pack is a placeholder, not a pack to play.", pickPack);
            else new AlertDialog.Builder(this).setTitle("No pack for " + g.name).setMessage(Ondevice.UNAVAILABLE + "\n\nPack: " + g.packDir)
                    .setNegativeButton(Lang.t(this, "Cancel"), null).setPositiveButton("Pick pack folder", (d, w) -> pickPack.run()).show();
            return;
        }
        // The pack's code must be of the game build in the folder (else the game crashes at once), and of a build this app supports: a plain message, and Prepare
        // again from the dump, instead of a start that cannot work
        String bad = PackCheck.blocking(this, p, gd);
        if (bad != null) { packProblem(g, bad, pickPack); return; }
        // The host loads <pack>/shaders/v-<its DMSH_VK_CHAIN>/dmsh_vk.lib: without it the game runs but draws nothing. Say so, and offer to prepare the shaders
        // (a dump) or another pack folder
        String lp = Pack.libraryProblem(new File(g.packDir), g.titleId);
        if (lp != null && Pack.playableLibrary(new File(g.packDir), g.titleId) != null) {   // the older library plays while the update makes this version's (vka_shader.c lib_load)
            if (dump) Ondevice.autoUpdate(this, gd, new File(g.packDir), g.titleId);
            start(g);
            return;
        }
        if (lp != null && Pack.legacyLibrary(new File(g.packDir), g.titleId) != null) {   // made before versioned updates: never blocks Play, says so (the host warns in its log too)
            if (dump) Ondevice.autoUpdate(this, gd, new File(g.packDir), g.titleId);
            Toast.makeText(this, ShaderLibs.LEGACY_WARNING, Toast.LENGTH_LONG).show();
            start(g);
            return;
        }
        if (lp != null) {
            AlertDialog.Builder b = new AlertDialog.Builder(this).setTitle("Shaders missing")
                    .setMessage((dump ? "Prepare " + Ondevice.shadersText(new File(g.packDir), g.titleId).replaceFirst("^Updates", "updates").replaceFirst("^Makes", "makes") : "Without them the game draws nothing.")
                            + "\n\n" + lp)
                    .setNeutralButton("Pick pack folder", (d, w) -> pickPack.run()).setNegativeButton(Lang.t(this, "Cancel"), null);
            if (dump) b.setPositiveButton(Lang.t(this, "Prepare"), (d, w) -> Ondevice.prepare(this, gd, false));
            else b.setPositiveButton("Start anyway", (d, w) -> start(g));
            b.show();
            return;
        }
        start(g);
    }

    /** A pack that cannot run with this game folder: why, then Prepare again from the dump (the default), or another pack folder */
    private void packProblem(Game g, String why, Runnable pickPack) {
        File gd = new File(g.gameDir);
        boolean dump = Ondevice.isDump(gd);
        AlertDialog.Builder b = new AlertDialog.Builder(this).setTitle("The pack cannot start this game")
                .setMessage((dump ? "Prepare again fixes it from your dump, on this phone.\n\n" : "") + why + "\n\nPack: " + g.packDir)
                .setNegativeButton(Lang.t(this, "Cancel"), null).setNeutralButton("Pick pack folder", (d, w) -> pickPack.run());
        if (dump) b.setPositiveButton(Lang.t(this, "Prepare again"), (d, w) -> Ondevice.prepare(this, gd, false));
        b.show();
    }

    private void start(Game g) {
        Game.touch(this, g);   // the entry played last is the one kept if the list ever holds this game twice
        startActivity(new Intent(this, GameActivity.class).putExtra("title_id", g.titleId).putExtra("name", g.name).putExtra("game", g.gameDir).putExtra("pack", g.packDir));
    }

    /** The overlay's master switch and its items (Overlay.rows); the main screen's switch follows the master switch */
    private void overlayItems() {
        LinearLayout box = new LinearLayout(this);
        box.setOrientation(LinearLayout.VERTICAL);
        Overlay.rows(this, box, null);
        Ui.sheet(this, Lang.t(this, "Overlay"), box).setPositiveButton(Lang.t(this, "Done"), null).setOnDismissListener(d -> refreshSettings()).show();
    }

    private String resLabel() { return Prefs.windowName(Prefs.window(this), true); }

    /** Game resolution: the window size the host gives the game (DM_WINDOW), from the next start */
    private void gameRes() {
        int i = Math.max(0, java.util.Arrays.asList(Prefs.WINDOWS).indexOf(Prefs.window(this)));
        new AlertDialog.Builder(this).setTitle("Game resolution").setSingleChoiceItems(Prefs.WINDOW_NAMES, i, (d, w) -> { Prefs.window(this, Prefs.WINDOWS[w]); refreshSettings(); d.dismiss(); })
                .setNegativeButton(Lang.t(this, "Cancel"), null).show();
    }

    static String capLabel(int v) { return v < 0 ? "Default" : v == 0 ? "The game's own" : v + " fps"; }

    /** The frame rate cap the next start uses (Prefs.fpsCap, DM_FPS_TARGET); the in-game panel changes it while the game runs */
    private void fpsCap() {
        int cur = Prefs.fpsCap(this), n = Prefs.FPS_CAPS.length;
        String[] labels = new String[n + 1];
        labels[0] = capLabel(-1);
        int checked = 0;
        for (int k = 0; k < n; k++) {
            int v = Prefs.FPS_CAPS[k];
            labels[k + 1] = capLabel(v);
            if (v == cur) checked = k + 1;
        }
        new AlertDialog.Builder(this).setTitle(Lang.t(this, "Frame rate cap"))
                .setSingleChoiceItems(labels, checked, (d, w) -> { Prefs.fpsCap(this, w == 0 ? -1 : Prefs.FPS_CAPS[w - 1]); refreshSettings(); d.dismiss(); })
                .setNegativeButton(Lang.t(this, "Cancel"), null).show();
    }

    /** Frame generation's settings and the Lossless.dll found (or a manual pick when it is in none of the known places) */
    private void frameGen() {
        LinearLayout box = new LinearLayout(this);
        box.setOrientation(LinearLayout.VERTICAL);
        File dll = Lsfg.find(this);
        Lsfg.rows(this, box, Lsfg.note + (dll != null ? "" : ". Put it in Documents/Emulation/bios or Switch2, or pick it."), () -> {}, true);
        Ui.add(box, Ui.small(this, "Ported from Eden (GPL-3.0-or-later). Lossless.dll is yours; it is never part of the app."), 10);
        Ui.sheet(this, Lang.t(this, "Frame generation"), box).setPositiveButton(Lang.t(this, "Done"), null).setOnDismissListener(d -> refreshSettings())
                .setNeutralButton("Pick Lossless.dll", (d, w) -> {
                    Intent i = new Intent(Intent.ACTION_OPEN_DOCUMENT).addCategory(Intent.CATEGORY_OPENABLE).setType("*/*");
                    startActivityForResult(i, PICK_DLL);
                }).show();
    }

    private void shareLog() { shareLog(null); }

    /** The newest log of a game (titleId), or of any run (null) */
    private void shareLog(String titleId) {
        File log = LogProvider.newest(this, titleId);
        if (log == null) { error(titleId == null ? "No log yet." : "No log of this game yet."); return; }
        Uri uri = LogProvider.uri(log);
        Intent send = new Intent(Intent.ACTION_SEND).setType("text/plain").putExtra(Intent.EXTRA_STREAM, uri).putExtra(Intent.EXTRA_SUBJECT, "NativeAYN log " + log.getName());
        send.setClipData(ClipData.newRawUri(log.getName(), uri));
        send.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION);
        startActivity(Intent.createChooser(send, "Share " + log.getName()));
    }

    void error(String msg) { new AlertDialog.Builder(this).setMessage(msg).setPositiveButton("OK", null).show(); }
}
