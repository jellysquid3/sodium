package net.caffeinemc.mods.sodium.client.compatibility.checks;

import net.caffeinemc.mods.sodium.client.compatibility.workarounds.intel.IntelWorkarounds;
import net.caffeinemc.mods.sodium.client.compatibility.workarounds.nvidia.NvidiaDriverVersion;
import net.caffeinemc.mods.sodium.client.compatibility.workarounds.nvidia.NvidiaWorkarounds;
import net.caffeinemc.mods.sodium.client.platform.MessageBox;
import org.lwjgl.Version;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Performs OpenGL driver validation before the game creates an OpenGL context. This runs during the earliest possible
 * opportunity at game startup, and uses a custom hardware prober to search for problematic drivers.
 */
public class PreLaunchChecks {
    private static final Logger LOGGER = LoggerFactory.getLogger("Sodium-EarlyDriverScanner");

    // These version constants are inlined at compile time.
    private static final String REQUIRED_LWJGL_VERSION =
            Version.VERSION_MAJOR + "." + Version.VERSION_MINOR + "." + Version.VERSION_REVISION;

    private static final String normalMessage = "You must change the LWJGL version in your launcher to continue. " +
            "This is usually controlled by the settings for a profile or instance in your launcher.";

    private static final String prismMessage = "It appears you are using Prism Launcher to start the game. You can " +
            "likely fix this problem by opening your instance settings and navigating to the Version section in the " +
            "sidebar.";

    public static void beforeLWJGLInit() {
        if (BugChecks.ISSUE_2561) {
            if (!isUsingKnownCompatibleLwjglVersion()) {
                String message = normalMessage;

                if (isUsingPrismLauncher()) {
                    message = prismMessage;
                }

                showCriticalErrorAndClose("Sodium Renderer - Unsupported LWJGL",
                        ("""
                                The game failed to start because the currently active LWJGL version is not \
                                compatible.
                                
                                Installed version: ###CURRENT_VERSION###
                                Required version: ###REQUIRED_VERSION###
                                
                                """ + message)
                                .replace("###CURRENT_VERSION###", Version.getVersion())
                                .replace("###REQUIRED_VERSION###", REQUIRED_LWJGL_VERSION),
                        "https://github.com/CaffeineMC/sodium/wiki/LWJGL-Compatibility");
            }
        }
    }

    public static void onGameInit() {
        if (BugChecks.ISSUE_899) {
            var installedVersion = IntelWorkarounds.findIntelDriverMatchingBug899();

            if (installedVersion != null) {
                var installedVersionString = installedVersion.toString();

                showCriticalErrorAndClose("Sodium Renderer - Unsupported Driver",
                        """
                                The game failed to start because the currently installed Intel Graphics Driver is not \
                                compatible.
                                
                                Installed version: ###CURRENT_DRIVER###
                                Required version: 10.18.10.5161 (or newer)
                                
                                You must update your graphics card driver in order to continue."""
                                .replace("###CURRENT_DRIVER###", installedVersionString),
                        "https://github.com/CaffeineMC/sodium/wiki/Driver-Compatibility#windows-intel-gen7");
            }
        }

        if (BugChecks.ISSUE_1486) {
            var installedVersion = NvidiaWorkarounds.findNvidiaDriverMatchingBug1486();

            if (installedVersion != null) {
                var installedVersionString = NvidiaDriverVersion.parse(installedVersion)
                        .toString();

                showCriticalErrorAndClose("Sodium Renderer - Unsupported Driver",
                        """
                                The game failed to start because the currently installed NVIDIA Graphics Driver is not \
                                compatible.
                                
                                Installed version: ###CURRENT_DRIVER###
                                Required version: 536.23 (or newer)
                                
                                You must update your graphics card driver in order to continue."""
                                .replace("###CURRENT_DRIVER###", installedVersionString),
                        "https://github.com/CaffeineMC/sodium/wiki/Driver-Compatibility#nvidia-gpus");

            }
        }
    }

    private static void showCriticalErrorAndClose(String title, String message, String url) {
        // Always print the information to the log file first, just in case we can't show the message box.
        LOGGER.error(""" 
                ###ERROR_DESCRIPTION###
                
                For more information, please see: ###HELP_URL###"""
                .replace("###ERROR_DESCRIPTION###", message)
                .replace("###HELP_URL###", url == null ? "" : url));

        // Try to show a graphical message box (if the platform supports it) and shut down the game.
        MessageBox.showMessageBox(null, MessageBox.IconType.ERROR, title, message, url);
        System.exit(1 /* failure code */);
    }

    private static boolean isUsingKnownCompatibleLwjglVersion() {
        return Version.getVersion()
                .startsWith(REQUIRED_LWJGL_VERSION);
    }

    private static boolean isUsingPrismLauncher() {
        return System.getProperty("minecraft.launcher.brand", "unknown")
                .equalsIgnoreCase("PrismLauncher");
    }
}
