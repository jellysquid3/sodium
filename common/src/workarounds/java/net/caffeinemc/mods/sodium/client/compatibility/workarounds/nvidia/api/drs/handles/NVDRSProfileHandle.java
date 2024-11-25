package net.caffeinemc.mods.sodium.client.compatibility.workarounds.nvidia.api.drs.handles;

import net.caffeinemc.mods.sodium.client.compatibility.workarounds.nvidia.api.core.util.NvHandle;
import org.lwjgl.system.MemoryStack;

import java.nio.ByteBuffer;

// NvDRSProfileHandle
public class NVDRSProfileHandle extends NvHandle {
    protected NVDRSProfileHandle(long address, ByteBuffer container) {
        super(address, container);
    }

    public static NVDRSProfileHandle allocateStack(MemoryStack stack) {
        return new NVDRSProfileHandle(stack.ncalloc(ALIGNOF, 1, SIZEOF), null);
    }
}
