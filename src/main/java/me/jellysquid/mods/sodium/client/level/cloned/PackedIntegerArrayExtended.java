package me.jellysquid.mods.sodium.client.level.cloned;

import me.jellysquid.mods.sodium.client.level.cloned.palette.ClonedPalette;

public interface PackedIntegerArrayExtended {
    <T> void copyUsingPalette(T[] out, ClonedPalette<T> palette);
}
