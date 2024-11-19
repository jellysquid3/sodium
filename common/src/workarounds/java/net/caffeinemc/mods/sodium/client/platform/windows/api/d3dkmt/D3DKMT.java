package net.caffeinemc.mods.sodium.client.platform.windows.api.d3dkmt;

import net.caffeinemc.mods.sodium.client.compatibility.environment.probe.GraphicsAdapterInfo;
import net.caffeinemc.mods.sodium.client.compatibility.environment.probe.GraphicsAdapterVendor;
import net.caffeinemc.mods.sodium.client.platform.windows.WindowsFileVersion;
import net.caffeinemc.mods.sodium.client.platform.windows.api.Gdi32;
import net.caffeinemc.mods.sodium.client.platform.windows.api.version.Version;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.lwjgl.system.MemoryStack;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.nio.ByteBuffer;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.List;

import static net.caffeinemc.mods.sodium.client.platform.windows.api.Gdi32.*;
import static net.caffeinemc.mods.sodium.client.platform.windows.api.d3dkmt.D3DKMTQueryAdapterInfoType.WDDM12.*;
import static org.lwjgl.system.MemoryUtil.memAddress;
import static org.lwjgl.system.MemoryUtil.memByteBuffer;

public class D3DKMT {
    private static final Logger LOGGER = LoggerFactory.getLogger("Sodium-D3DKMT");

    public static List<WDDMAdapterInfo> findGraphicsAdapters() {
        if (!Gdi32.isD3DKMTSupported()) {
            // D3DKMT was introduced with Windows Vista, but it was not possible to enumerate adapters and their
            // rendering capabilities until Windows 8.0.
            LOGGER.warn("Unable to query graphics adapters when the operating system is older than Windows 8.0.");
            return List.of();
        }

        try (MemoryStack stack = MemoryStack.stackPush()) {
            var adapters = D3DKMTEnumAdaptersStruct.calloc(stack);
            apiCheckError("D3DKMTEnumAdapters", nD3DKMTEnumAdapters(adapters.address()));

            final D3DKMTAdapterInfoStruct.Buffer adapterInfoBuffer = adapters.getAdapters();

            try {
                return queryAdapters(adapterInfoBuffer);
            } finally {
                freeAdapters(adapterInfoBuffer);
            }
        }
    }

    private static @NotNull ArrayList<WDDMAdapterInfo> queryAdapters(@NotNull D3DKMTAdapterInfoStruct.Buffer adapterInfoBuffer) {
        var results = new ArrayList<WDDMAdapterInfo>();

        for (int adapterIndex = adapterInfoBuffer.position(); adapterIndex < adapterInfoBuffer.limit(); adapterIndex++) {
            var pAdapterInfo = adapterInfoBuffer.get(adapterIndex);
            int pAdapter = pAdapterInfo.getAdapterHandle();

            var parsed = getAdapterInfo(pAdapter);

            if (parsed != null) {
                results.add(parsed);
            }
        }

        return results;
    }

    private static void freeAdapters(@NotNull D3DKMTAdapterInfoStruct.Buffer adapterInfoBuffer) {
        for (int adapterIndex = adapterInfoBuffer.position(); adapterIndex < adapterInfoBuffer.limit(); adapterIndex++) {
            var adapterInfo = adapterInfoBuffer.get(adapterIndex);
            apiCheckError("D3DKMTCloseAdapter",
                    d3dkmtCloseAdapter(adapterInfo.getAdapterHandle()));
        }
    }

    private static @Nullable D3DKMT.WDDMAdapterInfo getAdapterInfo(int adapter) {
        int adapterType = queryAdapterType(adapter);

        if (!isSupportedAdapterType(adapterType)) {
            return null;
        }

        String adapterName = queryFriendlyName(adapter);

        @Nullable String driverFileName = queryDriverFileName(adapter);
        @Nullable WindowsFileVersion driverVersion = null;

        GraphicsAdapterVendor driverVendor = GraphicsAdapterVendor.UNKNOWN;

        if (driverFileName != null) {
            driverVersion = queryDriverVersion(driverFileName);
            driverVendor = GraphicsAdapterVendor.fromIcdName(getOpenGlIcdName(driverFileName));
        }

        return new WDDMAdapterInfo(driverVendor, adapterName, adapterType, driverFileName, driverVersion);

    }

    private static boolean isSupportedAdapterType(int adapterType) {
        // Adapter does not support rendering
        if ((adapterType & 0x1) == 0) {
            return false;
        }

        // Adapter uses software rendering
        if ((adapterType & 0x4) != 0) {
            return false;
        }

        return true;
    }

    private static @Nullable String queryDriverFileName(int adapter) {
        try (MemoryStack stack = MemoryStack.stackPush()) {
            D3DKMTOpenGLInfoStruct info = D3DKMTOpenGLInfoStruct.calloc(stack);
            d3dkmtQueryAdapterInfo(adapter, KMTQAITYPE_UMOPENGLINFO, memByteBuffer(info));

            return info.getUserModeDriverFileName();
        }
    }

    private static @Nullable WindowsFileVersion queryDriverVersion(String file) {
        var version = Version.getModuleFileVersion(file);

        if (version == null) {
            return null;
        }

        var fileVersion = version.queryFixedFileInfo();

        if (fileVersion == null) {
            return null;
        }

        return WindowsFileVersion.fromFileVersion(fileVersion);
    }

    private static @NotNull String queryFriendlyName(int adapter) {
        try (MemoryStack stack = MemoryStack.stackPush()) {
            D3DKMTAdapterRegistryInfoStruct registryInfo = D3DKMTAdapterRegistryInfoStruct.calloc(stack);
            d3dkmtQueryAdapterInfo(adapter, KMTQAITYPE_ADAPTERREGISTRYINFO, memByteBuffer(registryInfo));

            String name = registryInfo.getAdapterString();

            if (name == null) {
                name = "<unknown>";
            }

            return name;
        }
    }

    private static int queryAdapterType(int adapter) {
        try (MemoryStack stack = MemoryStack.stackPush()) {
            var info = stack.callocInt(1);
            d3dkmtQueryAdapterInfo(adapter, KMTQAITYPE_ADAPTERTYPE, memByteBuffer(info));

            return info.get(0);
        }
    }

    private static void d3dkmtQueryAdapterInfo(int adapter, int type, ByteBuffer holder) {
        try (MemoryStack stack = MemoryStack.stackPush()) {
            var info = D3DKMTQueryAdapterInfoStruct.malloc(stack);
            info.setAdapterHandle(adapter);
            info.setType(type);
            info.setDataPointer(memAddress(holder));
            info.setDataLength(holder.remaining());

            apiCheckError("D3DKMTQueryAdapterInfo", nd3dKmtQueryAdapterInfo(info.address()));
        }
    }

    private static int d3dkmtCloseAdapter(int handle) {
        try (var stack = MemoryStack.stackPush()) {
            var info = stack.ints(handle);
            return nD3DKMTCloseAdapter(memAddress(info));
        }
    }

    private static void apiCheckError(String name, int error) {
        if (error != 0) {
            throw new RuntimeException("%s returned non-zero result (error=%s)".formatted(name, Integer.toHexString(error)));
        }
    }

    public record WDDMAdapterInfo(
            @NotNull GraphicsAdapterVendor vendor,
            @NotNull String name,
            int adapterType,
            @Nullable String openglIcdFilePath,
            @Nullable WindowsFileVersion openglIcdVersion
    ) implements GraphicsAdapterInfo {
        public @Nullable String getOpenGlIcdName() {
            return D3DKMT.getOpenGlIcdName(this.openglIcdFilePath);
        }

        @Override
        public String toString() {
            return "AdapterInfo{vendor=%s, description='%s', adapterType=0x%08X, openglIcdFilePath='%s', openglIcdVersion=%s}"
                    .formatted(this.vendor, this.name, this.adapterType, this.openglIcdFilePath, this.openglIcdVersion);
        }
    }

    private static String getOpenGlIcdName(@Nullable String filePath) {
        if (filePath == null) {
            return null;
        }

        var fileName = Paths.get(filePath)
                .getFileName();

        if (fileName == null) {
            return null;
        }

        return fileName.toString();
    }
}
