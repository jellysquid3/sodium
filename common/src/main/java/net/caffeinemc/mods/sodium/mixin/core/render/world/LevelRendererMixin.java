package net.caffeinemc.mods.sodium.mixin.core.render.world;

import com.llamalad7.mixinextras.sugar.Local;
import com.llamalad7.mixinextras.sugar.ref.LocalBooleanRef;
import com.mojang.blaze3d.vertex.PoseStack;
import it.unimi.dsi.fastutil.longs.Long2ObjectMap;
import net.caffeinemc.mods.sodium.client.gl.device.RenderDevice;
import net.caffeinemc.mods.sodium.client.render.SodiumWorldRenderer;
import net.caffeinemc.mods.sodium.client.render.chunk.ChunkRenderMatrices;
import net.caffeinemc.mods.sodium.client.render.viewport.ViewportProvider;
import net.caffeinemc.mods.sodium.client.services.PlatformLevelRenderHooks;
import net.caffeinemc.mods.sodium.client.util.FlawlessFrames;
import net.caffeinemc.mods.sodium.client.world.LevelRendererExtension;
import net.minecraft.client.Camera;
import net.minecraft.client.DeltaTracker;
import net.minecraft.client.Minecraft;
import net.minecraft.client.Options;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.renderer.*;
import net.minecraft.client.renderer.blockentity.BlockEntityRenderDispatcher;
import net.minecraft.client.renderer.culling.Frustum;
import net.minecraft.client.renderer.entity.EntityRenderDispatcher;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.BlockDestructionProgress;
import net.minecraft.world.level.block.entity.BlockEntity;
import org.jetbrains.annotations.Nullable;
import org.joml.Matrix4f;
import org.spongepowered.asm.mixin.*;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.Redirect;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import java.util.SortedSet;
import java.util.function.Consumer;

@Mixin(LevelRenderer.class)
public abstract class LevelRendererMixin implements LevelRendererExtension {
    @Shadow
    @Final
    private RenderBuffers renderBuffers;

    @Shadow
    @Final
    private Long2ObjectMap<SortedSet<BlockDestructionProgress>> destructionProgress;

    @Shadow
    @Nullable
    private ClientLevel level;

    @Shadow
    private int ticks;

    @Shadow
    @Final
    private Minecraft minecraft;

    @Shadow
    private Frustum cullingFrustum;

    @Unique
    private SodiumWorldRenderer renderer;

    @Override
    public SodiumWorldRenderer sodium$getWorldRenderer() {
        return this.renderer;
    }

    @Redirect(method = "allChanged()V", at = @At(value = "INVOKE", target = "Lnet/minecraft/client/Options;getEffectiveRenderDistance()I", ordinal = 1))
    private int nullifyBuiltChunkStorage(Options options) {
        // Do not allow any resources to be allocated
        return 0;
    }

    @Inject(method = "<init>", at = @At("RETURN"))
    private void init(Minecraft client, EntityRenderDispatcher entityRenderDispatcher, BlockEntityRenderDispatcher blockEntityRenderDispatcher, RenderBuffers bufferBuilderStorage, CallbackInfo ci) {
        this.renderer = new SodiumWorldRenderer(client);
    }

    @Inject(method = "setLevel", at = @At("RETURN"))
    private void onWorldChanged(ClientLevel level, CallbackInfo ci) {
        RenderDevice.enterManagedCode();

        try {
            this.renderer.setLevel(level);
        } finally {
            RenderDevice.exitManagedCode();
        }
    }

    /**
     * @reason Redirect to our renderer
     * @author JellySquid
     */
    @Overwrite
    public int countRenderedSections() {
        return this.renderer.getVisibleChunkCount();
    }

    /**
     * @reason Redirect the check to our renderer
     * @author JellySquid
     */
    @Overwrite
    public boolean hasRenderedAllSections() {
        return this.renderer.isTerrainRenderComplete();
    }

    @Inject(method = "needsUpdate", at = @At("RETURN"))
    private void onTerrainUpdateScheduled(CallbackInfo ci) {
        this.renderer.scheduleTerrainUpdate();
    }

    /**
     * @reason Redirect the chunk layer render passes to our renderer
     * @author JellySquid
     */
    @Overwrite
    private void renderSectionLayer(RenderType renderLayer, double x, double y, double z, Matrix4f modelMatrix, Matrix4f projectionMatrix) {
        RenderDevice.enterManagedCode();

        try {
            this.renderer.drawChunkLayer(renderLayer, new ChunkRenderMatrices(projectionMatrix, modelMatrix), x, y, z);
        } finally {
            RenderDevice.exitManagedCode();
        }

        PlatformLevelRenderHooks.getInstance().runChunkLayerEvents(renderLayer, ((LevelRenderer) (Object) this), modelMatrix, projectionMatrix, this.ticks, this.minecraft.gameRenderer.getMainCamera(), this.cullingFrustum);
    }

    /**
     * @reason Redirect the terrain setup phase to our renderer
     * @author JellySquid
     */
    @Overwrite
    private void setupRender(Camera camera, Frustum frustum, boolean hasForcedFrustum, boolean spectator) {

        var viewport = ((ViewportProvider) frustum).sodium$createViewport();
        var updateChunksImmediately = FlawlessFrames.isActive();

        RenderDevice.enterManagedCode();

        try {
            this.renderer.setupTerrain(camera, viewport, spectator, updateChunksImmediately);
        } finally {
            RenderDevice.exitManagedCode();
        }
    }

    /**
     * @reason Redirect chunk updates to our renderer
     * @author JellySquid
     */
    @Overwrite
    public void setBlocksDirty(int minX, int minY, int minZ, int maxX, int maxY, int maxZ) {
        this.renderer.scheduleRebuildForBlockArea(minX, minY, minZ, maxX, maxY, maxZ, false);
    }

    /**
     * @reason Redirect chunk updates to our renderer
     * @author JellySquid
     */
    @Overwrite
    public void setSectionDirtyWithNeighbors(int x, int y, int z) {
        this.renderer.scheduleRebuildForChunks(x - 1, y - 1, z - 1, x + 1, y + 1, z + 1, false);
    }

    /**
     * @reason Redirect chunk updates to our renderer
     * @author JellySquid
     */
    @Overwrite
    private void setBlockDirty(BlockPos pos, boolean important) {
        this.renderer.scheduleRebuildForBlockArea(pos.getX() - 1, pos.getY() - 1, pos.getZ() - 1, pos.getX() + 1, pos.getY() + 1, pos.getZ() + 1, important);
    }

    /**
     * @reason Redirect chunk updates to our renderer
     * @author JellySquid
     */
    @Overwrite
    private void setSectionDirty(int x, int y, int z, boolean important) {
        this.renderer.scheduleRebuildForChunk(x, y, z, important);
    }

    /**
     * @reason Redirect chunk updates to our renderer
     * @author JellySquid
     */
    @Overwrite
    public boolean isSectionCompiled(BlockPos pos) {
        return this.renderer.isSectionReady(pos.getX() >> 4, pos.getY() >> 4, pos.getZ() >> 4);
    }

    @Inject(method = "allChanged()V", at = @At("RETURN"))
    private void onReload(CallbackInfo ci) {
        RenderDevice.enterManagedCode();

        try {
            this.renderer.reload();
        } finally {
            RenderDevice.exitManagedCode();
        }
    }

    @Overwrite
    private void renderBlockEntities(PoseStack poseStack, MultiBufferSource.BufferSource bufferSource, MultiBufferSource.BufferSource bufferSource2, Camera camera, float f) {
        // TODO 1.21.2: Add the NeoForge glowing patch
        this.renderer.renderBlockEntities(new PoseStack(), this.renderBuffers, this.destructionProgress, camera, f, null);
    }

    // Exclusive to NeoForge, allow to fail.
    @SuppressWarnings("all")
    @Inject(method = "iterateVisibleBlockEntities", at = @At("HEAD"), cancellable = true, require = 0)
    public void replaceBlockEntityIteration(Consumer<BlockEntity> blockEntityConsumer, CallbackInfo ci) {
        ci.cancel();

        this.renderer.iterateVisibleBlockEntities(blockEntityConsumer);
    }

    /**
    * @reason Replace the debug string
    * @author JellySquid
    */
    @Overwrite
    public String getSectionStatistics() {
        return this.renderer.getChunksDebugString();
    }
}
