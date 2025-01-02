package net.caffeinemc.mods.sodium.client.util;

import java.nio.ByteOrder;

public class Int2 {
    public static long pack(int a, int b) {
        if (ByteOrder.nativeOrder() == ByteOrder.LITTLE_ENDIAN) {
            return ((a & 0xFFFFFFFFL) << 0) | ((b & 0xFFFFFFFFL) << 32);
        } else {
            return ((a & 0xFFFFFFFFL) << 32) | ((b & 0xFFFFFFFFL) << 0);
        }
    }
}
