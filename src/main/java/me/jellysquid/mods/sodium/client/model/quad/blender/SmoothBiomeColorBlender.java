package me.jellysquid.mods.sodium.client.model.quad.blender;

import me.jellysquid.mods.sodium.client.model.quad.ModelQuadView;
import me.jellysquid.mods.sodium.client.model.quad.properties.ModelQuadFlags;
import me.jellysquid.mods.sodium.client.model.quad.ModelQuadColorProvider;
import me.jellysquid.mods.sodium.client.util.color.ColorARGB;
import me.jellysquid.mods.sodium.client.util.color.ColorMixer;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.BlockAndTintGetter;

public class SmoothBiomeColorBlender implements BiomeColorBlender {
    private final int[] cachedRet = new int[4];

    private final BlockPos.MutableBlockPos mpos = new BlockPos.MutableBlockPos();

    @Override
    public <T> int[] getColors(BlockAndTintGetter level, BlockPos origin, ModelQuadView quad, ModelQuadColorProvider<T> colorizer, T state) {
        final int[] colors = this.cachedRet;

        boolean aligned = ModelQuadFlags.contains(quad.getFlags(), ModelQuadFlags.IS_ALIGNED);

        for (int i = 0; i < 4; i++) {
            // If the vertex is aligned to the block grid, we do not need to interpolate
            if (aligned) {
                colors[i] = this.getVertexColor(colorizer, level, state, origin, quad, i);
            } else {
                colors[i] = this.getInterpolatedVertexColor(colorizer, level, state, origin, quad, i);
            }
        }

        return colors;
    }

    private <T> int getVertexColor(ModelQuadColorProvider<T> colorizer, BlockAndTintGetter world, T state, BlockPos origin,
                                   ModelQuadView quad, int vertexIdx) {
        final int x = origin.getX() + (int) quad.getX(vertexIdx);
        final int z = origin.getZ() + (int) quad.getZ(vertexIdx);

        final int color = this.getBlockColor(colorizer, world, state, origin, x, z, quad.getTintIndex());

        return ColorARGB.toABGR(color);
    }

    private <T> int getBlockColor(ModelQuadColorProvider<T> colorizer, BlockAndTintGetter world, T state, BlockPos origin,
                                  int x, int z, int colorIdx) {
        return colorizer.getColor(state, world, this.mpos.set(x, origin.getY(), z), colorIdx);
    }

    private <T> int getInterpolatedVertexColor(ModelQuadColorProvider<T> colorizer, BlockAndTintGetter world, T state,
                                               BlockPos origin, ModelQuadView quad, int vertexIdx) {
        final float x = quad.getX(vertexIdx);
        final float z = quad.getZ(vertexIdx);

        final int intX = (int) x;
        final int intZ = (int) z;

        // Integer component of position vector
        final int originX = origin.getX() + intX;
        final int originZ = origin.getZ() + intZ;

        // Retrieve the color values for each neighbor
        final int c1 = this.getBlockColor(colorizer, world, state, origin, originX, originZ, quad.getTintIndex());
        final int c2 = this.getBlockColor(colorizer, world, state, origin, originX, originZ + 1, quad.getTintIndex());
        final int c3 = this.getBlockColor(colorizer, world, state, origin, originX + 1, originZ, quad.getTintIndex());
        final int c4 = this.getBlockColor(colorizer, world, state, origin, originX + 1, originZ + 1, quad.getTintIndex());

        final int result;

        // All the colors are the same, so the results of interpolation will be useless.
        if (c1 == c2 && c2 == c3 && c3 == c4) {
            result = c1;
        } else {
            // Fraction component of position vector
            final float fracX = x - intX;
            final float fracZ = z - intZ;

            int z1 = ColorMixer.getStartRatio(fracZ);
            int z2 = ColorMixer.getEndRatio(fracZ);

            int r1 = ColorMixer.mixARGB(c1, c2, z1, z2);
            int r2 = ColorMixer.mixARGB(c3, c4, z1, z2);

            int x1 = ColorMixer.getStartRatio(fracX);
            int x2 = ColorMixer.getEndRatio(fracX);

            result = ColorMixer.mixARGB(r1, r2, x1, x2);
        }

        return ColorARGB.toABGR(result);
    }

}
