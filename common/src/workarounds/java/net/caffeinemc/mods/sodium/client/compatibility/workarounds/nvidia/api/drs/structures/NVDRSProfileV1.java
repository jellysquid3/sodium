package net.caffeinemc.mods.sodium.client.compatibility.workarounds.nvidia.api.drs.structures;

import net.caffeinemc.mods.sodium.client.compatibility.workarounds.nvidia.api.core.util.NvUnicodeString;
import org.jetbrains.annotations.NotNull;
import org.lwjgl.system.MemoryStack;
import org.lwjgl.system.MemoryUtil;
import org.lwjgl.system.Struct;

import java.nio.ByteBuffer;

// NVDRS_PROFILE_V1
public class NVDRSProfileV1 extends Struct<NVDRSProfileV1> {
    private static final int SIZEOF, ALIGNOF;

    private static final int OFFSET_VERSION;
    private static final int OFFSET_PROFILE_NAME;
    private static final int OFFSET_GPU_SUPPORT;
    private static final int OFFSET_IS_PREDEFINED;
    private static final int OFFSET_NUM_OF_APPS;
    private static final int OFFSET_NUM_OF_SETTINGS;

    public static final int VERSION;

    static {
        var layout = __struct(
                __member(Integer.BYTES), // version
                __member(NvUnicodeString.SIZEOF, NvUnicodeString.ALIGNOF), // profileName
                __member(Integer.BYTES), // gpuSupport

                __member(Integer.BYTES), // isPredefined
                __member(Integer.BYTES), // numOfApps
                __member(Integer.BYTES) // numOfSettings
        );

        OFFSET_VERSION = layout.offsetof(0);
        OFFSET_PROFILE_NAME = layout.offsetof(1);
        OFFSET_GPU_SUPPORT = layout.offsetof(2);
        OFFSET_IS_PREDEFINED = layout.offsetof(3);
        OFFSET_NUM_OF_APPS = layout.offsetof(4);
        OFFSET_NUM_OF_SETTINGS = layout.offsetof(5);

        SIZEOF = layout.getSize();
        ALIGNOF = layout.getAlignment();

        VERSION = SIZEOF | (1 << 16);
    }

    protected NVDRSProfileV1(long address, ByteBuffer container) {
        super(address, container);
    }

    @Override
    protected @NotNull NVDRSProfileV1 create(long address, ByteBuffer container) {
        return new NVDRSProfileV1(address, container);
    }

    public static NVDRSProfileV1 allocateStack(MemoryStack stack) {
        return new NVDRSProfileV1(stack.ncalloc(ALIGNOF, 1, SIZEOF), null);
    }

    public void setGpuSupport(int value) {
        MemoryUtil.memPutInt(this.address + OFFSET_GPU_SUPPORT, value);

    }

    public void setVersion(int value) {
        MemoryUtil.memPutInt(this.address + OFFSET_VERSION, value);
    }

    public void setProfileName(String value) {
        NvUnicodeString.fromPointer(this.address + OFFSET_PROFILE_NAME)
                .setValue(value);
    }

    public void setIsPredefined(boolean value) {
        MemoryUtil.memPutInt(this.address + OFFSET_IS_PREDEFINED, value ? 1 : 0);
    }

    public void setNumOfApps(int value) {
        MemoryUtil.memPutInt(this.address + OFFSET_NUM_OF_APPS, value);
    }

    public void setNumOfSettings(int value) {
        MemoryUtil.memPutInt(this.address + OFFSET_NUM_OF_SETTINGS, value);
    }

    @Override
    public int sizeof() {
        return SIZEOF;
    }
}
