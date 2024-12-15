package net.caffeinemc.mods.sodium.client.compatibility.checks;

import com.sun.jna.Platform;
import com.sun.jna.platform.win32.Kernel32Util;
import net.caffeinemc.mods.sodium.client.platform.MessageBox;
import net.caffeinemc.mods.sodium.client.platform.NativeWindowHandle;
import net.caffeinemc.mods.sodium.client.platform.windows.WindowsFileVersion;
import net.caffeinemc.mods.sodium.client.platform.windows.api.Kernel32;
import net.caffeinemc.mods.sodium.client.platform.windows.api.version.Version;
import org.jetbrains.annotations.Nullable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * Utility class for determining whether the current process has been injected into or otherwise modified. This should
 * generally only be accessed after OpenGL context creation, as most third-party software waits until the OpenGL ICD
 * is initialized before injecting.
 */
public class ModuleScanner {
    private static final Logger LOGGER = LoggerFactory.getLogger("Sodium-Win32ModuleChecks");

    private static final String[] RTSS_HOOKS_MODULE_NAMES = {
            "RTSSHooks64.dll",
            "RTSSHooks.dll"
    };

    private static final String[] ASUS_GPU_TWEAK_MODULE_NAMES = {
            "GTIII-OSD64-GL.dll",   "GTIII-OSD-GL.dll",
            "GTIII-OSD64-VK.dll",   "GTIII-OSD-VK.dll",
            "GTIII-OSD64.dll",      "GTIII-OSD.dll"
    };

    public static void checkModules(NativeWindowHandle window) {
        List<String> modules;

        try {
            modules = listModules();
        } catch (Throwable t) {
            LOGGER.warn("Failed to scan the currently loaded modules", t);
            return;
        }

        if (modules.isEmpty()) {
            return;
        }

        // RivaTuner hooks the wglCreateContext() function to inject itself, and injects even if the process
        // is blacklisted in the settings. The only way to stop it from injecting is to close the server process
        // entirely.
        if (BugChecks.ISSUE_2048 && isModuleLoaded(modules, RTSS_HOOKS_MODULE_NAMES)) {
            checkRTSSModules(window);
        }

        // ASUS GPU Tweak III hooks SwapBuffers() function to inject itself, and does so even if the On-Screen
        // Display (OSD) is disabled. The only way to stop it from hooking the game is to add the Java process to
        // the blacklist, or uninstall the application entirely.
        if (BugChecks.ISSUE_2637 && isModuleLoaded(modules, ASUS_GPU_TWEAK_MODULE_NAMES)) {
            checkASUSGpuTweakIII(window);
        }
    }

    private static List<String> listModules() {
        if (!Platform.isWindows()) {
            return List.of();
        }

        var pid = com.sun.jna.platform.win32.Kernel32.INSTANCE.GetCurrentProcessId();
        var modules = new ArrayList<String>();

        for (var module : Kernel32Util.getModules(pid)) {
            modules.add(module.szModule());
        }

        return Collections.unmodifiableList(modules);
    }

    private static void checkRTSSModules(NativeWindowHandle window) {
        LOGGER.warn("RivaTuner Statistics Server (RTSS) has injected into the process! Attempting to apply workarounds for compatibility...");

        @Nullable WindowsFileVersion version = null;

        try {
            version = findRTSSModuleVersion();
        } catch (Throwable t) {
            LOGGER.warn("Exception thrown while reading file version", t);
        }

        if (version == null) {
            LOGGER.warn("Could not determine version of RivaTuner Statistics Server");
        } else {
            LOGGER.info("Detected RivaTuner Statistics Server version: {}", version);
        }

        if (version == null || !isRTSSCompatible(version)) {
            MessageBox.showMessageBox(window, MessageBox.IconType.ERROR, "Sodium Renderer",
                    """
                            You appear to be using an older version of RivaTuner Statistics Server (RTSS) which is not compatible with Sodium.
                            
                            You must either update to a newer version (7.3.4 and later) or close the RivaTuner Statistics Server application.

                            For more information on how to solve this problem, click the 'Help' button.""",
                    "https://link.caffeinemc.net/help/sodium/incompatible-software/rivatuner-statistics-server/gh-2048");

            throw new RuntimeException("The installed version of RivaTuner Statistics Server (RTSS) is not compatible with Sodium, " +
                    "see here for more details: https://link.caffeinemc.net/help/sodium/incompatible-software/rivatuner-statistics-server/gh-2048");
        }
    }

    private static boolean isRTSSCompatible(WindowsFileVersion version) {
        int x = version.x();
        int y = version.y();
        int z = version.z();

        // >=7.3.4
        return x > 7 || (x == 7 && y > 3) || (x == 7 && y == 3 && z >= 4);
    }

    private static void checkASUSGpuTweakIII(NativeWindowHandle window) {
        MessageBox.showMessageBox(window, MessageBox.IconType.ERROR, "Sodium Renderer",
                """
                        ASUS GPU Tweak III is not compatible with Minecraft, and causes extreme performance issues and severe graphical corruption when used with Minecraft.
                        
                        You *must* do one of the following things to continue:
                        
                        a) Open the settings of ASUS GPU Tweak III, enable the Blacklist option, click "Browse from file...", and select the Java runtime (javaw.exe) which is used by Minecraft.
                        
                        b) Completely uninstall the ASUS GPU Tweak III application.
                        
                        For more information on how to solve this problem, click the 'Help' button.""",
                "https://link.caffeinemc.net/help/sodium/incompatible-software/asus-gtiii/gh-2637");

        throw new RuntimeException("ASUS GPU Tweak III is not compatible with Minecraft, " +
                "see here for more details: https://link.caffeinemc.net/help/sodium/incompatible-software/asus-gtiii/gh-2637");
    }

    private static @Nullable WindowsFileVersion findRTSSModuleVersion() {
        long module;

        try {
            module = Kernel32.getModuleHandleByNames(RTSS_HOOKS_MODULE_NAMES);
        } catch (Throwable t) {
            LOGGER.warn("Failed to locate module", t);
            return null;
        }

        String moduleFileName;

        try {
            moduleFileName = Kernel32.getModuleFileName(module);
        } catch (Throwable t) {
            LOGGER.warn("Failed to get path of module", t);
            return null;
        }

        var modulePath = Path.of(moduleFileName);
        var moduleDirectory = modulePath.getParent();

        LOGGER.info("Searching directory: {}", moduleDirectory);

        var executablePath = moduleDirectory.resolve("RTSS.exe");

        if (!Files.exists(executablePath)) {
            LOGGER.warn("Could not find executable: {}", executablePath);
            return null;
        }

        LOGGER.info("Parsing file: {}", executablePath);

        var version = Version.getModuleFileVersion(executablePath.toAbsolutePath().toString());

        if (version == null) {
            LOGGER.warn("Couldn't find version structure");
            return null;
        }

        var fileVersion = version.queryFixedFileInfo();

        if (fileVersion == null) {
            LOGGER.warn("Couldn't query file version");
            return null;
        }

        return WindowsFileVersion.fromFileVersion(fileVersion);
    }

    private static boolean isModuleLoaded(List<String> modules, String[] names) {
        for (var name : names) {
            for (var module : modules) {
                if (module.equalsIgnoreCase(name)) {
                    return true;
                }
            }
        }

        return false;
    }
}
