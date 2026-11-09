package dev.nxnative.android;

import android.content.Context;

import java.io.File;
import java.io.PrintWriter;
import java.io.Writer;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Locale;

/**
 * A crash logger: an uncaught Java exception writes its full stack trace to the log folder as <tag>-<time>-crash.log,
 * where Share log finds it (the newest .log). The default handler then runs as before. This is how a crash on a phone
 * the developer is not plugged into gets diagnosed: the player reproduces it, shares the log, the trace is there.
 */
final class Crash {
    private Crash() {}

    private static Thread.UncaughtExceptionHandler previous;

    /** Installs once (every activity onCreate is fine): the crash file is named <tag>-<time>-crash.log; use the title ID as the tag in a game */
    static void install(Context c, String tag) {
        if (previous != null) return;
        previous = Thread.getDefaultUncaughtExceptionHandler();
        final Context app = c.getApplicationContext();
        Thread.setDefaultUncaughtExceptionHandler((t, e) -> {
            try {
                String ts = new SimpleDateFormat("yyyyMMdd-HHmmss", Locale.ROOT).format(new Date());
                File f = new File(LogProvider.dir(app), tag + "-" + ts + "-crash.log");
                try (Writer w = new java.io.FileWriter(f); PrintWriter p = new PrintWriter(w)) {
                    p.println("NativeAYN " + BuildConfig.VERSION_NAME + " (code " + BuildConfig.VERSION_CODE + ") crashed at " + ts + ", thread " + t.getName());
                    for (Throwable x = e; x != null; x = x.getCause() == x ? null : x.getCause()) { x.printStackTrace(p); p.println("--- cause ---"); }
                }
                android.util.Log.e("nxnative", "crash written to " + f.getName());
            } catch (Throwable ignored) { /* a log that cannot be written must not stop the crash itself */ }
            if (previous != null) previous.uncaughtException(t, e);
        });
    }
}
