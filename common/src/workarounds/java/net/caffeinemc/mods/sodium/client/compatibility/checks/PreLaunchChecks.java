package net.caffeinemc.mods.sodium.client.compatibility.checks;

import net.caffeinemc.mods.sodium.client.platform.PlatformHelper;
import org.lwjgl.Version;

/**
 * Performs OpenGL driver validation before the game creates an OpenGL context. This runs during the earliest possible
 * opportunity at game startup, and uses a custom hardware prober to search for problematic drivers.
 */
public class PreLaunchChecks {
    // These version constants are inlined at compile time.
    private static final String REQUIRED_LWJGL_VERSION =
            Version.VERSION_MAJOR + "." + Version.VERSION_MINOR + "." + Version.VERSION_REVISION;

    public static void checkEnvironment() {
        if (BugChecks.ISSUE_2561) {
            checkLwjglRuntimeVersion();
        }
    }

    private static void checkLwjglRuntimeVersion() {
        if (isUsingKnownCompatibleLwjglVersion()) {
            return;
        }

        String advice;

        if (isUsingPrismLauncher()) {
            advice = """
                    It appears you are using Prism Launcher to start the game. You can \
                    likely fix this problem by opening your instance settings and navigating to the Version\
                    section in the sidebar.""";
        } else {
            advice = """
                    You must change the LWJGL version in your launcher to continue. \
                    This is usually controlled by the settings for a profile or instance in your launcher.""";
        }

        String message = """
                        The game failed to start because the currently active LWJGL version is not \
                        compatible.
                        
                        Installed version: ###CURRENT_VERSION###
                        Required version: ###REQUIRED_VERSION###
                        
                        ###ADVICE_STRING###"""
                .replace("###CURRENT_VERSION###", Version.getVersion())
                .replace("###REQUIRED_VERSION###", REQUIRED_LWJGL_VERSION)
                .replace("###ADVICE_STRING###", advice);

        PlatformHelper.showCriticalErrorAndClose(null, "Sodium Renderer - Unsupported LWJGL", message,
                "https://link.caffeinemc.net/help/sodium/runtime-issue/lwjgl3/gh-2561");
    }

    private static boolean isUsingKnownCompatibleLwjglVersion() {
        return Version.getVersion()
                .startsWith(REQUIRED_LWJGL_VERSION);
    }

    private static boolean isUsingPrismLauncher() {
        return getLauncherBrand()
                .equalsIgnoreCase("PrismLauncher");
    }

    private static String getLauncherBrand() {
        return System.getProperty("minecraft.launcher.brand", "unknown");
    }
}
