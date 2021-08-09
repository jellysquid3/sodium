package me.jellysquid.mods.sodium.client.model.quad;

import net.minecraft.core.BlockPos;
import net.minecraft.world.level.BlockAndTintGetter;
import org.jetbrains.annotations.Nullable;

public interface ModelQuadColorProvider<T> {
    int getColor(T state, @Nullable BlockAndTintGetter world, @Nullable BlockPos pos, int tintIndex);
}
