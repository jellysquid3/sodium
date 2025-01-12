package net.caffeinemc.mods.sodium.client.render.chunk.data;

import it.unimi.dsi.fastutil.objects.ObjectOpenHashSet;
import net.caffeinemc.mods.sodium.client.render.chunk.RenderSectionFlags;
import net.caffeinemc.mods.sodium.client.render.chunk.occlusion.VisibilityEncoding;
import net.caffeinemc.mods.sodium.client.render.chunk.terrain.TerrainRenderPass;
import net.caffeinemc.mods.sodium.client.render.texture.SpriteUtil;
import net.minecraft.client.renderer.chunk.VisibilitySet;
import net.minecraft.client.renderer.texture.TextureAtlasSprite;
import net.minecraft.core.Direction;
import net.minecraft.world.level.block.entity.BlockEntity;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.*;
import java.util.function.IntFunction;

/**
 * The render data for a chunk render container containing all the information about which meshes are attached, the
 * block entities contained by it, and any data used for occlusion testing.
 */
public class BuiltSectionInfo {
    public static final BuiltSectionInfo EMPTY = createEmptyData();

    public final int flags;
    public final long visibilityData;

    public final BlockEntity @Nullable[] globalBlockEntities;
    public final BlockEntity @Nullable[] culledBlockEntities;
    public final TextureAtlasSprite @Nullable[] animatedSprites;

    private BuiltSectionInfo(@NotNull Collection<TerrainRenderPass> blockRenderPasses,
                             @NotNull Collection<BlockEntity> globalBlockEntities,
                             @NotNull Collection<BlockEntity> culledBlockEntities,
                             @NotNull Collection<TextureAtlasSprite> animatedSprites,
                             @NotNull VisibilitySet occlusionData) {
        this.globalBlockEntities = toArray(globalBlockEntities, BlockEntity[]::new);
        this.culledBlockEntities = toArray(culledBlockEntities, BlockEntity[]::new);
        this.animatedSprites = toArray(animatedSprites, TextureAtlasSprite[]::new);

        int flags = 0;

        if (!blockRenderPasses.isEmpty()) {
            flags |= RenderSectionFlags.MASK_HAS_BLOCK_GEOMETRY;
        }

        if (!culledBlockEntities.isEmpty()) {
            flags |= RenderSectionFlags.MASK_HAS_BLOCK_ENTITIES;
        }

        if (!animatedSprites.isEmpty()) {
            flags |= RenderSectionFlags.MASK_HAS_ANIMATED_SPRITES;
        }

        this.flags = flags;

        this.visibilityData = VisibilityEncoding.encode(occlusionData);
    }

    public static class Builder {
        private final List<TerrainRenderPass> blockRenderPasses = new ArrayList<>();
        private final List<BlockEntity> globalBlockEntities = new ArrayList<>();
        private final List<BlockEntity> culledBlockEntities = new ArrayList<>();
        private final Set<TextureAtlasSprite> animatedSprites = new ObjectOpenHashSet<>();

        private VisibilitySet occlusionData;

        public void addRenderPass(TerrainRenderPass pass) {
            this.blockRenderPasses.add(pass);
        }

        public void setOcclusionData(VisibilitySet data) {
            this.occlusionData = data;
        }

        /**
         * Adds a sprite to this data container for tracking. If the sprite is tickable, it will be ticked every frame
         * before rendering as necessary.
         * @param sprite The sprite
         */
        public void addSprite(TextureAtlasSprite sprite) {
            if (SpriteUtil.hasAnimation(sprite)) {
                this.animatedSprites.add(sprite);
            }
        }

        /**
         * Adds a block entity to the data container.
         * @param entity The block entity itself
         * @param cull True if the block entity can be culled to this chunk render's volume, otherwise false
         */
        public void addBlockEntity(BlockEntity entity, boolean cull) {
            (cull ? this.culledBlockEntities : this.globalBlockEntities).add(entity);
        }

        public BuiltSectionInfo build() {
            return new BuiltSectionInfo(this.blockRenderPasses, this.globalBlockEntities, this.culledBlockEntities, this.animatedSprites, this.occlusionData);
        }
    }

    private static BuiltSectionInfo createEmptyData() {
        VisibilitySet occlusionData = new VisibilitySet();
        occlusionData.add(EnumSet.allOf(Direction.class));

        BuiltSectionInfo.Builder meshInfo = new BuiltSectionInfo.Builder();
        meshInfo.setOcclusionData(occlusionData);

        return meshInfo.build();
    }

    private static <T> T[] toArray(Collection<T> collection, IntFunction<T[]> allocator) {
        if (collection.isEmpty()) {
            return null;
        }

        return collection.toArray(allocator);
    }
}