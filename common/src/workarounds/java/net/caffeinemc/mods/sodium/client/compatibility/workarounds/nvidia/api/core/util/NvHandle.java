package net.caffeinemc.mods.sodium.client.compatibility.workarounds.nvidia.api.core.util;

import org.jetbrains.annotations.NotNull;
import org.lwjgl.system.MemoryUtil;
import org.lwjgl.system.Struct;

import java.nio.ByteBuffer;

public class NvHandle extends Struct<NvHandle> {
    protected static final int SIZEOF = 8, ALIGNOF = 8;

    protected NvHandle(long address, ByteBuffer container) {
        super(address, container);
    }

    @Override
    protected @NotNull NvHandle create(long address, ByteBuffer container) {
        return new NvHandle(address, container);
    }

    public final long value() {
        return MemoryUtil.memGetAddress(this.address);
    }

    @Override
    public final int sizeof() {
        return SIZEOF;
    }
}
