package net.caffeinemc.mods.sodium.client.compatibility.workarounds.nvidia;

import net.caffeinemc.mods.sodium.client.compatibility.environment.GlContextInfo;
import net.caffeinemc.mods.sodium.client.compatibility.environment.OsUtils;
import net.caffeinemc.mods.sodium.client.compatibility.environment.OsUtils.OperatingSystem;
import net.caffeinemc.mods.sodium.client.compatibility.environment.probe.GraphicsAdapterProbe;
import net.caffeinemc.mods.sodium.client.compatibility.environment.probe.GraphicsAdapterVendor;
import net.caffeinemc.mods.sodium.client.compatibility.workarounds.Workarounds;
import net.caffeinemc.mods.sodium.client.platform.unix.Libc;
import net.caffeinemc.mods.sodium.client.platform.windows.WindowsCommandLine;
import net.caffeinemc.mods.sodium.client.platform.windows.WindowsFileVersion;
import net.caffeinemc.mods.sodium.client.platform.windows.api.d3dkmt.D3DKMT;
import org.jetbrains.annotations.Nullable;
import org.lwjgl.opengl.GL;
import org.lwjgl.opengl.GL32C;
import org.lwjgl.opengl.KHRDebug;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class NvidiaWorkarounds {
    private static final Logger LOGGER = LoggerFactory.getLogger("Sodium-NvidiaWorkarounds");

    public static boolean isNvidiaGraphicsCardPresent() {
        return GraphicsAdapterProbe.getAdapters()
                .stream()
                .anyMatch(adapter -> adapter.vendor() == GraphicsAdapterVendor.NVIDIA);
    }

    // https://github.com/CaffeineMC/sodium/issues/1486
    // The way which NVIDIA tries to detect the Minecraft process could not be circumvented until fairly recently
    // So we require that an up-to-date graphics driver is installed so that our workarounds can disable the Threaded
    // Optimizations driver hack.
    public static @Nullable WindowsFileVersion findNvidiaDriverMatchingBug1486() {
        // The Linux driver has two separate branches which have overlapping version numbers, despite also having
        // different feature sets. As a result, we can't reliably determine which Linux drivers are broken...
        if (OsUtils.getOs() != OperatingSystem.WIN) {
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

    public static void applyEnvironmentChanges() {
        // We can't know if the OpenGL context will actually be initialized using the NVIDIA ICD, but we need to
        // modify the process environment *now* otherwise the driver will initialize with bad settings. For non-NVIDIA
        // drivers, these workarounds are not likely to cause issues.
        if (!isNvidiaGraphicsCardPresent()) {
            return;
        }

        LOGGER.info("Modifying process environment to apply workarounds for the NVIDIA graphics driver...");

        try {
            if (OsUtils.getOs() == OperatingSystem.WIN) {
                applyEnvironmentChanges$Windows();
            } else if (OsUtils.getOs() == OperatingSystem.LINUX) {
                applyEnvironmentChanges$Linux();
            }
        } catch (Throwable t) {
            LOGGER.error("Failed to modify the process environment", t);
            logWarning();
        }
    }


    private static void applyEnvironmentChanges$Windows() {
        // The NVIDIA drivers rely on parsing the command line arguments to detect Minecraft. We need to
        // make sure that it detects the game so that *some* important optimizations are applied. Later,
        // we will try to enable GL_DEBUG_OUTPUT_SYNCHRONOUS so that "Threaded Optimizations" cannot
        // be enabled.
        WindowsCommandLine.setCommandLine("net.caffeinemc.sodium / net.minecraft.client.main.Main /");
    }

    private static void applyEnvironmentChanges$Linux() {
        // Unlike Windows, we can just request that it not use threaded optimizations instead.
        Libc.setEnvironmentVariable("__GL_THREADED_OPTIMIZATIONS", "0");
    }

    public static void undoEnvironmentChanges() {
        if (OsUtils.getOs() == OperatingSystem.WIN) {
            undoEnvironmentChanges$Windows();
        }
    }

    private static void undoEnvironmentChanges$Windows() {
        WindowsCommandLine.resetCommandLine();
    }

    public static void applyContextChanges(GlContextInfo context) {
        // The context may not have been initialized with the NVIDIA ICD, even if we think there is an NVIDIA
        // graphics adapter in use. Because enabling these workarounds have the potential to severely hurt performance
        // on other drivers, make sure we exit now.
        if (GraphicsAdapterVendor.fromContext(context) != GraphicsAdapterVendor.NVIDIA) {
            return;
        }

        LOGGER.info("Modifying OpenGL context to apply workarounds for the NVIDIA graphics driver...");

        if (Workarounds.isWorkaroundEnabled(Workarounds.Reference.NVIDIA_THREADED_OPTIMIZATIONS_BROKEN)) {
            if (OsUtils.getOs() == OperatingSystem.WIN) {
                applyContextChanges$Windows();
            }
        }
    }

    private static void applyContextChanges$Windows() {
        // On Windows, the NVIDIA drivers do not have any environment variable to control whether
        // "Threaded Optimizations" are enabled. But we can enable the "GL_DEBUG_OUTPUT_SYNCHRONOUS" option to
        // achieve the same effect.
        var capabilities = GL.getCapabilities();

        if (capabilities.GL_KHR_debug) {
            LOGGER.info("Enabling GL_DEBUG_OUTPUT_SYNCHRONOUS to force the NVIDIA driver to disable threaded" +
                    "command submission");
            GL32C.glEnable(KHRDebug.GL_DEBUG_OUTPUT_SYNCHRONOUS);
        } else {
            LOGGER.error("GL_KHR_debug does not appear to be supported, unable to disable threaded " +
                    "command submission!");
            logWarning();
        }
    }

    private static void logWarning() {
        LOGGER.error("READ ME!");
        LOGGER.error("READ ME! The workarounds for the NVIDIA Graphics Driver did not apply correctly!");
        LOGGER.error("READ ME! You are very likely going to run into unexplained crashes and severe performance issues.");
        LOGGER.error("READ ME! More information about what went wrong can be found above this message.");
        LOGGER.error("READ ME!");
        LOGGER.error("READ ME! Please help us understand why this problem occurred by opening a bug report on our issue tracker:");
        LOGGER.error("READ ME!   https://github.com/CaffeineMC/sodium/issues");
        LOGGER.error("READ ME!");

    }
}
