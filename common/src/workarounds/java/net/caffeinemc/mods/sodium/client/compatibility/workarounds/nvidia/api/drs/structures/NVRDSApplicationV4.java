package net.caffeinemc.mods.sodium.client.compatibility.workarounds.nvidia.api.drs.structures;

import net.caffeinemc.mods.sodium.client.compatibility.workarounds.nvidia.api.core.util.NvUnicodeString;
import org.jetbrains.annotations.NotNull;
import org.lwjgl.system.MemoryStack;
import org.lwjgl.system.MemoryUtil;
import org.lwjgl.system.Struct;

import java.nio.ByteBuffer;

// NVDRS_APPLICATION_V4
public class NVRDSApplicationV4 extends Struct<NVRDSApplicationV4> {
    private static final int SIZEOF, ALIGNOF;

    private static final int OFFSET_VERSION;
    private static final int OFFSET_IS_PREDEFINED;
    private static final int OFFSET_APP_NAME;
    private static final int OFFSET_USER_FRIENDLY_NAME;
    private static final int OFFSET_LAUNCHER;
    private static final int OFFSET_FILE_IN_FOLDER;
    private static final int OFFSET_FLAGS;
    private static final int OFFSET_COMMAND_LINE;

    public static final int VERSION;

    public enum Flags {
        IS_METRO,
        IS_COMMAND_LINE
    }

    static {
        var layout = __struct(
                __member(Integer.BYTES), // version
                __member(Integer.BYTES), // isPredefined
                __member(NvUnicodeString.SIZEOF, NvUnicodeString.ALIGNOF), // appName
                __member(NvUnicodeString.SIZEOF, NvUnicodeString.ALIGNOF), // userFriendlyName
                __member(NvUnicodeString.SIZEOF, NvUnicodeString.ALIGNOF), // launcher
                __member(NvUnicodeString.SIZEOF, NvUnicodeString.ALIGNOF), // fileInFolder

                __member(Integer.BYTES), // flags
                __member(NvUnicodeString.SIZEOF, NvUnicodeString.ALIGNOF) // commandLine
        );

        SIZEOF = layout.getSize();
        ALIGNOF = layout.getAlignment();

        OFFSET_VERSION = layout.offsetof(0);
        OFFSET_IS_PREDEFINED = layout.offsetof(1);
        OFFSET_APP_NAME = layout.offsetof(2);
        OFFSET_USER_FRIENDLY_NAME = layout.offsetof(3);
        OFFSET_LAUNCHER = layout.offsetof(4);
        OFFSET_FILE_IN_FOLDER = layout.offsetof(5);
        OFFSET_FLAGS = layout.offsetof(6);
        OFFSET_COMMAND_LINE = layout.offsetof(7);

        VERSION = SIZEOF | (4 << 16);
    }

    protected NVRDSApplicationV4(long address, ByteBuffer container) {
        super(address, container);
    }

    @Override
    protected @NotNull NVRDSApplicationV4 create(long address, ByteBuffer container) {
        return new NVRDSApplicationV4(address, container);
    }

    public static NVRDSApplicationV4 allocateStack(MemoryStack stack) {
        return new NVRDSApplicationV4(stack.ncalloc(ALIGNOF, 1, SIZEOF), null);
    }

    public void setVersion(int value) {
        MemoryUtil.memPutInt(this.address + OFFSET_VERSION, value);
    }

    public void setIsPredefined(boolean value) {
        MemoryUtil.memPutInt(this.address + OFFSET_IS_PREDEFINED, value ? 1 : 0);
    }

    public void setAppName(String value) {
        NvUnicodeString.fromPointer(this.address + OFFSET_APP_NAME)
                .setValue(value);
    }

    public void setUserFriendlyName(String value) {
        NvUnicodeString.fromPointer(this.address + OFFSET_USER_FRIENDLY_NAME)
                .setValue(value);
    }

    public void setLauncher(String value) {
        NvUnicodeString.fromPointer(this.address + OFFSET_LAUNCHER)
                .setValue(value);
    }

    public void setFileInFolder(String value) {
        NvUnicodeString.fromPointer(this.address + OFFSET_FILE_IN_FOLDER)
                .setValue(value);
    }

    public void setFlags(Flags... values) {
        int packed = 0;
        for (Flags value : values) {
            packed |= (1 << value.ordinal());
        }
        MemoryUtil.memPutInt(this.address + OFFSET_FLAGS, packed);
    }

    @Override
    public int sizeof() {
        return SIZEOF;
    }
}
