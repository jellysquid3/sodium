package net.caffeinemc.mods.sodium.api.vertex.attributes.common;

import org.lwjgl.system.MemoryUtil;

public class PositionAttribute {
    public static void put(long ptr, float x, float y, float z) {
        MemoryUtil.memPutFloat(ptr + 0L, x);
        MemoryUtil.memPutFloat(ptr + 4L, y);
        MemoryUtil.memPutFloat(ptr + 8L, z);
    }

    public static float getX(long ptr) {
        return MemoryUtil.memGetFloat(ptr + 0L);
    }

    public static float getY(long ptr) {
        return MemoryUtil.memGetFloat(ptr + 4L);
    }

    public static float getZ(long ptr) {
        return MemoryUtil.memGetFloat(ptr + 8L);
    }
}
