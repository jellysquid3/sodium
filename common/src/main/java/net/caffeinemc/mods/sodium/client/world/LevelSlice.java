package net.caffeinemc.mods.sodium.client.world;

import it.unimi.dsi.fastutil.ints.Int2ReferenceMap;
import net.caffeinemc.mods.sodium.client.services.*;
import net.caffeinemc.mods.sodium.client.world.biome.LevelColorCache;
import net.caffeinemc.mods.sodium.client.world.biome.LevelBiomeSlice;
import net.caffeinemc.mods.sodium.client.world.cloned.ChunkRenderContext;
import net.caffeinemc.mods.sodium.client.world.cloned.ClonedChunkSection;
import net.caffeinemc.mods.sodium.client.world.cloned.ClonedChunkSectionCache;
import net.fabricmc.fabric.api.blockview.v2.FabricBlockView;
import net.fabricmc.fabric.api.rendering.data.v1.RenderAttachedBlockView;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.Holder;
import net.minecraft.core.SectionPos;
import net.minecraft.util.Mth;
import net.minecraft.world.level.BlockAndTintGetter;
import net.minecraft.world.level.ColorResolver;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.LightLayer;
import net.minecraft.world.level.biome.Biome;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.chunk.DataLayer;
import net.minecraft.world.level.chunk.LevelChunk;
import net.minecraft.world.level.chunk.LevelChunkSection;
import net.minecraft.world.level.levelgen.structure.BoundingBox;
import net.minecraft.world.level.lighting.LevelLightEngine;
import net.minecraft.world.level.material.FluidState;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.Arrays;
import java.util.List;
import java.util.Objects;

/**
 * <p>Takes a slice of level state (block states, biome and light data arrays) and copies the data for use in off-thread
 * operations. This allows chunk build tasks to see a consistent snapshot of chunk data at the exact moment the task was
 * created.</p>
 *
 * <p>World slices are not safe to use from multiple threads at once, but the data they contain is safe from modification
 * by the main client thread.</p>
 *
 * <p>Object pooling should be used to avoid huge allocations as this class contains many large arrays.</p>
 */
public final class LevelSlice implements BlockAndTintGetter, RenderAttachedBlockView, FabricBlockView {
    private static final LightLayer[] LIGHT_TYPES = LightLayer.values();

    // The number of blocks in a section.
    private static final int SECTION_BLOCK_COUNT = 16 * 16 * 16;

    // The radius of blocks around the origin chunk that should be copied.
    private static final int NEIGHBOR_BLOCK_RADIUS = 2;

    // The radius of chunks around the origin chunk that should be copied.
    private static final int NEIGHBOR_CHUNK_RADIUS = Mth.roundToward(NEIGHBOR_BLOCK_RADIUS, 16) >> 4;

    // The number of sections on each axis of this slice.
    private static final int SECTION_ARRAY_LENGTH = 1 + (NEIGHBOR_CHUNK_RADIUS * 2);

    // The size of the (Local Section -> Resource) arrays.
    private static final int SECTION_ARRAY_SIZE = SECTION_ARRAY_LENGTH * SECTION_ARRAY_LENGTH * SECTION_ARRAY_LENGTH;

    // The number of bits needed for each local X/Y/Z coordinate.
    private static final int LOCAL_XYZ_BITS = 4;

    // The default block state used for out-of-bounds access
    private static final BlockState EMPTY_BLOCK_STATE = Blocks.AIR.defaultBlockState();

    // The level this slice has copied data from
    private final ClientLevel level;

    // The accessor used for fetching biome data from the slice
    private final LevelBiomeSlice biomeSlice;

    // The biome blend cache
    private final LevelColorCache biomeColors;

    // (Local Section -> Block States) table.
    private final BlockState[][] blockArrays;

    // (Local Section -> Light Manager) table.
    @SuppressWarnings("MismatchedReadAndWriteOfArray")
    private final SodiumAuxiliaryLightManager[] auxLightManager;

    // (Local Section -> Light Arrays) table.
    private final @Nullable DataLayer[][] lightArrays;

    // (Local Section -> Block Entity) table.
    private final @Nullable Int2ReferenceMap<BlockEntity>[] blockEntityArrays;

    // (Local Section -> Block Entity Render Data) table.
    private final @Nullable Int2ReferenceMap<Object>[] blockEntityRenderDataArrays;

    // (Local Section -> Model Data) table.
    private final SodiumModelDataContainer[] modelMapArrays;

    // The starting point from which this slice captures blocks
    private int originBlockX, originBlockY, originBlockZ;

    // The volume that this WorldSlice contains
    private BoundingBox volume;

    public static ChunkRenderContext prepare(Level level, SectionPos pos, ClonedChunkSectionCache cache) {
        LevelChunk chunk = level.getChunk(pos.getX(), pos.getZ());
        LevelChunkSection section = chunk.getSections()[level.getSectionIndexFromSectionY(pos.getY())];

        // If the chunk section is absent or empty, simply terminate now. There will never be anything in this chunk
        // section to render, so we need to signal that a chunk render task shouldn't be created. This saves a considerable
        // amount of time in queueing instant build tasks and greatly accelerates how quickly the level can be loaded.
        if (section == null || section.hasOnlyAir()) {
            return null;
        }

        BoundingBox box = new BoundingBox(pos.minBlockX() - NEIGHBOR_BLOCK_RADIUS,
                pos.minBlockY() - NEIGHBOR_BLOCK_RADIUS,
                pos.minBlockZ() - NEIGHBOR_BLOCK_RADIUS,
                pos.maxBlockX() + NEIGHBOR_BLOCK_RADIUS,
                pos.maxBlockY() + NEIGHBOR_BLOCK_RADIUS,
                pos.maxBlockZ() + NEIGHBOR_BLOCK_RADIUS);

        // The min/max bounds of the chunks copied by this slice
        final int minChunkX = pos.getX() - NEIGHBOR_CHUNK_RADIUS;
        final int minChunkY = pos.getY() - NEIGHBOR_CHUNK_RADIUS;
        final int minChunkZ = pos.getZ() - NEIGHBOR_CHUNK_RADIUS;

        final int maxChunkX = pos.getX() + NEIGHBOR_CHUNK_RADIUS;
        final int maxChunkY = pos.getY() + NEIGHBOR_CHUNK_RADIUS;
        final int maxChunkZ = pos.getZ() + NEIGHBOR_CHUNK_RADIUS;

        ClonedChunkSection[] sections = new ClonedChunkSection[SECTION_ARRAY_SIZE];

        for (int chunkX = minChunkX; chunkX <= maxChunkX; chunkX++) {
            for (int chunkZ = minChunkZ; chunkZ <= maxChunkZ; chunkZ++) {
                for (int chunkY = minChunkY; chunkY <= maxChunkY; chunkY++) {
                    sections[getLocalSectionIndex(chunkX - minChunkX, chunkY - minChunkY, chunkZ - minChunkZ)] =
                            cache.acquire(chunkX, chunkY, chunkZ);
                }
            }
        }

        List<?> renderers = PlatformLevelRenderHooks.getInstance().retrieveChunkMeshAppenders(level, pos.origin());

        return new ChunkRenderContext(pos, sections, box, renderers);
    }

    @SuppressWarnings("unchecked")
    public LevelSlice(ClientLevel level) {
        this.level = level;

        this.blockArrays = new BlockState[SECTION_ARRAY_SIZE][SECTION_BLOCK_COUNT];
        this.lightArrays = new DataLayer[SECTION_ARRAY_SIZE][LIGHT_TYPES.length];

        this.blockEntityArrays = new Int2ReferenceMap[SECTION_ARRAY_SIZE];
        this.blockEntityRenderDataArrays = new Int2ReferenceMap[SECTION_ARRAY_SIZE];
        this.auxLightManager = new SodiumAuxiliaryLightManager[SECTION_ARRAY_SIZE];
        this.modelMapArrays = new SodiumModelDataContainer[SECTION_ARRAY_SIZE];

        this.biomeSlice = new LevelBiomeSlice();
        this.biomeColors = new LevelColorCache(this.biomeSlice, Minecraft.getInstance().options.biomeBlendRadius().get());

        for (BlockState[] blockArray : this.blockArrays) {
            Arrays.fill(blockArray, EMPTY_BLOCK_STATE);
        }
    }

    public void copyData(ChunkRenderContext context) {
        this.originBlockX = SectionPos.sectionToBlockCoord(context.getOrigin().getX() - NEIGHBOR_CHUNK_RADIUS);
        this.originBlockY = SectionPos.sectionToBlockCoord(context.getOrigin().getY() - NEIGHBOR_CHUNK_RADIUS);
        this.originBlockZ = SectionPos.sectionToBlockCoord(context.getOrigin().getZ() - NEIGHBOR_CHUNK_RADIUS);

        this.volume = context.getVolume();

        for (int x = 0; x < SECTION_ARRAY_LENGTH; x++) {
            for (int y = 0; y < SECTION_ARRAY_LENGTH; y++) {
                for (int z = 0; z < SECTION_ARRAY_LENGTH; z++) {
                    this.copySectionData(context, getLocalSectionIndex(x, y, z));
                }
            }
        }

        this.biomeSlice.update(this.level, context);
        this.biomeColors.update(context);
    }

    private void copySectionData(ChunkRenderContext context, int sectionIndex) {
        var section = context.getSections()[sectionIndex];

        Objects.requireNonNull(section, "Chunk section must be non-null");

        this.unpackBlockData(this.blockArrays[sectionIndex], context, section);

        this.lightArrays[sectionIndex][LightLayer.BLOCK.ordinal()] = section.getLightArray(LightLayer.BLOCK);
        this.lightArrays[sectionIndex][LightLayer.SKY.ordinal()] = section.getLightArray(LightLayer.SKY);

        this.blockEntityArrays[sectionIndex] = section.getBlockEntityMap();
        this.auxLightManager[sectionIndex] = section.getAuxLightManager();
        this.blockEntityRenderDataArrays[sectionIndex] = section.getBlockEntityRenderDataMap();
        this.modelMapArrays[sectionIndex] = section.getModelMap();
    }

    private void unpackBlockData(BlockState[] blockArray, ChunkRenderContext context, ClonedChunkSection section) {
        if (section.getBlockData() == null) {
            Arrays.fill(blockArray, EMPTY_BLOCK_STATE);
            return;
        }

        var container = PalettedContainerROExtension.of(section.getBlockData());

        SectionPos sectionPos = section.getPosition();

        if (sectionPos.equals(context.getOrigin())) {
            container.sodium$unpack(blockArray);
        } else {
            var bounds = context.getVolume();

            int minBlockX = Math.max(bounds.minX(), sectionPos.minBlockX());
            int maxBlockX = Math.min(bounds.maxX(), sectionPos.maxBlockX());

            int minBlockY = Math.max(bounds.minY(), sectionPos.minBlockY());
            int maxBlockY = Math.min(bounds.maxY(), sectionPos.maxBlockY());

            int minBlockZ = Math.max(bounds.minZ(), sectionPos.minBlockZ());
            int maxBlockZ = Math.min(bounds.maxZ(), sectionPos.maxBlockZ());

            container.sodium$unpack(blockArray, minBlockX & 15, minBlockY & 15, minBlockZ & 15,
                    maxBlockX & 15, maxBlockY & 15, maxBlockZ & 15);
        }
    }

    public void reset() {
        // erase any pointers to resources we no longer need
        // no point in cleaning the pre-allocated arrays (such as block state storage) since we hold the
        // only reference.
        for (int sectionIndex = 0; sectionIndex < SECTION_ARRAY_LENGTH; sectionIndex++) {
            Arrays.fill(this.lightArrays[sectionIndex], null);

            this.blockEntityArrays[sectionIndex] = null;
            this.auxLightManager[sectionIndex] = null;
            this.blockEntityRenderDataArrays[sectionIndex] = null;
        }
    }

    @Override
    public @NotNull BlockState getBlockState(BlockPos pos) {
        return this.getBlockState(pos.getX(), pos.getY(), pos.getZ());
    }

    public BlockState getBlockState(int blockX, int blockY, int blockZ) {
        if (!this.volume.isInside(blockX, blockY, blockZ)) {
            return EMPTY_BLOCK_STATE;
        }

        int relBlockX = blockX - this.originBlockX;
        int relBlockY = blockY - this.originBlockY;
        int relBlockZ = blockZ - this.originBlockZ;

        return this.blockArrays[getLocalSectionIndex(relBlockX >> 4, relBlockY >> 4, relBlockZ >> 4)]
                [getLocalBlockIndex(relBlockX & 15, relBlockY & 15, relBlockZ & 15)];
    }

    @Override
    public @NotNull FluidState getFluidState(BlockPos pos) {
        return this.getBlockState(pos)
                .getFluidState();
    }

    @Override
    public float getShade(Direction direction, boolean shaded) {
        return this.level.getShade(direction, shaded);
    }

    @Override
    public @NotNull LevelLightEngine getLightEngine() {
        // Not thread-safe to access lighting data from off-thread, even if Minecraft allows it.
        throw new UnsupportedOperationException();
    }

    @Override
    public int getBrightness(LightLayer type, BlockPos pos) {
        if (!this.volume.isInside(pos.getX(), pos.getY(), pos.getZ())) {
            return 0;
        }

        int relBlockX = pos.getX() - this.originBlockX;
        int relBlockY = pos.getY() - this.originBlockY;
        int relBlockZ = pos.getZ() - this.originBlockZ;

        var lightArray = this.lightArrays[getLocalSectionIndex(relBlockX >> 4, relBlockY >> 4, relBlockZ >> 4)][type.ordinal()];

        if (lightArray == null) {
            // If the array is null, it means the dimension for the current level does not support that light type
            return 0;
        }

        return lightArray.get(relBlockX & 15, relBlockY & 15, relBlockZ & 15);
    }

    @Override
    public int getRawBrightness(BlockPos pos, int ambientDarkness) {
        if (!this.volume.isInside(pos.getX(), pos.getY(), pos.getZ())) {
            return 0;
        }

        int relBlockX = pos.getX() - this.originBlockX;
        int relBlockY = pos.getY() - this.originBlockY;
        int relBlockZ = pos.getZ() - this.originBlockZ;

        var lightArrays = this.lightArrays[getLocalSectionIndex(relBlockX >> 4, relBlockY >> 4, relBlockZ >> 4)];

        var skyLightArray = lightArrays[LightLayer.SKY.ordinal()];
        var blockLightArray = lightArrays[LightLayer.BLOCK.ordinal()];

        int localBlockX = relBlockX & 15;
        int localBlockY = relBlockY & 15;
        int localBlockZ = relBlockZ & 15;

        int skyLight = skyLightArray == null ? 0 : skyLightArray.get(localBlockX, localBlockY, localBlockZ) - ambientDarkness;
        int blockLight = blockLightArray == null ? 0 : blockLightArray.get(localBlockX, localBlockY, localBlockZ);

        return Math.max(blockLight, skyLight);
    }

    @Override
    public BlockEntity getBlockEntity(BlockPos pos) {
        return this.getBlockEntity(pos.getX(), pos.getY(), pos.getZ());
    }

    public BlockEntity getBlockEntity(int blockX, int blockY, int blockZ) {
        if (!this.volume.isInside(blockX, blockY, blockZ)) {
            return null;
        }

        int relBlockX = blockX - this.originBlockX;
        int relBlockY = blockY - this.originBlockY;
        int relBlockZ = blockZ - this.originBlockZ;

        var blockEntities = this.blockEntityArrays[getLocalSectionIndex(relBlockX >> 4, relBlockY >> 4, relBlockZ >> 4)];

        if (blockEntities == null) {
            return null;
        }

        return blockEntities.get(getLocalBlockIndex(relBlockX & 15, relBlockY & 15, relBlockZ & 15));
    }

    @Override
    public int getBlockTint(BlockPos pos, ColorResolver resolver) {
        return this.biomeColors.getColor(resolver, pos.getX(), pos.getY(), pos.getZ());
    }

    @Override
    public int getHeight() {
        return this.level.getHeight();
    }

    @Override
    public int getMinY() {
        return this.level.getMinY();
    }

    @Override
    public @Nullable Object getBlockEntityRenderData(BlockPos pos) {
        if (!this.volume.isInside(pos.getX(), pos.getY(), pos.getZ())) {
            return null;
        }

        int relBlockX = pos.getX() - this.originBlockX;
        int relBlockY = pos.getY() - this.originBlockY;
        int relBlockZ = pos.getZ() - this.originBlockZ;

        var blockEntityRenderDataMap = this.blockEntityRenderDataArrays[getLocalSectionIndex(relBlockX >> 4, relBlockY >> 4, relBlockZ >> 4)];

        if (blockEntityRenderDataMap == null) {
            return null;
        }

        return blockEntityRenderDataMap.get(getLocalBlockIndex(relBlockX & 15, relBlockY & 15, relBlockZ & 15));
    }

    public SodiumModelData getPlatformModelData(BlockPos pos) {
        if (!this.volume.isInside(pos.getX(), pos.getY(), pos.getZ())) {
            return SodiumModelData.EMPTY;
        }

        int relBlockX = pos.getX() - this.originBlockX;
        int relBlockY = pos.getY() - this.originBlockY;
        int relBlockZ = pos.getZ() - this.originBlockZ;

        var modelMap = this.modelMapArrays[getLocalSectionIndex(relBlockX >> 4, relBlockY >> 4, relBlockZ >> 4)];

        if (modelMap.isEmpty()) {
            return SodiumModelData.EMPTY;
        }

        return modelMap.getModelData(pos);
    }

    @Override
    public boolean hasBiomes() {
        return true;
    }

    @Override
    public Holder<Biome> getBiomeFabric(BlockPos pos) {
        return this.biomeSlice.getBiome(pos.getX(), pos.getY(), pos.getZ());
    }

    public static int getLocalBlockIndex(int blockX, int blockY, int blockZ) {
        return (blockY << LOCAL_XYZ_BITS << LOCAL_XYZ_BITS) | (blockZ << LOCAL_XYZ_BITS) | blockX;
    }

    public static int getLocalSectionIndex(int sectionX, int sectionY, int sectionZ) {
        return (sectionY * SECTION_ARRAY_LENGTH * SECTION_ARRAY_LENGTH) + (sectionZ * SECTION_ARRAY_LENGTH) + sectionX;
    }

    @Override
    public @Nullable Object getBlockEntityRenderAttachment(BlockPos pos) {
        if (!this.volume.isInside(pos.getX(), pos.getY(), pos.getZ())) {
            return null;
        }

        int relBlockX = pos.getX() - this.originBlockX;
        int relBlockY = pos.getY() - this.originBlockY;
        int relBlockZ = pos.getZ() - this.originBlockZ;

        var blockEntityRenderDataMap = this.blockEntityRenderDataArrays[getLocalSectionIndex(relBlockX >> 4, relBlockY >> 4, relBlockZ >> 4)];

        if (blockEntityRenderDataMap == null) {
            return null;
        }

        return blockEntityRenderDataMap.get(getLocalBlockIndex(relBlockX & 15, relBlockY & 15, relBlockZ & 15));
    }
}
