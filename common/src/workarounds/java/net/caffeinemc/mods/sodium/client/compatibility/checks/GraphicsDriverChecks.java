package net.caffeinemc.mods.sodium.client.compatibility.checks;

import net.caffeinemc.mods.sodium.client.compatibility.environment.GlContextInfo;
import net.caffeinemc.mods.sodium.client.compatibility.environment.probe.GraphicsAdapterVendor;
import net.caffeinemc.mods.sodium.client.compatibility.workarounds.intel.IntelWorkarounds;
import net.caffeinemc.mods.sodium.client.compatibility.workarounds.nvidia.NvidiaDriverVersion;
import net.caffeinemc.mods.sodium.client.compatibility.workarounds.nvidia.NvidiaWorkarounds;
import net.caffeinemc.mods.sodium.client.platform.NativeWindowHandle;
import net.caffeinemc.mods.sodium.client.platform.PlatformHelper;

class GraphicsDriverChecks {
    static void postContextInit(NativeWindowHandle window, GlContextInfo context) {
        var vendor = GraphicsAdapterVendor.fromContext(context);

        if (vendor == GraphicsAdapterVendor.UNKNOWN) {
            return;
        }

        if (vendor == GraphicsAdapterVendor.INTEL && BugChecks.ISSUE_899) {
            var installedVersion = IntelWorkarounds.findIntelDriverMatchingBug899();

            if (installedVersion != null) {
                var installedVersionString = installedVersion.toString();

                PlatformHelper.showCriticalErrorAndClose(window,
                        "Sodium Renderer - Unsupported Driver",
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

        if (vendor == GraphicsAdapterVendor.NVIDIA && BugChecks.ISSUE_1486) {
            var installedVersion = NvidiaWorkarounds.findNvidiaDriverMatchingBug1486();

            if (installedVersion != null) {
                var installedVersionString = NvidiaDriverVersion.parse(installedVersion)
                        .toString();

                PlatformHelper.showCriticalErrorAndClose(window,
                        "Sodium Renderer - Unsupported Driver",
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
}
