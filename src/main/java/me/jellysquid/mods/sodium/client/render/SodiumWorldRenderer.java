package me.jellysquid.mods.sodium.client.render;

import it.unimi.dsi.fastutil.longs.Long2ObjectMap;
import it.unimi.dsi.fastutil.objects.ObjectOpenHashSet;
import me.jellysquid.mods.sodium.client.SodiumClientMod;
import me.jellysquid.mods.sodium.client.gl.device.CommandList;
import me.jellysquid.mods.sodium.client.gl.device.RenderDevice;
import me.jellysquid.mods.sodium.client.render.chunk.RenderSectionManager;
import me.jellysquid.mods.sodium.client.render.chunk.data.ChunkRenderData;
import me.jellysquid.mods.sodium.client.render.chunk.passes.BlockRenderPass;
import me.jellysquid.mods.sodium.client.render.chunk.passes.BlockRenderPassManager;
import me.jellysquid.mods.sodium.client.render.pipeline.context.ChunkRenderCacheShared;
import me.jellysquid.mods.sodium.client.util.NativeBuffer;
import me.jellysquid.mods.sodium.client.util.math.FrustumExtended;
import me.jellysquid.mods.sodium.client.world.ChunkStatusListener;
import me.jellysquid.mods.sodium.client.world.ClientChunkManagerExtended;
import me.jellysquid.mods.sodium.client.world.WorldRendererExtended;
import me.jellysquid.mods.sodium.common.util.ListUtil;
import net.minecraft.block.entity.BlockEntity;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.network.ClientPlayerEntity;
import net.minecraft.client.render.*;
import net.minecraft.client.render.block.entity.BlockEntityRenderDispatcher;
import net.minecraft.client.render.model.ModelLoader;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.client.world.ClientWorld;
import net.minecraft.entity.Entity;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Box;
import net.minecraft.util.math.MathHelper;
import net.minecraft.util.math.Vec3d;
import net.minecraft.util.profiler.Profiler;

import java.util.*;

/**
 * Provides an extension to vanilla's {@link WorldRenderer}.
 */
public class SodiumWorldRenderer implements ChunkStatusListener {
    private final MinecraftClient client;

    private ClientWorld world;
    private int renderDistance;

    private double lastCameraX, lastCameraY, lastCameraZ;
    private double lastCameraPitch, lastCameraYaw;

    private boolean useEntityCulling;

    private final Set<BlockEntity> globalBlockEntities = new ObjectOpenHashSet<>();

    private Frustum frustum;
    private RenderSectionManager renderSectionManager;
    private BlockRenderPassManager renderPassManager;

    /**
     * @return The SodiumWorldRenderer based on the current dimension
     */
    public static SodiumWorldRenderer instance() {
        var instance = instanceNullable();

        if (instance == null) {
            throw new IllegalStateException("No renderer attached to active world");
        }

        return instance;
    }

    /**
     * @return The SodiumWorldRenderer based on the current dimension, or null if none is attached
     */
    public static SodiumWorldRenderer instanceNullable() {
        var world = MinecraftClient.getInstance().worldRenderer;

        if (world instanceof WorldRendererExtended) {
            return ((WorldRendererExtended) world).getSodiumWorldRenderer();
        }

        return null;
    }

    public SodiumWorldRenderer(MinecraftClient client) {
        this.client = client;
    }

    public void setWorld(ClientWorld world) {
        // Check that the world is actually changing
        if (this.world == world) {
            return;
        }

        // If we have a world is already loaded, unload the renderer
        if (this.world != null) {
            this.unloadWorld();
        }

        // If we're loading a new world, load the renderer
        if (world != null) {
            this.loadWorld(world);
        }
    }

    private void loadWorld(ClientWorld world) {
        this.world = world;

        ChunkRenderCacheShared.createRenderContext(this.world);

        try (CommandList commandList = RenderDevice.INSTANCE.createCommandList()) {
            this.initRenderer(commandList);
        }

        ((ClientChunkManagerExtended) world.getChunkManager()).setListener(this);
    }

    private void unloadWorld() {
        ChunkRenderCacheShared.destroyRenderContext(this.world);

        if (this.renderSectionManager != null) {
            this.renderSectionManager.destroy();
            this.renderSectionManager = null;
        }

        this.globalBlockEntities.clear();

        this.world = null;
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
    public void updateChunks(Camera camera, Frustum frustum, boolean hasForcedFrustum, int frame, boolean spectator) {
        NativeBuffer.reclaim(false);

        this.frustum = frustum;

        this.useEntityCulling = SodiumClientMod.options().advanced.useEntityCulling;

        if (this.client.options.viewDistance != this.renderDistance) {
            this.reload();
        }

        Profiler profiler = this.client.getProfiler();
        profiler.push("camera_setup");

        ClientPlayerEntity player = this.client.player;

        if (player == null) {
            throw new IllegalStateException("Client instance has no active player entity");
        }

        Vec3d pos = camera.getPos();
        float pitch = camera.getPitch();
        float yaw = camera.getYaw();

        boolean dirty = pos.x != this.lastCameraX || pos.y != this.lastCameraY || pos.z != this.lastCameraZ ||
                pitch != this.lastCameraPitch || yaw != this.lastCameraYaw;

        if (dirty) {
            this.renderSectionManager.markGraphDirty();
        }

        this.lastCameraX = pos.x;
        this.lastCameraY = pos.y;
        this.lastCameraZ = pos.z;
        this.lastCameraPitch = pitch;
        this.lastCameraYaw = yaw;

        profiler.swap("chunk_update");

        this.renderSectionManager.updateChunks();

        if (!hasForcedFrustum && this.renderSectionManager.isGraphDirty()) {
            profiler.swap("chunk_graph_rebuild");

            this.renderSectionManager.update(camera, (FrustumExtended) frustum, frame, spectator);
        }

        profiler.swap("visible_chunk_tick");

        this.renderSectionManager.tickVisibleRenders();

        profiler.pop();

        Entity.setRenderDistanceMultiplier(MathHelper.clamp((double) this.client.options.viewDistance / 8.0D, 1.0D, 2.5D) * (double) this.client.options.entityDistanceScaling);
    }

    /**
     * Performs a render pass for the given {@link RenderLayer} and draws all visible chunks for it.
     */
    public void drawChunkLayer(RenderLayer renderLayer, MatrixStack matrixStack, double x, double y, double z) {
        BlockRenderPass pass = this.renderPassManager.getRenderPassForLayer(renderLayer);
        pass.startDrawing();

        this.renderSectionManager.renderLayer(matrixStack, pass, x, y, z);

        pass.endDrawing();
    }

    public void reload() {
        if (this.world == null) {
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

        this.renderDistance = this.client.options.viewDistance;

        this.renderPassManager = BlockRenderPassManager.createDefaultMappings();

        this.renderSectionManager = new RenderSectionManager(this, this.renderPassManager, this.world, this.renderDistance, commandList);
        this.renderSectionManager.loadChunks();
    }

    public void renderTileEntities(MatrixStack matrices, BufferBuilderStorage bufferBuilders, Long2ObjectMap<SortedSet<BlockBreakingInfo>> blockBreakingProgressions,
                                   Camera camera, float tickDelta) {
        VertexConsumerProvider.Immediate immediate = bufferBuilders.getEntityVertexConsumers();

        Vec3d cameraPos = camera.getPos();
        double x = cameraPos.getX();
        double y = cameraPos.getY();
        double z = cameraPos.getZ();

        BlockEntityRenderDispatcher blockEntityRenderer = MinecraftClient.getInstance().getBlockEntityRenderDispatcher();

        for (BlockEntity blockEntity : this.renderSectionManager.getVisibleBlockEntities()) {
            BlockPos pos = blockEntity.getPos();

            matrices.push();
            matrices.translate((double) pos.getX() - x, (double) pos.getY() - y, (double) pos.getZ() - z);

            VertexConsumerProvider consumer = immediate;
            SortedSet<BlockBreakingInfo> breakingInfos = blockBreakingProgressions.get(pos.asLong());

            if (breakingInfos != null && !breakingInfos.isEmpty()) {
                int stage = breakingInfos.last().getStage();

                if (stage >= 0) {
                    MatrixStack.Entry entry = matrices.peek();
                    VertexConsumer transformer = new OverlayVertexConsumer(bufferBuilders.getEffectVertexConsumers().getBuffer(ModelLoader.BLOCK_DESTRUCTION_RENDER_LAYERS.get(stage)), entry.getModel(), entry.getNormal());
                    consumer = (layer) -> layer.hasCrumbling() ? VertexConsumers.union(transformer, immediate.getBuffer(layer)) : immediate.getBuffer(layer);
                }
            }


            blockEntityRenderer.render(blockEntity, tickDelta, matrices, consumer);

            matrices.pop();
        }

        for (BlockEntity blockEntity : this.globalBlockEntities) {
            BlockPos pos = blockEntity.getPos();

            matrices.push();
            matrices.translate((double) pos.getX() - x, (double) pos.getY() - y, (double) pos.getZ() - z);

            blockEntityRenderer.render(blockEntity, tickDelta, matrices, immediate);

            matrices.pop();
        }
    }

    @Override
    public void onChunkAdded(int x, int z) {
        this.renderSectionManager.onChunkAdded(x, z);
    }

    @Override
    public void onChunkRemoved(int x, int z) {
        this.renderSectionManager.onChunkRemoved(x, z);
    }

    public void onChunkRenderUpdated(int x, int y, int z, ChunkRenderData meshBefore, ChunkRenderData meshAfter) {
        ListUtil.updateList(this.globalBlockEntities, meshBefore.getGlobalBlockEntities(), meshAfter.getGlobalBlockEntities());

        this.renderSectionManager.onChunkRenderUpdates(x, y, z, meshAfter);
    }

    /**
     * Returns whether or not the entity intersects with any visible chunks in the graph.
     * @return True if the entity is visible, otherwise false
     */
    public boolean isEntityVisible(Entity entity) {
        if (!this.useEntityCulling) {
            return true;
        }

        Box box = entity.getVisibilityBoundingBox();

        // Entities outside the valid world height will never map to a rendered chunk
        // Always render these entities or they'll be culled incorrectly!
        if (box.maxY < 0.5D || box.minY > 255.5D) {
            return true;
        }

        // Ensure entities with outlines or nametags are always visible
        if (this.client.hasOutline(entity) || entity.shouldRenderName()) {
            return true;
        }

        return this.isBoxVisible(box.minX, box.minY, box.minZ, box.maxX, box.maxY, box.maxZ);
    }


    public boolean isBoxVisible(double x1, double y1, double z1, double x2, double y2, double z2) {
        int minX = MathHelper.floor(x1 - 0.5D) >> 4;
        int minY = MathHelper.floor(y1 - 0.5D) >> 4;
        int minZ = MathHelper.floor(z1 - 0.5D) >> 4;

        int maxX = MathHelper.floor(x2 + 0.5D) >> 4;
        int maxY = MathHelper.floor(y2 + 0.5D) >> 4;
        int maxZ = MathHelper.floor(z2 + 0.5D) >> 4;

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

    /**
     * @return The frustum of the current player's camera used to cull chunks
     */
    public Frustum getFrustum() {
        return this.frustum;
    }

    public String getChunksDebugString() {
        // C: visible/total
        // TODO: add dirty and queued counts
        return String.format("C: %s/%s", this.renderSectionManager.getVisibleChunkCount(), this.renderSectionManager.getTotalSections());
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

    public Collection<String> getMemoryDebugStrings() {
        return this.renderSectionManager.getDebugStrings();
    }

    public RenderSectionManager getRenderSectionManager() {
        return this.renderSectionManager;
    }
}
