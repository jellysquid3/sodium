package net.caffeinemc.mods.sodium.client.model.light.data;

import net.caffeinemc.mods.sodium.client.services.PlatformBlockAccess;
import net.minecraft.client.renderer.LevelRenderer;
import net.minecraft.client.renderer.LightTexture;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.level.BlockAndTintGetter;
import net.minecraft.world.level.LightLayer;
import net.minecraft.world.level.block.state.BlockState;

/**
 * The light data cache is used to make accessing the light data and occlusion properties of blocks cheaper. The data
 * for each block is stored as an integer with packed fields in order to work around the lack of value types in Java.
 *
 * This code is not very pretty, but it does perform significantly faster than the vanilla implementation and has
 * good cache locality.
 *
 * Each integer contains the following fields:
 * - BL: World block light, encoded as a 4-bit unsigned integer
 * - SL: World sky light, encoded as a 4-bit unsigned integer
 * - LU: Block luminance, encoded as a 4-bit unsigned integer
 * - AO: Ambient occlusion, floating point value in the range of 0.0..1.0 encoded as a 16-bit unsigned integer with 12-bit precision
 * - EM: Emissive test, true if block uses emissive lighting
 * - OP: Block opacity test, true if opaque
 * - FO: Full cube opacity test, true if opaque full cube
 * - FC: Full cube test, true if full cube
 *
 * You can use the various static pack/unpack methods to extract these values in a usable format.
 */
public abstract class LightDataAccess {
    private final BlockPos.MutableBlockPos pos = new BlockPos.MutableBlockPos();
    protected BlockAndTintGetter level;

    public int get(int x, int y, int z, Direction d1, Direction d2) {
        return this.get(x + d1.getStepX() + d2.getStepX(),
                y + d1.getStepY() + d2.getStepY(),
                z + d1.getStepZ() + d2.getStepZ());
    }

    public int get(int x, int y, int z, Direction dir) {
        return this.get(x + dir.getStepX(),
                y + dir.getStepY(),
                z + dir.getStepZ());
    }

    public int get(BlockPos pos, Direction dir) {
        return this.get(pos.getX(), pos.getY(), pos.getZ(), dir);
    }

    public int get(BlockPos pos) {
        return this.get(pos.getX(), pos.getY(), pos.getZ());
    }

    /**
     * Returns the light data for the block at the given position. The property fields can then be accessed using
     * the various unpack methods below.
     */
    public abstract int get(int x, int y, int z);

    protected int compute(int x, int y, int z) {
        BlockPos pos = this.pos.set(x, y, z);
        BlockAndTintGetter level = this.level;

        BlockState state = level.getBlockState(pos);

        boolean em = state.emissiveRendering(level, pos);
        boolean op = state.isViewBlocking(level, pos) && state.getLightBlock() != 0;
        boolean fo = state.isSolidRender();
        boolean fc = state.isCollisionShapeFullBlock(level, pos);

        int lu = PlatformBlockAccess.getInstance().getLightEmission(state, level, pos);

        // OPTIMIZE: Do not calculate light data if the block is full and opaque and does not emit light.
        int bl;
        int sl;
        if (fo && lu == 0) {
            bl = 0;
            sl = 0;
        } else {
            if (em) {
                bl = level.getBrightness(LightLayer.BLOCK, pos);
                sl = level.getBrightness(LightLayer.SKY, pos);
            } else {
                int light = LevelRenderer.getLightColor(level, state, pos);
                bl = LightTexture.block(light);
                sl = LightTexture.sky(light);
            }
        }

        // FIX: Do not apply AO from blocks that emit light
        float ao;
        if (lu == 0) {
            ao = state.getShadeBrightness(level, pos);
        } else {
            ao = 1.0f;
        }

        return packFC(fc) | packFO(fo) | packOP(op) | packEM(em) | packAO(ao) | packLU(lu) | packSL(sl) | packBL(bl);
    }

    public static int packBL(int blockLight) {
        return blockLight & 0xF;
    }

    public static int unpackBL(int word) {
        return word & 0xF;
    }

    public static int packSL(int skyLight) {
        return (skyLight & 0xF) << 4;
    }

    public static int unpackSL(int word) {
        return (word >>> 4) & 0xF;
    }

    public static int packLU(int luminance) {
        return (luminance & 0xF) << 8;
    }

    public static int unpackLU(int word) {
        return (word >>> 8) & 0xF;
    }

    public static int packAO(float ao) {
        int aoi = (int) (ao * 4096.0f);
        return (aoi & 0xFFFF) << 12;
    }

    public static float unpackAO(int word) {
        int aoi = (word >>> 12) & 0xFFFF;
        return aoi * (1.0f / 4096.0f);
    }

    public static int packEM(boolean emissive) {
        return (emissive ? 1 : 0) << 28;
    }

    public static boolean unpackEM(int word) {
        return ((word >>> 28) & 0b1) != 0;
    }

    public static int packOP(boolean opaque) {
        return (opaque ? 1 : 0) << 29;
    }

    public static boolean unpackOP(int word) {
        return ((word >>> 29) & 0b1) != 0;
    }

    public static int packFO(boolean opaque) {
        return (opaque ? 1 : 0) << 30;
    }

    public static boolean unpackFO(int word) {
        return ((word >>> 30) & 0b1) != 0;
    }

    public static int packFC(boolean fullCube) {
        return (fullCube ? 1 : 0) << 31;
    }

    public static boolean unpackFC(int word) {
        return ((word >>> 31) & 0b1) != 0;
    }

    /**
     * Computes the combined lightmap using block light, sky light, and luminance values.
     *
     * <p>This method's logic is equivalent to
     * {@link LevelRenderer#getLightColor(BlockAndTintGetter, BlockPos)}, but without the
     * emissive check.
     */
    public static int getLightmap(int word) {
        return LightTexture.pack(Math.max(unpackBL(word), unpackLU(word)), unpackSL(word));
    }

    /**
     * Like {@link #getLightmap(int)}, but checks {@link #unpackEM(int)} first and returns
     * the {@link LightTexture#FULL_BRIGHT fullbright lightmap} if emissive.
     *
     * <p>This method's logic is equivalent to
     * {@link LevelRenderer#getLightColor(BlockAndTintGetter, BlockPos)}.
     */
    public static int getEmissiveLightmap(int word) {
        if (unpackEM(word)) {
            return LightTexture.FULL_BRIGHT;
        } else {
            return getLightmap(word);
        }
    }

    public BlockAndTintGetter getLevel() {
        return this.level;
    }
}