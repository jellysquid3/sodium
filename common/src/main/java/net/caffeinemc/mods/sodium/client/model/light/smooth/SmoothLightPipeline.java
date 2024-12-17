package net.caffeinemc.mods.sodium.client.model.light.smooth;

import net.caffeinemc.mods.sodium.api.util.NormI8;
import net.caffeinemc.mods.sodium.client.model.light.LightPipeline;
import net.caffeinemc.mods.sodium.client.model.light.data.LightDataAccess;
import net.caffeinemc.mods.sodium.client.model.light.data.QuadLightData;
import net.caffeinemc.mods.sodium.client.model.quad.ModelQuadView;
import net.caffeinemc.mods.sodium.client.model.quad.properties.ModelQuadFlags;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.util.Mth;
import org.joml.Vector3f;

/**
 * A light pipeline which produces smooth interpolated lighting and ambient occlusion for model quads. This
 * implementation makes a number of improvements over vanilla's own "smooth lighting" option. In no particular order:
 *
 * - Corner blocks are now selected from the correct set of neighbors above block faces (fixes MC-148689 and MC-12558)
 * - Shading issues caused by anisotropy are fixed by re-orientating quads to a consistent ordering (fixes MC-138211)
 * - Inset block faces are correctly shaded by their neighbors, fixing a number of problems with non-full blocks such as
 *   grass paths (fixes MC-11783 and MC-108621)
 * - Blocks next to emissive blocks are too bright (MC-260989)
 * - Synchronization issues between the main render thread's light engine and chunk build worker threads are corrected
 *   by copying light data alongside block states, fixing a number of inconsistencies in baked chunks (no open issue)
 *
 * This implementation also includes a significant number of optimizations:
 *
 * - Computed light data for a given block face is cached and re-used again when multiple quads exist for a given
 *   facing, making complex block models less expensive to render
 * - The light data cache encodes as much information as possible into integer words to improve cache locality and
 *   to eliminate the multiple array lookups that would otherwise be needed, significantly speeding up this section
 * - Block faces aligned to the block grid use a fast-path for mapping corner light values to vertices without expensive
 *   interpolation or blending, speeding up most block renders
 * - Some critical code paths have been re-written to hit the JVM's happy path, allowing it to perform auto-vectorization
 *   of the blend functions
 * - Information about a given model quad is cached to enable the light pipeline to make certain assumptions and skip
 *   unnecessary computation
 */
public class SmoothLightPipeline implements LightPipeline {
    /**
     * The cache which light data will be accessed from.
     */
    private final LightDataAccess lightCache;

    /**
     * The cached face data for each side of a block, both inset and outset.
     */
    private final AoFaceData[] cachedFaceData = new AoFaceData[6 * 2];

    /**
     * The position at which the cached face data was taken at.
     */
    private long cachedPos = Long.MIN_VALUE;

    /**
     * A temporary array for storing the intermediary results of weight data for non-aligned face blending.
     */
    private final float[] weights = new float[4];

    public SmoothLightPipeline(LightDataAccess cache) {
        this.lightCache = cache;

        for (int i = 0; i < this.cachedFaceData.length; i++) {
            this.cachedFaceData[i] = new AoFaceData();
        }
    }

    @Override
    public void calculate(ModelQuadView quad, BlockPos pos, QuadLightData out, Direction cullFace, Direction lightFace, boolean shade, boolean enhanced) {
        this.updateCachedData(pos.asLong());

        int flags = quad.getFlags();

        final AoNeighborInfo neighborInfo = AoNeighborInfo.get(lightFace);

        // If the model quad is aligned to the block's face and covers it entirely, we can take a fast path and directly
        // map the corner values onto this quad's vertices. This covers most situations during rendering and provides
        // a modest speed-up.
        // To match vanilla behavior, also treat the face as aligned if it is parallel and the block state is a full cube
        if ((flags & ModelQuadFlags.IS_ALIGNED) != 0 || ((flags & ModelQuadFlags.IS_PARALLEL) != 0 && LightDataAccess.unpackFC(this.lightCache.get(pos)))) {
            if ((flags & ModelQuadFlags.IS_PARTIAL) == 0) {
                this.applyAlignedFullFace(neighborInfo, pos, lightFace, out, shade);
            } else {
                this.applyAlignedPartialFace(neighborInfo, quad, pos, lightFace, out, shade);
            }
        } else if ((flags & ModelQuadFlags.IS_PARALLEL) != 0) {
            this.applyParallelFace(neighborInfo, quad, pos, lightFace, out, shade);
        } else if (enhanced) {
            this.applyIrregularFace(pos, quad, out, shade);
        } else {
            this.applyNonParallelFace(neighborInfo, quad, pos, lightFace, out, shade);
        }
    }

    /**
     * Quickly calculates the light data for a full grid-aligned quad. This represents the most common case (outward
     * facing quads on a full-block model) and avoids interpolation between neighbors as each corner will only ever
     * have two contributing sides.
     * Flags: IS_ALIGNED, !IS_PARTIAL
     */
    private void applyAlignedFullFace(AoNeighborInfo neighborInfo, BlockPos pos, Direction dir, QuadLightData out, boolean shade) {
        AoFaceData faceData = this.getCachedFaceData(pos, dir, true, shade);
        neighborInfo.mapCorners(faceData.lm, faceData.ao, out.lm, out.br);

        this.applyAmbientLighting(out.br, dir, shade);
    }

    /**
     * Calculates the light data for a grid-aligned quad that does not cover the entire block volume's face.
     * Flags: IS_ALIGNED, IS_PARTIAL
     */
    private void applyAlignedPartialFace(AoNeighborInfo neighborInfo, ModelQuadView quad, BlockPos pos, Direction dir, QuadLightData out, boolean shade) {
        for (int i = 0; i < 4; i++) {
            // Clamp the vertex positions to the block's boundaries to prevent weird errors in lighting
            float cx = clamp(quad.getX(i));
            float cy = clamp(quad.getY(i));
            float cz = clamp(quad.getZ(i));

            float[] weights = this.weights;
            neighborInfo.calculateCornerWeights(cx, cy, cz, weights);
            this.applyAlignedPartialFaceVertex(pos, dir, weights, i, out, true, shade);
        }

        this.applyAmbientLighting(out.br, dir, shade);
    }

    /**
     * This method is the same as {@link #applyNonParallelFace(AoNeighborInfo, ModelQuadView, BlockPos, Direction,
     * QuadLightData, boolean)} but with the check for a depth of approximately 0 removed. If the quad is parallel but not
     * aligned, all of its vertices will have the same depth and this depth must be approximately greater than 0,
     * meaning the check for 0 will always return false.
     * Flags: !IS_ALIGNED, IS_PARALLEL
     */
    private void applyParallelFace(AoNeighborInfo neighborInfo, ModelQuadView quad, BlockPos pos, Direction dir, QuadLightData out, boolean shade) {
        for (int i = 0; i < 4; i++) {
            // Clamp the vertex positions to the block's boundaries to prevent weird errors in lighting
            float cx = clamp(quad.getX(i));
            float cy = clamp(quad.getY(i));
            float cz = clamp(quad.getZ(i));

            float[] weights = this.weights;
            neighborInfo.calculateCornerWeights(cx, cy, cz, weights);

            float depth = neighborInfo.getDepth(cx, cy, cz);

            // If the quad is approximately grid-aligned (not inset) to the other side of the block, avoid unnecessary
            // computation by treating it is as aligned
            if (Mth.equal(depth, 1.0F)) {
                this.applyAlignedPartialFaceVertex(pos, dir, weights, i, out, false, shade);
            } else {
                // Blend the occlusion factor between the blocks directly beside this face and the blocks above it
                // based on how inset the face is. This fixes a few issues with blocks such as farmland and paths.
                this.applyInsetPartialFaceVertex(pos, dir, depth, 1.0f - depth, weights, i, out, shade);
            }
        }

        this.applyAmbientLighting(out.br, dir, shade);
    }

    /**
     * Flags: !IS_ALIGNED, !IS_PARALLEL
     */
    private void applyNonParallelFace(AoNeighborInfo neighborInfo, ModelQuadView quad, BlockPos pos, Direction dir, QuadLightData out, boolean shade) {
        for (int i = 0; i < 4; i++) {
            // Clamp the vertex positions to the block's boundaries to prevent weird errors in lighting
            float cx = clamp(quad.getX(i));
            float cy = clamp(quad.getY(i));
            float cz = clamp(quad.getZ(i));

            float[] weights = this.weights;
            neighborInfo.calculateCornerWeights(cx, cy, cz, weights);

            float depth = neighborInfo.getDepth(cx, cy, cz);

            // If the quad is approximately grid-aligned (not inset), avoid unnecessary computation by treating it is as aligned
            if (Mth.equal(depth, 0.0F)) {
                this.applyAlignedPartialFaceVertex(pos, dir, weights, i, out, true, shade);
            } else if (Mth.equal(depth, 1.0F)) {
                this.applyAlignedPartialFaceVertex(pos, dir, weights, i, out, false, shade);
            } else {
                // Blend the occlusion factor between the blocks directly beside this face and the blocks above it
                // based on how inset the face is. This fixes a few issues with blocks such as farmland and paths.
                this.applyInsetPartialFaceVertex(pos, dir, depth, 1.0f - depth, weights, i, out, shade);
            }
        }

        this.applyAmbientLighting(out.br, dir, shade);
    }

    private void applyAlignedPartialFaceVertex(BlockPos pos, Direction dir, float[] w, int i, QuadLightData out, boolean offset, boolean shade) {
        AoFaceData faceData = this.getCachedFaceData(pos, dir, offset, shade);

        if (!faceData.hasUnpackedLightData()) {
            faceData.unpackLightData();
        }

        float sl = faceData.getBlendedSkyLight(w);
        float bl = faceData.getBlendedBlockLight(w);
        float ao = faceData.getBlendedShade(w);

        out.br[i] = ao;
        out.lm[i] = getLightMapCoord(sl, bl);
    }

    private void applyInsetPartialFaceVertex(BlockPos pos, Direction dir, float n1d, float n2d, float[] w, int i, QuadLightData out, boolean shade) {
        AoFaceData n1 = this.getCachedFaceData(pos, dir, false, shade);

        if (!n1.hasUnpackedLightData()) {
            n1.unpackLightData();
        }

        AoFaceData n2 = this.getCachedFaceData(pos, dir, true, shade);

        if (!n2.hasUnpackedLightData()) {
            n2.unpackLightData();
        }

        // Blend between the direct neighbors and above based on the passed weights
        float ao = (n1.getBlendedShade(w) * n1d) + (n2.getBlendedShade(w) * n2d);
        float sl = (n1.getBlendedSkyLight(w) * n1d) + (n2.getBlendedSkyLight(w) * n2d);
        float bl = (n1.getBlendedBlockLight(w) * n1d) + (n2.getBlendedBlockLight(w) * n2d);

        out.br[i] = ao;
        out.lm[i] = getLightMapCoord(sl, bl);
    }

    /** used exclusively in irregular face to avoid new heap allocations each call. */
    private final Vector3f vertexNormal = new Vector3f();
    private final AoFaceData tmpFace = new AoFaceData();

    private AoFaceData gatherInsetFace(ModelQuadView quad, BlockPos blockPos, int vertexIndex, Direction lightFace, boolean shade) {
        final float w1 = AoNeighborInfo.get(lightFace).getDepth(quad.getX(vertexIndex), quad.getY(vertexIndex), quad.getZ(vertexIndex));

        if (Mth.equal(w1, 0)) {
            return getCachedFaceData(blockPos, lightFace, true, shade);
        } else if (Mth.equal(w1, 1)) {
            return getCachedFaceData(blockPos, lightFace, false, shade);
        } else {
            tmpFace.reset();
            final float w0 = 1 - w1;
            return AoFaceData.weightedMean(getCachedFaceData(blockPos, lightFace, true, shade), w0, getCachedFaceData(blockPos, lightFace, false, shade), w1, tmpFace);
        }
    }

    /**
     * Calculates the light data for a quad that does not follow any grid and is not parallel to it's light face.
     * Flags: !IS_ALIGNED, !IS_PARTIAL, !IS_FULL
     */
    private void applyIrregularFace(BlockPos blockPos, ModelQuadView quad, QuadLightData out, boolean shade) {
        final float[] w = this.weights;
        final float[] aoResult = out.br;
        final int[] lightResult = out.lm;

        for (int i = 0; i < 4; i++) {
            // TODO: Avoid this if the accurate normal is the face normal
            Vector3f normal = NormI8.unpack(quad.getAccurateNormal(i), vertexNormal);
            float ao = 0, sky = 0, block = 0, maxAo = 0;
            float maxSky = 0, maxBlock = 0;

            final float x = normal.x();

            if (!Mth.equal(0f, x)) {
                final Direction face = x > 0 ? Direction.EAST : Direction.WEST;
                final AoFaceData fd = gatherInsetFace(quad, blockPos, i, face, shade);
                AoNeighborInfo.get(face).calculateCornerWeights(quad.getX(i), quad.getY(i), quad.getZ(i), w);
                final float n = x * x;
                final float a = fd.getBlendedShade(w) * this.getAmbientBrightness(face, shade);
                final float s = fd.getBlendedSkyLight(w);
                final float b = fd.getBlendedBlockLight(w);
                ao += n * a;
                sky += n * s;
                block += n * b;
                maxAo = a;
                maxSky = s;
                maxBlock = b;
            }

            final float y = normal.y();

            if (!Mth.equal(0f, y)) {
                final Direction face = y > 0 ? Direction.UP : Direction.DOWN;
                final AoFaceData fd = gatherInsetFace(quad, blockPos, i, face, shade);
                AoNeighborInfo.get(face).calculateCornerWeights(quad.getX(i), quad.getY(i), quad.getZ(i), w);
                final float n = y * y;
                final float a = fd.getBlendedShade(w) * this.getAmbientBrightness(face, shade);
                final float s = fd.getBlendedSkyLight(w);
                final float b = fd.getBlendedBlockLight(w);
                ao += n * a;
                sky += n * s;
                block += n * b;
                maxAo = Math.max(maxAo, a);
                maxSky = Math.max(maxSky, s);
                maxBlock = Math.max(maxBlock, b);
            }

            final float z = normal.z();

            if (!Mth.equal(0f, z)) {
                final Direction face = z > 0 ? Direction.SOUTH : Direction.NORTH;
                final AoFaceData fd = gatherInsetFace(quad, blockPos, i, face, shade);
                AoNeighborInfo.get(face).calculateCornerWeights(quad.getX(i), quad.getY(i), quad.getZ(i), w);
                final float n = z * z;
                final float a = fd.getBlendedShade(w) * this.getAmbientBrightness(face, shade);
                final float s = fd.getBlendedSkyLight(w);
                final float b = fd.getBlendedBlockLight(w);
                ao += n * a;
                sky += n * s;
                block += n * b;
                maxAo = Math.max(maxAo, a);
                maxSky = Math.max(maxSky, s);
                maxBlock = Math.max(maxBlock, b);
            }

            aoResult[i] = (ao + maxAo) * 0.5f;
            lightResult[i] = (((int) ((sky + maxSky) * 0.5f) & 0xF0) << 16) | ((int) ((block + maxBlock) * 0.5f) & 0xF0);
        }
    }

    /**
     * Applies the "ambient" lighting from the dimension to a quad that is parallel with the block grid.
     * @param brightness The array of brightnesses for each quad vertex
     * @param face The facing of the quad
     * @param shade Whether the quad should receive directional lighting
     */
    private void applyAmbientLighting(final float[] brightness, Direction face, boolean shade) {
        final float multiplier = this.getAmbientBrightness(face, shade);

        for (int i = 0; i < brightness.length; i++) {
            brightness[i] *= multiplier;
        }
    }

    /**
     * Returns the "ambient" brightness a block face receives in the world.
     * @param face The block face
     * @param shade Whether the block face is receiving directional light
     */
    private float getAmbientBrightness(Direction face, boolean shade) {
        return this.lightCache.getLevel()
                .getShade(face, shade);
    }

    /**
     * Returns the cached data for a given facing or calculates it if it hasn't been cached.
     */
    private AoFaceData getCachedFaceData(BlockPos pos, Direction face, boolean offset, boolean shade) {
        AoFaceData data = this.cachedFaceData[offset ? face.ordinal() : face.ordinal() + 6];

        if (data.hasLightData()) {
            return data;
        }

        data.initLightData(this.lightCache, pos, face, offset);
        data.unpackLightData();

        return data;
    }

    private void updateCachedData(long key) {
        if (this.cachedPos != key) {
            for (AoFaceData data : this.cachedFaceData) {
                data.reset();
            }

            this.cachedPos = key;
        }
    }

    /**
     * Clamps the given float to the range [0.0, 1.0].
     */
    private static float clamp(float v) {
        if (v < 0.0f) {
            return 0.0f;
        } else if (v > 1.0f) {
            return 1.0f;
        }

        return v;
    }

    /**
     * Returns a texture coordinate on the light map texture for the given block and sky light values.
     */
    private static int getLightMapCoord(float sl, float bl) {
        return (((int) sl & 0xFF) << 16) | ((int) bl & 0xFF);
    }

}