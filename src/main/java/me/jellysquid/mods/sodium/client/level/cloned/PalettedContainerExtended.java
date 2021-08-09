package me.jellysquid.mods.sodium.client.level.cloned;

import net.minecraft.util.BitStorage;
import net.minecraft.world.level.chunk.Palette;
import net.minecraft.world.level.chunk.PalettedContainer;

public interface PalettedContainerExtended<T> {
    @SuppressWarnings("unchecked")
    static <T> PalettedContainerExtended<T> cast(PalettedContainer<T> container) {
        return (PalettedContainerExtended<T>) container;
    }

    BitStorage getDataArray();

    Palette<T> getPalette();

    T getDefaultValue();

    int getBits();
}
