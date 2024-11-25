package net.caffeinemc.mods.sodium.client.compatibility.workarounds.nvidia.api.drs.handles;

import net.caffeinemc.mods.sodium.client.compatibility.workarounds.nvidia.api.core.util.NvHandle;
import org.lwjgl.system.MemoryStack;

import java.nio.ByteBuffer;

// NvDRSSessionHandle
public class NVDRSSessionHandle extends NvHandle {
    protected NVDRSSessionHandle(long address, ByteBuffer container) {
        super(address, container);
    }

    public static NVDRSSessionHandle allocateStack(MemoryStack stack) {
        return new NVDRSSessionHandle(stack.ncalloc(ALIGNOF, 1, SIZEOF), null);
    }
}
