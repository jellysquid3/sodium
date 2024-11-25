package net.caffeinemc.mods.sodium.client.compatibility.workarounds.nvidia;

import net.caffeinemc.mods.sodium.client.compatibility.environment.OsUtils;
import net.caffeinemc.mods.sodium.client.compatibility.environment.OsUtils.OperatingSystem;
import net.caffeinemc.mods.sodium.client.compatibility.environment.probe.GraphicsAdapterProbe;
import net.caffeinemc.mods.sodium.client.compatibility.environment.probe.GraphicsAdapterVendor;
import net.caffeinemc.mods.sodium.client.compatibility.workarounds.nvidia.api.core.NvAPI;
import net.caffeinemc.mods.sodium.client.compatibility.workarounds.nvidia.api.drs.enums.NVDRSGPUSupport;
import net.caffeinemc.mods.sodium.client.compatibility.workarounds.nvidia.api.drs.enums.NVDRSSettingLocation;
import net.caffeinemc.mods.sodium.client.compatibility.workarounds.nvidia.api.drs.enums.NVDRSSettingType;
import net.caffeinemc.mods.sodium.client.compatibility.workarounds.nvidia.api.drs.handles.NVDRSProfileHandle;
import net.caffeinemc.mods.sodium.client.compatibility.workarounds.nvidia.api.drs.handles.NVDRSSessionHandle;
import net.caffeinemc.mods.sodium.client.compatibility.workarounds.nvidia.api.drs.settings.ESettings;
import net.caffeinemc.mods.sodium.client.compatibility.workarounds.nvidia.api.drs.settings.EValues;
import net.caffeinemc.mods.sodium.client.compatibility.workarounds.nvidia.api.drs.structures.NVDRSProfileV1;
import net.caffeinemc.mods.sodium.client.compatibility.workarounds.nvidia.api.drs.structures.NVRDSApplicationV4;
import net.caffeinemc.mods.sodium.client.compatibility.workarounds.nvidia.api.drs.structures.NVRDSSettingV1;
import net.caffeinemc.mods.sodium.client.platform.unix.Libc;
import net.caffeinemc.mods.sodium.client.platform.windows.WindowsCommandLine;
import net.caffeinemc.mods.sodium.client.platform.windows.WindowsFileVersion;
import net.caffeinemc.mods.sodium.client.platform.windows.api.Kernel32;
import net.caffeinemc.mods.sodium.client.platform.windows.api.d3dkmt.D3DKMT;
import org.jetbrains.annotations.Nullable;
import org.lwjgl.system.MemoryStack;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;

import static net.caffeinemc.mods.sodium.client.compatibility.workarounds.nvidia.api.core.NvAPI.NvAPI_Initialize;
import static net.caffeinemc.mods.sodium.client.compatibility.workarounds.nvidia.api.core.NvAPI.NvAPI_Unload;
import static net.caffeinemc.mods.sodium.client.compatibility.workarounds.nvidia.api.drs.NvDRS.*;

public class NvidiaWorkarounds {
    private static final Logger LOGGER = LoggerFactory.getLogger("Sodium-NvidiaWorkarounds");

    public static boolean isUsingNvidiaGraphicsCard() {
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
        if (!isUsingNvidiaGraphicsCard()) {
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
        installProfile();
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

    public static void installProfile() {
        try (MemoryStack stack = MemoryStack.stackPush()) {
            NvAPI.checkError(NvAPI_Initialize());

            NVDRSSessionHandle hSession = NVDRSSessionHandle.allocateStack(stack);
            NvAPI.checkError(NvAPI_DRS_CreateSession(hSession.address()));
            NvAPI.checkError(NvAPI_DRS_LoadSettings(hSession.value()));

            NVDRSProfileV1 profile = NVDRSProfileV1.allocateStack(stack);
            profile.setVersion(NVDRSProfileV1.VERSION);
            profile.setGpuSupport(NVDRSGPUSupport.all());
            profile.setIsPredefined(false);
            profile.setProfileName("sodium.renderer.exe");

            NVDRSProfileHandle hProfile = NVDRSProfileHandle.allocateStack(stack);
            NvAPI.checkError(NvAPI_DRS_CreateProfile(hSession.value(), profile.address(), hProfile.address()));

            NVRDSApplicationV4 application = NVRDSApplicationV4.allocateStack(stack);
            application.setVersion(NVRDSApplicationV4.VERSION);
            application.setIsPredefined(false);
            application.setAppName("sodium.renderer.exe");
            application.setUserFriendlyName("Minecraft using Sodium Renderer");

            NvAPI.checkError(NvAPI_DRS_CreateApplication(hSession.value(), hProfile.value(), application.address()));

            NVRDSSettingV1 setting = NVRDSSettingV1.allocateStack(stack);
            setting.setVersion(NVRDSSettingV1.VERSION);
            setting.setSettingId(ESettings.OGL_THREAD_CONTROL_ID);
            setting.setSettingType(NVDRSSettingType.DWORD);
            setting.setSettingLocation(NVDRSSettingLocation.CURRENT_PROFILE);
            setting.setIsCurrentPredefined(false);
            setting.setPredefinedValid(false);
            setting.getPredefinedValue()
                    .setValue(EValues.OGLThreadControl.DISABLE);
            setting.getCurrentValue()
                    .setValue(EValues.OGLThreadControl.DISABLE);

            NvAPI.checkError(NvAPI_DRS_SetSetting(hSession.value(), hProfile.value(), setting.address()));
            NvAPI.checkError(NvAPI_DRS_SaveSettings(hSession.value()));
            NvAPI.checkError(NvAPI_DRS_DestroySession(hSession.value()));
            NvAPI.checkError(NvAPI_Unload());
        }

        var profileName = "sodium.renderer.exe";
        var profileNameUtf16 = profileName.toCharArray();
        var profileNameEnvVar = new short[profileNameUtf16.length];

        for (int charIndex = 0; charIndex < profileNameUtf16.length; charIndex++) {
            // NVIDIA thought they could keep us out with this useless XOR.
            profileNameEnvVar[charIndex] = (short) ((profileNameUtf16[charIndex] & 0xFFFF) ^ 0x5aa5);
        }

        Kernel32.setEnvironmentVariable("NV_APP_PROFILE_PROCESS_NAME", profileNameEnvVar);
    }
}
