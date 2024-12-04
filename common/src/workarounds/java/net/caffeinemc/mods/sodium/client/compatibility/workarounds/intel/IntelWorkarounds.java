package net.caffeinemc.mods.sodium.client.compatibility.workarounds.intel;

import net.caffeinemc.mods.sodium.client.compatibility.environment.OsUtils;
import net.caffeinemc.mods.sodium.client.compatibility.environment.probe.GraphicsAdapterProbe;
import net.caffeinemc.mods.sodium.client.platform.windows.WindowsFileVersion;
import net.caffeinemc.mods.sodium.client.platform.windows.api.d3dkmt.D3DKMT;
import org.jetbrains.annotations.Nullable;

public class IntelWorkarounds {
    // https://github.com/CaffeineMC/sodium/issues/899
    public static @Nullable WindowsFileVersion findIntelDriverMatchingBug899() {
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
                if (driverName.matches("ig7icd(32|64).dll")) {
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

    public static boolean isUsingIntelGen8OrOlder() {
        if (OsUtils.getOs() != OsUtils.OperatingSystem.WIN) {
            return false;
        }

        for (var adapter : GraphicsAdapterProbe.getAdapters()) {
            if (adapter instanceof D3DKMT.WDDMAdapterInfo wddmAdapterInfo) {
                @Nullable var driverName = wddmAdapterInfo.getOpenGlIcdName();

                // Intel OpenGL ICD for legacy GPUs
                if (driverName != null && driverName.matches("ig(7|75|8)icd(32|64)\\.dll")) {
                    return true;
                }
            }
        }

        return false;
    }
}
