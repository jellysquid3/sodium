package net.caffeinemc.mods.sodium.client.compatibility.checks;

import net.caffeinemc.mods.sodium.client.compatibility.environment.OsUtils;
import net.caffeinemc.mods.sodium.client.compatibility.environment.probe.GraphicsAdapterProbe;
import net.caffeinemc.mods.sodium.client.compatibility.environment.probe.GraphicsAdapterVendor;
import net.caffeinemc.mods.sodium.client.compatibility.workarounds.nvidia.NvidiaDriverVersion;
import net.caffeinemc.mods.sodium.client.platform.MessageBox;
import net.caffeinemc.mods.sodium.client.platform.windows.WindowsFileVersion;
import net.caffeinemc.mods.sodium.client.platform.windows.api.d3dkmt.D3DKMT;
import org.jetbrains.annotations.Nullable;
import org.lwjgl.Version;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Arrays;

/**
 * Performs OpenGL driver validation before the game creates an OpenGL context. This runs during the earliest possible
 * opportunity at game startup, and uses a custom hardware prober to search for problematic drivers.
 */
public class PreLaunchChecks {
    private static final Logger LOGGER = LoggerFactory.getLogger("Sodium-EarlyDriverScanner");

    // This string should be determined at compile time, so it can be checked against the runtime version.
    private static final String REQUIRED_LWJGL_VERSION = Version.VERSION_MAJOR + "." + Version.VERSION_MINOR + "." + Version.VERSION_REVISION;

    private static final String normalMessage = "You must change the LWJGL version in your launcher to continue. This is usually controlled by the settings for a profile or instance in your launcher.";

    private static final String prismMessage = "It appears you are using Prism Launcher to start the game. You can likely fix this problem by opening your instance settings and navigating to the Version section in the sidebar.";

    public static void beforeLWJGLInit() {
        if (BugChecks.ISSUE_2561) {
            if (!Version.getVersion().startsWith(REQUIRED_LWJGL_VERSION)) {
                String message = normalMessage;

                if (System.getProperty("minecraft.launcher.brand", "unknown").equalsIgnoreCase("PrismLauncher")) {
                    message = prismMessage;
                }

                showCriticalErrorAndClose("Sodium Renderer - Unsupported LWJGL",
                        ("""
                                The game failed to start because the currently active LWJGL version is not \
                                compatible.
                                
                                Installed version: ###CURRENT_VERSION###
                                Required version: ###REQUIRED_VERSION###
                                
                                """ + message)
                                .replace("###CURRENT_VERSION###", org.lwjgl.Version.getVersion())
                                .replace("###REQUIRED_VERSION###", REQUIRED_LWJGL_VERSION),
                        "https://github.com/CaffeineMC/sodium/wiki/LWJGL-Compatibility");

            }
        }
    }

    public static void onGameInit() {
        if (BugChecks.ISSUE_899) {
            var installedVersion = findIntelDriverMatchingBug899();

            if (installedVersion != null) {
                showCriticalErrorAndClose("Sodium Renderer - Unsupported Driver",
                        """
                                The game failed to start because the currently installed Intel Graphics Driver is not \
                                compatible.
                                
                                Installed version: ###CURRENT_DRIVER###
                                Required version: 10.18.10.5161 (or newer)
                                
                                You must update your graphics card driver in order to continue."""
                                .replace("###CURRENT_DRIVER###", installedVersion.toString()),
                        "https://github.com/CaffeineMC/sodium/wiki/Driver-Compatibility#windows-intel-gen7");
            }
        }

        if (BugChecks.ISSUE_1486) {
            var installedVersion = findNvidiaDriverMatchingBug1486();

            if (installedVersion != null) {
                showCriticalErrorAndClose("Sodium Renderer - Unsupported Driver",
                        """
                                The game failed to start because the currently installed NVIDIA Graphics Driver is not \
                                compatible.
                                
                                Installed version: ###CURRENT_DRIVER###
                                Required version: 536.23 (or newer)
                                
                                You must update your graphics card driver in order to continue."""
                                .replace("###CURRENT_DRIVER###", NvidiaDriverVersion.parse(installedVersion).toString()),
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

    // https://github.com/CaffeineMC/sodium/issues/899
    private static @Nullable WindowsFileVersion findIntelDriverMatchingBug899() {
        if (OsUtils.getOs() != OsUtils.OperatingSystem.WIN) {
            return null;
        }

        for (var adapter : GraphicsAdapterProbe.getAdapters()) {
            if (adapter instanceof D3DKMT.WDDMAdapterInfo wddmAdapterInfo) {
                @Nullable var driverName = wddmAdapterInfo.getOpenGlIcdName();

                if (driverName == null) {
                    continue;
                }

                var driverVersion = wddmAdapterInfo.openglIcdVersion();

                // Intel OpenGL ICD for Generation 7 GPUs
                if (driverName.matches("ig7icd(32|64)")) {
                    // https://www.intel.com/content/www/us/en/support/articles/000005654/graphics.html
                    // Anything which matches the 15.33 driver scheme (WDDM x.y.10.w) should be checked
                    // Drivers before build 5161 are assumed to have bugs with synchronization primitives
                    if (driverVersion.z() == 10 && driverVersion.w() < 5161) {
                        return driverVersion;
                    }
                }
            }
        }

        return null;
    }


    // https://github.com/CaffeineMC/sodium/issues/1486
    // The way which NVIDIA tries to detect the Minecraft process could not be circumvented until fairly recently
    // So we require that an up-to-date graphics driver is installed so that our workarounds can disable the Threaded
    // Optimizations driver hack.
    private static @Nullable WindowsFileVersion findNvidiaDriverMatchingBug1486() {
        // The Linux driver has two separate branches which have overlapping version numbers, despite also having
        // different feature sets. As a result, we can't reliably determine which Linux drivers are broken...
        if (OsUtils.getOs() != OsUtils.OperatingSystem.WIN) {
            return null;
        }

        for (var adapter : GraphicsAdapterProbe.getAdapters()) {
            if (adapter.vendor() != GraphicsAdapterVendor.NVIDIA) {
                continue;
            }

            if (adapter instanceof D3DKMT.WDDMAdapterInfo wddmAdapterInfo) {
                var driverVersion = wddmAdapterInfo.openglIcdVersion();

                if (driverVersion.z() == 15) { // Only match 5XX.XX drivers
                    // Broken in x.y.15.2647 (526.47)
                    // Fixed in x.y.15.3623 (536.23)
                    if (driverVersion.w() >= 2647 && driverVersion.w() < 3623) {
                        return driverVersion;
                    }
                }
            }
        }

        return null;
    }
}
