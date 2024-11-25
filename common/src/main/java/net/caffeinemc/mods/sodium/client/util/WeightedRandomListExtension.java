package net.caffeinemc.mods.sodium.client.util;

import net.minecraft.util.RandomSource;

public interface WeightedRandomListExtension<T> {
    T sodium$getQuick(RandomSource random);
}
