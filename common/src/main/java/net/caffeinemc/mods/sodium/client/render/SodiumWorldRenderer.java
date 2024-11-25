package net.caffeinemc.mods.sodium.client.render;

import com.llamalad7.mixinextras.sugar.ref.LocalBooleanRef;
import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.SheetedDecalTextureGenerator;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.mojang.blaze3d.vertex.VertexMultiConsumer;
import it.unimi.dsi.fastutil.longs.Long2ObjectMap;
import net.caffeinemc.mods.sodium.client.SodiumClientMod;
import net.caffeinemc.mods.sodium.client.gl.device.CommandList;
import net.caffeinemc.mods.sodium.client.gl.device.RenderDevice;
import net.caffeinemc.mods.sodium.client.render.chunk.ChunkRenderMatrices;
import net.caffeinemc.mods.sodium.client.render.chunk.RenderSectionManager;
import net.caffeinemc.mods.sodium.client.render.chunk.lists.ChunkRenderList;
import net.caffeinemc.mods.sodium.client.render.chunk.lists.SortedRenderLists;
import net.caffeinemc.mods.sodium.client.render.chunk.map.ChunkTracker;
import net.caffeinemc.mods.sodium.client.render.chunk.map.ChunkTrackerHolder;
import net.caffeinemc.mods.sodium.client.render.chunk.terrain.DefaultTerrainRenderPasses;
import net.caffeinemc.mods.sodium.client.render.chunk.translucent_sorting.trigger.CameraMovement;
import net.caffeinemc.mods.sodium.client.render.viewport.Viewport;
import net.caffeinemc.mods.sodium.client.services.PlatformBlockAccess;
import net.caffeinemc.mods.sodium.client.util.NativeBuffer;
import net.caffeinemc.mods.sodium.client.world.LevelRendererExtension;
import net.caffeinemc.mods.sodium.mixin.core.render.world.EntityRendererAccessor;
import net.minecraft.client.Camera;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.client.renderer.LevelRenderer;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderBuffers;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.blockentity.BlockEntityRenderDispatcher;
import net.minecraft.client.renderer.entity.EntityRenderer;
import net.minecraft.client.renderer.entity.state.EntityRenderState;
import net.minecraft.client.resources.model.ModelBakery;
import net.minecraft.core.BlockPos;
import net.minecraft.core.SectionPos;
import net.minecraft.server.level.BlockDestructionProgress;
import net.minecraft.util.Mth;
import net.minecraft.util.profiling.Profiler;
import net.minecraft.util.profiling.ProfilerFiller;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import org.joml.Matrix4f;
import org.joml.Vector3d;

import java.util.Collection;
import java.util.Iterator;
import java.util.SortedSet;
import java.util.function.Consumer;

/**
 * Provides an extension to vanilla's {@link LevelRenderer}.
 */
public class SodiumWorldRenderer {
    private final Minecraft client;

    private ClientLevel level;
    private int renderDistance;

    private Vector3d lastCameraPos;
    private double lastCameraPitch, lastCameraYaw;
    private float lastFogDistance;
    private Matrix4f lastProjectionMatrix;

    private boolean useEntityCulling;

    private RenderSectionManager renderSectionManager;

    /**
     * @return The SodiumWorldRenderer based on the current dimension
     */
    public static SodiumWorldRenderer instance() {
        var instance = instanceNullable();

        if (instance == null) {
            throw new IllegalStateException("No renderer attached to active level");
        }

        return instance;
    }

    /**
     * @return The SodiumWorldRenderer based on the current dimension, or null if none is attached
     */
    public static SodiumWorldRenderer instanceNullable() {
        var level = Minecraft.getInstance().levelRenderer;

        if (level instanceof LevelRendererExtension extension) {
            return extension.sodium$getWorldRenderer();
        }

        return null;
    }

    public SodiumWorldRenderer(Minecraft client) {
        this.client = client;
    }

    public void setLevel(ClientLevel level) {
        // Check that the level is actually changing
        if (this.level == level) {
            return;
        }

        // If we have a level is already loaded, unload the renderer
        if (this.level != null) {
            this.unloadLevel();
        }

        // If we're loading a new level, load the renderer
        if (level != null) {
            this.loadLevel(level);
        }
    }

    private void loadLevel(ClientLevel level) {
        this.level = level;

        try (CommandList commandList = RenderDevice.INSTANCE.createCommandList()) {
            this.initRenderer(commandList);
        }
    }

    private void unloadLevel() {
        if (this.renderSectionManager != null) {
            this.renderSectionManager.destroy();
            this.renderSectionManager = null;
        }

        this.level = null;
    }

    /**
     * @return The number of chunk renders which are visible in the current camera's frustum
     */
    public int getVisibleChunkCount() {
        return this.renderSectionManager.getVisibleChunkCount();
    }

    /**
     * Notifies the chunk renderer that the graph scene has changed and should be re-computed.
     */
    public void scheduleTerrainUpdate() {
        // BUG: seems to be called before init
        if (this.renderSectionManager != null) {
            this.renderSectionManager.markGraphDirty();
        }
    }

    /**
     * @return True if no chunks are pending rebuilds
     */
    public boolean isTerrainRenderComplete() {
        return this.renderSectionManager.getBuilder().isBuildQueueEmpty();
    }

    /**
     * Called prior to any chunk rendering in order to update necessary state.
     */
    public void setupTerrain(Camera camera,
                             Viewport viewport,
                             boolean spectator,
                             boolean updateChunksImmediately) {
        NativeBuffer.reclaim(false);

        this.processChunkEvents();

        this.useEntityCulling = SodiumClientMod.options().performance.useEntityCulling;

        if (this.client.options.getEffectiveRenderDistance() != this.renderDistance) {
            this.reload();
        }

        ProfilerFiller profiler = Profiler.get();
        profiler.push("camera_setup");

        LocalPlayer player = this.client.player;

        if (player == null) {
            throw new IllegalStateException("Client instance has no active player entity");
        }

        Vec3 posRaw = camera.getPosition();
        Vector3d pos = new Vector3d(posRaw.x(), posRaw.y(), posRaw.z());
        Matrix4f projectionMatrix = new Matrix4f(RenderSystem.getProjectionMatrix());
        float pitch = camera.getXRot();
        float yaw = camera.getYRot();
        float fogDistance = RenderSystem.getShaderFog().end();

        if (this.lastCameraPos == null) {
            this.lastCameraPos = new Vector3d(pos);
        }
        if (this.lastProjectionMatrix == null) {
            this.lastProjectionMatrix = new Matrix4f(projectionMatrix);
        }
        boolean cameraLocationChanged = !pos.equals(this.lastCameraPos);
        boolean cameraAngleChanged = pitch != this.lastCameraPitch || yaw != this.lastCameraYaw || fogDistance != this.lastFogDistance;
        boolean cameraProjectionChanged = !projectionMatrix.equals(this.lastProjectionMatrix);

        this.lastProjectionMatrix = projectionMatrix;

        this.lastCameraPitch = pitch;
        this.lastCameraYaw = yaw;

        if (cameraLocationChanged || cameraAngleChanged || cameraProjectionChanged) {
            this.renderSectionManager.markFrustumDirty();
        }

        this.lastFogDistance = fogDistance;

        this.renderSectionManager.updateCameraState(pos, camera);

        if (cameraLocationChanged) {
            profiler.popPush("translucent_triggering");

            this.renderSectionManager.processGFNIMovement(new CameraMovement(this.lastCameraPos, pos));
            this.lastCameraPos = new Vector3d(pos);
        }

        int maxChunkUpdates = updateChunksImmediately ? this.renderDistance : 1;

        for (int i = 0; i < maxChunkUpdates; i++) {
            if (this.renderSectionManager.needsAnyUpdate()) {
                profiler.popPush("chunk_render_lists");

                this.renderSectionManager.updateRenderLists(camera, viewport, spectator);
            }

            profiler.popPush("chunk_update");

            this.renderSectionManager.cleanupAndFlip();
            this.renderSectionManager.updateChunks(updateChunksImmediately);

            profiler.popPush("chunk_upload");

            this.renderSectionManager.uploadChunks();

            if (!this.renderSectionManager.needsAnyUpdate()) {
                break;
            }
        }

        profiler.popPush("chunk_render_tick");

        this.renderSectionManager.tickVisibleRenders();

        profiler.pop();

        Entity.setViewScale(Mth.clamp((double) this.client.options.getEffectiveRenderDistance() / 8.0D, 1.0D, 2.5D) * this.client.options.entityDistanceScaling().get());
    }

    private void processChunkEvents() {
        var tracker = ChunkTrackerHolder.get(this.level);
        tracker.forEachEvent(this.renderSectionManager::onChunkAdded, this.renderSectionManager::onChunkRemoved);
    }

    /**
     * Performs a render pass for the given {@link RenderType} and draws all visible chunks for it.
     */
    public void drawChunkLayer(RenderType renderLayer, ChunkRenderMatrices matrices, double x, double y, double z) {
        if (renderLayer == RenderType.solid()) {
            this.renderSectionManager.renderLayer(matrices, DefaultTerrainRenderPasses.SOLID, x, y, z);
            this.renderSectionManager.renderLayer(matrices, DefaultTerrainRenderPasses.CUTOUT, x, y, z);
        } else if (renderLayer == RenderType.translucent()) {
            this.renderSectionManager.renderLayer(matrices, DefaultTerrainRenderPasses.TRANSLUCENT, x, y, z);
        }
    }

    public void reload() {
        if (this.level == null) {
            return;
        }

        try (CommandList commandList = RenderDevice.INSTANCE.createCommandList()) {
            this.initRenderer(commandList);
        }
    }

    private void initRenderer(CommandList commandList) {
        if (this.renderSectionManager != null) {
            this.renderSectionManager.destroy();
            this.renderSectionManager = null;
        }

        this.renderDistance = this.client.options.getEffectiveRenderDistance();

        this.renderSectionManager = new RenderSectionManager(this.level, this.renderDistance, commandList);

        var tracker = ChunkTrackerHolder.get(this.level);
        ChunkTracker.forEachChunk(tracker.getReadyChunks(), this.renderSectionManager::onChunkAdded);
    }

    public void renderBlockEntities(PoseStack matrices,
                                    RenderBuffers bufferBuilders,
                                    Long2ObjectMap<SortedSet<BlockDestructionProgress>> blockBreakingProgressions,
                                    Camera camera,
                                    float tickDelta, LocalBooleanRef isGlowing) {
        MultiBufferSource.BufferSource immediate = bufferBuilders.bufferSource();

        Vec3 cameraPos = camera.getPosition();
        double x = cameraPos.x();
        double y = cameraPos.y();
        double z = cameraPos.z();

        LocalPlayer player = this.client.player;

        if (player == null) {
            throw new IllegalStateException("Client instance has no active player entity");
        }

        BlockEntityRenderDispatcher blockEntityRenderer = Minecraft.getInstance().getBlockEntityRenderDispatcher();

        this.renderBlockEntities(matrices, bufferBuilders, blockBreakingProgressions, tickDelta, immediate, x, y, z, blockEntityRenderer, player, isGlowing);
        this.renderGlobalBlockEntities(matrices, bufferBuilders, blockBreakingProgressions, tickDelta, immediate, x, y, z, blockEntityRenderer, player, isGlowing);
    }

    private void renderBlockEntities(PoseStack matrices,
                                     RenderBuffers bufferBuilders,
                                     Long2ObjectMap<SortedSet<BlockDestructionProgress>> blockBreakingProgressions,
                                     float tickDelta,
                                     MultiBufferSource.BufferSource immediate,
                                     double x,
                                     double y,
                                     double z,
                                     BlockEntityRenderDispatcher blockEntityRenderer,
                                     LocalPlayer player,
                                     LocalBooleanRef isGlowing) {
        SortedRenderLists renderLists = this.renderSectionManager.getRenderLists();
        Iterator<ChunkRenderList> renderListIterator = renderLists.iterator();

        while (renderListIterator.hasNext()) {
            var renderList = renderListIterator.next();

            var renderRegion = renderList.getRegion();
            var renderSectionIterator = renderList.sectionsWithEntitiesIterator();

            if (renderSectionIterator == null) {
                continue;
            }

            while (renderSectionIterator.hasNext()) {
                var renderSectionId = renderSectionIterator.nextByteAsInt();

                var blockEntities = renderRegion.getCulledBlockEntities(renderSectionId);

                if (blockEntities == null) {
                    continue;
                }

                for (BlockEntity blockEntity : blockEntities) {
                    renderBlockEntity(matrices, bufferBuilders, blockBreakingProgressions, tickDelta, immediate, x, y, z, blockEntityRenderer, blockEntity, player, isGlowing);
                }
            }
        }
    }

    private void renderGlobalBlockEntities(PoseStack matrices,
                                           RenderBuffers bufferBuilders,
                                           Long2ObjectMap<SortedSet<BlockDestructionProgress>> blockBreakingProgressions,
                                           float tickDelta,
                                           MultiBufferSource.BufferSource immediate,
                                           double x,
                                           double y,
                                           double z,
                                           BlockEntityRenderDispatcher blockEntityRenderer,
                                           LocalPlayer player,
                                           LocalBooleanRef isGlowing) {
        for (var renderSection : this.renderSectionManager.getSectionsWithGlobalEntities()) {
            var blockEntities = renderSection.getRegion().getGlobalBlockEntities(renderSection.getSectionIndex());

            if (blockEntities == null) {
                continue;
            }

            for (var blockEntity : blockEntities) {
                renderBlockEntity(matrices, bufferBuilders, blockBreakingProgressions, tickDelta, immediate, x, y, z, blockEntityRenderer, blockEntity, player, isGlowing);
            }
        }
    }

    private static void renderBlockEntity(PoseStack matrices,
                                          RenderBuffers bufferBuilders,
                                          Long2ObjectMap<SortedSet<BlockDestructionProgress>> blockBreakingProgressions,
                                          float tickDelta,
                                          MultiBufferSource.BufferSource immediate,
                                          double x,
                                          double y,
                                          double z,
                                          BlockEntityRenderDispatcher dispatcher,
                                          BlockEntity entity,
                                          LocalPlayer player,
                                          LocalBooleanRef isGlowing) {
        BlockPos pos = entity.getBlockPos();

        matrices.pushPose();
        matrices.translate((double) pos.getX() - x, (double) pos.getY() - y, (double) pos.getZ() - z);

        MultiBufferSource consumer = immediate;
        SortedSet<BlockDestructionProgress> breakingInfo = blockBreakingProgressions.get(pos.asLong());

        if (breakingInfo != null && !breakingInfo.isEmpty()) {
            int stage = breakingInfo.last().getProgress();

            if (stage >= 0) {
                var bufferBuilder = bufferBuilders.crumblingBufferSource()
                        .getBuffer(ModelBakery.DESTROY_TYPES.get(stage));

                PoseStack.Pose entry = matrices.last();
                VertexConsumer transformer = new SheetedDecalTextureGenerator(bufferBuilder,
                        entry, 1.0f);

                consumer = (layer) -> layer.affectsCrumbling() ? VertexMultiConsumer.create(transformer, immediate.getBuffer(layer)) : immediate.getBuffer(layer);
            }
        }

        dispatcher.render(entity, tickDelta, matrices, consumer);

        if (isGlowing != null) {
            if (PlatformBlockAccess.getInstance().shouldBlockEntityGlow(entity, player)) {
                isGlowing.set(true);
            }
        }

        matrices.popPose();
    }

    public void iterateVisibleBlockEntities(Consumer<BlockEntity> blockEntityConsumer) {
        SortedRenderLists renderLists = this.renderSectionManager.getRenderLists();
        Iterator<ChunkRenderList> renderListIterator = renderLists.iterator();

        while (renderListIterator.hasNext()) {
            var renderList = renderListIterator.next();

            var renderRegion = renderList.getRegion();
            var renderSectionIterator = renderList.sectionsWithEntitiesIterator();

            if (renderSectionIterator == null) {
                continue;
            }

            while (renderSectionIterator.hasNext()) {
                var renderSectionId = renderSectionIterator.nextByteAsInt();
                var blockEntities = renderRegion.getCulledBlockEntities(renderSectionId);

                if (blockEntities == null) {
                    continue;
                }

                for (BlockEntity blockEntity : blockEntities) {
                    blockEntityConsumer.accept(blockEntity);
                }
            }
        }

        for (var renderSection : this.renderSectionManager.getSectionsWithGlobalEntities()) {
            var blockEntities = renderSection.getRegion().getGlobalBlockEntities(renderSection.getSectionIndex());

            if (blockEntities == null) {
                continue;
            }

            for (BlockEntity blockEntity : blockEntities) {
                blockEntityConsumer.accept(blockEntity);
            }
        }
    }

    // the volume of a section multiplied by the number of sections to be checked at most
    private static final double MAX_ENTITY_CHECK_VOLUME = 16 * 16 * 16 * 15;

    /**
     * Returns whether or not the entity intersects with any visible chunks in the graph.
     * @return True if the entity is visible, otherwise false
     */
    public <T extends Entity, S extends EntityRenderState> boolean isEntityVisible(EntityRenderer<T, S> renderer, T entity) {
        if (!this.useEntityCulling) {
            return true;
        }

        // Ensure entities with outlines or nametags are always visible
        if (this.client.shouldEntityAppearGlowing(entity) || entity.shouldShowName()) {
            return true;
        }

        AABB bb = ((EntityRendererAccessor) renderer).getCullingBox(entity);

        // bail on very large entities to avoid checking many sections
        double entityVolume = (bb.maxX - bb.minX) * (bb.maxY - bb.minY) * (bb.maxZ - bb.minZ);
        if (entityVolume > MAX_ENTITY_CHECK_VOLUME) {
            // TODO: do a frustum check instead, even large entities aren't visible if they're outside the frustum
            return true;
        }

        return this.isBoxVisible(bb.minX, bb.minY, bb.minZ, bb.maxX, bb.maxY, bb.maxZ);
    }

    public boolean isBoxVisible(double x1, double y1, double z1, double x2, double y2, double z2) {
        // Boxes outside the valid level height will never map to a rendered chunk
        // Always render these boxes, or they'll be culled incorrectly!
        if (y2 < this.level.getMinY() + 0.5D || y1 > this.level.getMaxY() - 0.5D) {
            return true;
        }

        int minX = SectionPos.posToSectionCoord(x1 - 0.5D);
        int minY = SectionPos.posToSectionCoord(y1 - 0.5D);
        int minZ = SectionPos.posToSectionCoord(z1 - 0.5D);

        int maxX = SectionPos.posToSectionCoord(x2 + 0.5D);
        int maxY = SectionPos.posToSectionCoord(y2 + 0.5D);
        int maxZ = SectionPos.posToSectionCoord(z2 + 0.5D);

        for (int x = minX; x <= maxX; x++) {
            for (int z = minZ; z <= maxZ; z++) {
                for (int y = minY; y <= maxY; y++) {
                    if (this.renderSectionManager.isSectionVisible(x, y, z)) {
                        return true;
                    }
                }
            }
        }

        return false;
    }

    public String getChunksDebugString() {
        // C: visible/total D: distance
        // TODO: add dirty and queued counts
        return String.format("C: %d/%d D: %d", this.renderSectionManager.getVisibleChunkCount(), this.renderSectionManager.getTotalSections(), this.renderDistance);
    }

    /**
     * Schedules chunk rebuilds for all chunks in the specified block region.
     */
    public void scheduleRebuildForBlockArea(int minX, int minY, int minZ, int maxX, int maxY, int maxZ, boolean important) {
        this.scheduleRebuildForChunks(minX >> 4, minY >> 4, minZ >> 4, maxX >> 4, maxY >> 4, maxZ >> 4, important);
    }

    /**
     * Schedules chunk rebuilds for all chunks in the specified chunk region.
     */
    public void scheduleRebuildForChunks(int minX, int minY, int minZ, int maxX, int maxY, int maxZ, boolean important) {
        for (int chunkX = minX; chunkX <= maxX; chunkX++) {
            for (int chunkY = minY; chunkY <= maxY; chunkY++) {
                for (int chunkZ = minZ; chunkZ <= maxZ; chunkZ++) {
                    this.scheduleRebuildForChunk(chunkX, chunkY, chunkZ, important);
                }
            }
        }
    }

    /**
     * Schedules a chunk rebuild for the render belonging to the given chunk section position.
     */
    public void scheduleRebuildForChunk(int x, int y, int z, boolean important) {
        this.renderSectionManager.scheduleRebuild(x, y, z, important);
    }

    public Collection<String> getDebugStrings() {
        return this.renderSectionManager.getDebugStrings();
    }

    public boolean isSectionReady(int x, int y, int z) {
        return this.renderSectionManager.isSectionBuilt(x, y, z);
    }
}
