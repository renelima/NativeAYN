package dev.nxnative.android;

import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.List;

/**
 * The Scene resolution's 720p (Game settings › Scene resolution; a profile with a scene_scale_palette row, Profile.hasScene): a RomFS overlay folder whose one
 * file is the game's Pack/Bootup.Nin_Ounce_NVN2.pack.zs with one ResolutionReduceScale in every level of its dynamic resolution palettes. It is made on the phone
 * from the player's own RomFS by dm/tools/gsys_dynres.py in the packer's Python (SceneService, in the :pack process), never shipped with the app. It lives in the
 * app's files folder, overlay/&lt;title ID&gt;/scene_720p/, with scene_720p.txt beside it naming what it was made from (the scale, the originals the tool checked and
 * the pack's version): GameActivity gives it to the host (DM_ROMFS_OVERLAY, dm/host/hle_fs.c) only while that still matches, so an update of the game or another
 * scale in the profile asks for it to be made again. The game's own name of the RomFS (a moved .nup) is not part of it: the same version's files are the same.
 * No Android classes: the JVM test runs it.
 */
final class SceneOverlay {
    private SceneOverlay() {}

    static File dir(File files, String titleId) { return new File(files, "overlay/" + titleId + "/scene_720p"); }
    static File stamp(File files, String titleId) { return new File(files, "overlay/" + titleId + "/scene_720p.txt"); }
    static File log(File files, String titleId) { return new File(files, "overlay/" + titleId + "/scene_720p.log"); }

    /** What scene_720p.txt holds for an overlay made with these inputs */
    static String stampText(String scale, String expect, String version) {
        return "scale\t" + scale + "\nexpect\t" + expect + "\nversion\t" + version + "\n";
    }

    /** Whether the overlay is there and was made from these inputs (its folder has files, its stamp matches) */
    static boolean current(File files, String titleId, String scale, String expect, String version) {
        String[] l = dir(files, titleId).list();
        File s = stamp(files, titleId);
        if (l == null || l.length == 0 || !s.isFile()) return false;
        try {
            return new String(Files.readAllBytes(s.toPath()), StandardCharsets.UTF_8).equals(stampText(scale, expect, version));
        } catch (IOException e) {
            return false;
        }
    }

    /** gsys_dynres.py's arguments: the RomFS as the host takes it (DM_ROMFS: a folder or &lt;file&gt;.nup:&lt;selector&gt;/romfs), the scale, the originals, the folder to write */
    static String[] argv(String romfs, String scale, String expect, File out) {
        return expect.isEmpty() ? new String[] {"--romfs", romfs, "--scale", scale, "--out", out.getPath()}
                : new String[] {"--romfs", romfs, "--scale", scale, "--expect", expect, "--out", out.getPath()};
    }

    /** Why a run failed, from its log: the tool's own last error line (gsys_dynres: …, [RED] …, an exception's last line), else the last line, else null */
    static String why(List<String> log) {
        String last = null;
        for (int i = log.size() - 1; i >= 0; i--) {
            String l = log.get(i).trim();
            if (l.isEmpty() || l.startsWith("== ")) continue;
            if (last == null) last = l;
            if (l.startsWith("gsys_dynres: ") || l.startsWith("[RED] ") || l.matches("[A-Za-z_.]*(Error|Exception): .*")) return l;
        }
        return last;
    }
}
