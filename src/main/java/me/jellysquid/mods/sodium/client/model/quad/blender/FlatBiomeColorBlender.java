package me.jellysquid.mods.sodium.client.model.quad.blender;

import me.jellysquid.mods.sodium.client.model.quad.ModelQuadView;
import me.jellysquid.mods.sodium.client.model.quad.ModelQuadColorProvider;
import me.jellysquid.mods.sodium.client.util.color.ColorARGB;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.BlockAndTintGetter;
import java.util.Arrays;

/**
 * A simple colorizer which performs no blending between adjacent blocks.
 */
public class FlatBiomeColorBlender implements BiomeColorBlender {
    private final int[] cachedRet = new int[4];

    @Override
    public <T> int[] getColors(BlockAndTintGetter level, BlockPos origin, ModelQuadView quad, ModelQuadColorProvider<T> colorizer, T state) {
        Arrays.fill(this.cachedRet, ColorARGB.toABGR(colorizer.getColor(state, level, origin, quad.getTintIndex())));

        return this.cachedRet;
    }
}
