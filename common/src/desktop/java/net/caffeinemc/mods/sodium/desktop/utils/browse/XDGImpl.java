package net.caffeinemc.mods.sodium.desktop.utils.browse;

import java.io.IOException;
import java.util.Locale;

class XDGImpl implements BrowseUrlHandler {
    public static boolean isSupported() {
        String os = System.getProperty("os.name")
                .toLowerCase(Locale.ROOT);

        return os.equals("linux");
    }

    @Override
    public void browseTo(String url) throws IOException {
        var process = Runtime.getRuntime()
                .exec(new String[] { "xdg-open", url });

        try {
            int result = process.waitFor();

            if (result != 0 /* success */) {
                throw new IOException("xdg-open exited with code: %d".formatted(result));
            }
        } catch (InterruptedException e) {
            throw new RuntimeException(e);
        }

    }
}
