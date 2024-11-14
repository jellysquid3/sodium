package net.caffeinemc.mods.sodium.client.util;

public class UInt32 {
    public static long upcast(int x) {
        return Integer.toUnsignedLong(x);
    }

    public static int downcast(long x) {
        if (x < 0) {
            throw new IllegalArgumentException("x < 0");
        } else if (x >= (1L << 32)) {
            throw new IllegalArgumentException("x >= (1 << 32)");
        }

        return (int) x;
    }

    // Note: This is unsafe when (x) exceeds the maximum range of a UInt32.
    public static int uncheckedDowncast(long x) {
        return (int) x;
    }
}
