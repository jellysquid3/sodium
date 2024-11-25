package net.caffeinemc.mods.sodium.client.compatibility.workarounds.nvidia.api.core.util;

import org.jetbrains.annotations.NotNull;
import org.lwjgl.system.MemoryStack;
import org.lwjgl.system.MemoryUtil;
import org.lwjgl.system.Struct;

import java.nio.ByteBuffer;

// NvAPI_ShortString
public class NvShortString extends Struct<NvShortString> {
    private static final int ARRAY_LENGTH = 64;
    private static final int SIZEOF, ALIGNOF;

    static {
        var layout = __struct(
                __array(Byte.BYTES, ARRAY_LENGTH)
        );

        SIZEOF = layout.getSize();
        ALIGNOF = layout.getAlignment();
    }

    protected NvShortString(long address, ByteBuffer container) {
        super(address, container);
    }

    @Override
    protected @NotNull NvShortString create(long address, ByteBuffer container) {
        return new NvShortString(address, container);
    }

    public static NvShortString allocateStack(MemoryStack stack) {
        return new NvShortString(stack.ncalloc(ALIGNOF, 1, SIZEOF), null);
    }

    public String value() {
        return MemoryUtil.memUTF8(this.address);
    }

    @Override
    public int sizeof() {
        return SIZEOF;
    }
}
