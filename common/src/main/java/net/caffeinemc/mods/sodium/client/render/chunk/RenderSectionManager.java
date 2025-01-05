package net.caffeinemc.mods.sodium.client.render.chunk;

import com.mojang.blaze3d.systems.RenderSystem;
import it.unimi.dsi.fastutil.longs.Long2ReferenceMap;
import it.unimi.dsi.fastutil.longs.Long2ReferenceMaps;
import it.unimi.dsi.fastutil.longs.Long2ReferenceOpenHashMap;
import it.unimi.dsi.fastutil.objects.Reference2ReferenceLinkedOpenHashMap;
import it.unimi.dsi.fastutil.objects.ReferenceOpenHashSet;
import it.unimi.dsi.fastutil.objects.ReferenceSet;
import it.unimi.dsi.fastutil.objects.ReferenceSets;
import net.caffeinemc.mods.sodium.client.SodiumClientMod;
import net.caffeinemc.mods.sodium.client.gl.device.CommandList;
import net.caffeinemc.mods.sodium.client.gl.device.RenderDevice;
import net.caffeinemc.mods.sodium.client.render.chunk.compile.BuilderTaskOutput;
import net.caffeinemc.mods.sodium.client.render.chunk.compile.ChunkBuildOutput;
import net.caffeinemc.mods.sodium.client.render.chunk.compile.ChunkSortOutput;
import net.caffeinemc.mods.sodium.client.render.chunk.compile.executor.ChunkBuilder;
import net.caffeinemc.mods.sodium.client.render.chunk.compile.executor.ChunkJobResult;
import net.caffeinemc.mods.sodium.client.render.chunk.compile.executor.ChunkJobCollector;
import net.caffeinemc.mods.sodium.client.render.chunk.compile.tasks.ChunkBuilderMeshingTask;
import net.caffeinemc.mods.sodium.client.render.chunk.compile.tasks.ChunkBuilderSortingTask;
import net.caffeinemc.mods.sodium.client.render.chunk.compile.tasks.ChunkBuilderTask;
import net.caffeinemc.mods.sodium.client.render.chunk.data.BuiltSectionInfo;
import net.caffeinemc.mods.sodium.client.render.chunk.lists.ChunkRenderList;
import net.caffeinemc.mods.sodium.client.render.chunk.lists.SortedRenderLists;
import net.caffeinemc.mods.sodium.client.render.chunk.lists.VisibleChunkCollector;
import net.caffeinemc.mods.sodium.client.render.chunk.occlusion.GraphDirection;
import net.caffeinemc.mods.sodium.client.render.chunk.occlusion.OcclusionCuller;
import net.caffeinemc.mods.sodium.client.render.chunk.region.RenderRegion;
import net.caffeinemc.mods.sodium.client.render.chunk.region.RenderRegionManager;
import net.caffeinemc.mods.sodium.client.render.chunk.terrain.TerrainRenderPass;
import net.caffeinemc.mods.sodium.client.render.chunk.translucent_sorting.SortBehavior.DeferMode;
import net.caffeinemc.mods.sodium.client.render.chunk.translucent_sorting.SortBehavior.PriorityMode;
import net.caffeinemc.mods.sodium.client.render.chunk.translucent_sorting.data.DynamicTopoData;
import net.caffeinemc.mods.sodium.client.render.chunk.translucent_sorting.data.NoData;
import net.caffeinemc.mods.sodium.client.render.chunk.translucent_sorting.data.TranslucentData;
import net.caffeinemc.mods.sodium.client.render.chunk.translucent_sorting.trigger.CameraMovement;
import net.caffeinemc.mods.sodium.client.render.chunk.translucent_sorting.trigger.SortTriggering;
import net.caffeinemc.mods.sodium.client.render.chunk.vertex.format.ChunkMeshFormats;
import net.caffeinemc.mods.sodium.client.render.texture.SpriteUtil;
import net.caffeinemc.mods.sodium.client.render.util.RenderAsserts;
import net.caffeinemc.mods.sodium.client.render.viewport.CameraTransform;
import net.caffeinemc.mods.sodium.client.render.viewport.Viewport;
import net.caffeinemc.mods.sodium.client.util.MathUtil;
import net.caffeinemc.mods.sodium.client.world.LevelSlice;
import net.caffeinemc.mods.sodium.client.world.cloned.ChunkRenderContext;
import net.caffeinemc.mods.sodium.client.world.cloned.ClonedChunkSectionCache;
import net.minecraft.client.Camera;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.renderer.texture.TextureAtlasSprite;
import net.minecraft.core.BlockPos;
import net.minecraft.core.SectionPos;
import net.minecraft.util.Mth;
import net.minecraft.world.level.chunk.ChunkAccess;
import net.minecraft.world.level.chunk.LevelChunkSection;
import org.apache.commons.lang3.ArrayUtils;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.joml.Vector3dc;

import java.util.*;
import java.util.concurrent.ConcurrentLinkedDeque;

public class RenderSectionManager {
    private final ChunkBuilder builder;

    private final RenderRegionManager regions;
    private final ClonedChunkSectionCache sectionCache;

    private final Long2ReferenceMap<RenderSection> sectionByPosition = new Long2ReferenceOpenHashMap<>();

    private final ConcurrentLinkedDeque<ChunkJobResult<? extends BuilderTaskOutput>> buildResults = new ConcurrentLinkedDeque<>();

    private final ChunkRenderer chunkRenderer;

    private final ClientLevel level;

    private final ReferenceSet<RenderSection> sectionsWithGlobalEntities = new ReferenceOpenHashSet<>();

    private final OcclusionCuller occlusionCuller;

    private final int renderDistance;

    private final SortTriggering sortTriggering;

    private ChunkJobCollector lastBlockingCollector;

    @NotNull
    private SortedRenderLists renderLists;

    @NotNull
    private Map<ChunkUpdateType, ArrayDeque<RenderSection>> taskLists;

    private int lastUpdatedFrame;

    private boolean needsGraphUpdate;

    private @Nullable BlockPos cameraBlockPos;
    private @Nullable Vector3dc cameraPosition;

    public RenderSectionManager(ClientLevel level, int renderDistance, CommandList commandList) {
        this.chunkRenderer = new DefaultChunkRenderer(RenderDevice.INSTANCE, ChunkMeshFormats.COMPACT);

        this.level = level;
        this.builder = new ChunkBuilder(level, ChunkMeshFormats.COMPACT);

        this.needsGraphUpdate = true;
        this.renderDistance = renderDistance;

        this.sortTriggering = new SortTriggering();

        this.regions = new RenderRegionManager(commandList);
        this.sectionCache = new ClonedChunkSectionCache(this.level);

        this.renderLists = SortedRenderLists.empty();
        this.occlusionCuller = new OcclusionCuller(Long2ReferenceMaps.unmodifiable(this.sectionByPosition), this.level);

        this.taskLists = new EnumMap<>(ChunkUpdateType.class);

        for (var type : ChunkUpdateType.values()) {
            this.taskLists.put(type, new ArrayDeque<>());
        }
    }

    public void updateCameraState(Vector3dc cameraPosition, Camera camera) {
        this.cameraBlockPos = camera.getBlockPosition();
        this.cameraPosition = cameraPosition;
    }

    public void update(Camera camera, Viewport viewport, boolean spectator) {
        this.lastUpdatedFrame += 1;

        this.createTerrainRenderList(camera, viewport, this.lastUpdatedFrame, spectator);

        this.needsGraphUpdate = false;
    }

    private void createTerrainRenderList(Camera camera, Viewport viewport, int frame, boolean spectator) {
        this.resetRenderLists();

        final var searchDistance = this.getSearchDistance();
        final var useOcclusionCulling = this.shouldUseOcclusionCulling(camera, spectator);

        var visitor = new VisibleChunkCollector(frame);

        this.occlusionCuller.findVisible(visitor, viewport, searchDistance, useOcclusionCulling, frame);

        this.renderLists = visitor.createRenderLists(viewport);
        this.taskLists = visitor.getRebuildLists();
    }

    private float getSearchDistance() {
        float distance;

        if (SodiumClientMod.options().performance.useFogOcclusion) {
            distance = this.getEffectiveRenderDistance();
        } else {
            distance = this.getRenderDistance();
        }

        return distance;
    }

    private boolean shouldUseOcclusionCulling(Camera camera, boolean spectator) {
        final boolean useOcclusionCulling;
        BlockPos origin = camera.getBlockPosition();

        if (spectator && this.level.getBlockState(origin)
                .isSolidRender())
        {
            useOcclusionCulling = false;
        } else {
            useOcclusionCulling = Minecraft.getInstance().smartCull;
        }
        return useOcclusionCulling;
    }

    private void resetRenderLists() {
        this.renderLists = SortedRenderLists.empty();

        for (var list : this.taskLists.values()) {
            list.clear();
        }
    }

    public void onSectionAdded(int x, int y, int z) {
        long key = SectionPos.asLong(x, y, z);

        if (this.sectionByPosition.containsKey(key)) {
            return;
        }

        RenderRegion region = this.regions.createForChunk(x, y, z);

        RenderSection renderSection = new RenderSection(region, x, y, z);
        region.addSection(renderSection);

        this.sectionByPosition.put(key, renderSection);

        ChunkAccess chunk = this.level.getChunk(x, z);
        LevelChunkSection section = chunk.getSections()[this.level.getSectionIndexFromSectionY(y)];

        if (section.hasOnlyAir()) {
            this.updateSectionInfo(renderSection, BuiltSectionInfo.EMPTY);
        } else {
            renderSection.setPendingUpdate(ChunkUpdateType.INITIAL_BUILD);
        }

        this.connectNeighborNodes(renderSection);

        // force update to schedule build task
        this.needsGraphUpdate = true;
    }

    public void onSectionRemoved(int x, int y, int z) {
        long sectionPos = SectionPos.asLong(x, y, z);
        RenderSection section = this.sectionByPosition.remove(sectionPos);

        if (section == null) {
            return;
        }

        if (section.getTranslucentData() != null) {
            this.sortTriggering.removeSection(section.getTranslucentData(), sectionPos);
        }

        RenderRegion region = section.getRegion();

        if (region != null) {
            region.removeSection(section);
        }

        this.disconnectNeighborNodes(section);
        this.updateSectionInfo(section, null);

        section.delete();

        // force update to remove section from render lists
        this.needsGraphUpdate = true;
    }

    public void renderLayer(ChunkRenderMatrices matrices, TerrainRenderPass pass, double x, double y, double z) {
        RenderDevice device = RenderDevice.INSTANCE;
        CommandList commandList = device.createCommandList();

        this.chunkRenderer.render(matrices, commandList, this.renderLists, pass, new CameraTransform(x, y, z));

        commandList.flush();
    }

    public void tickVisibleRenders() {
        Iterator<ChunkRenderList> it = this.renderLists.iterator();

        while (it.hasNext()) {
            ChunkRenderList renderList = it.next();

            var region = renderList.getRegion();
            var iterator = renderList.sectionsWithSpritesIterator();

            if (iterator == null) {
                continue;
            }

            while (iterator.hasNext()) {
                var section = region.getSection(iterator.nextByteAsInt());

                if (section == null) {
                    continue;
                }

                var sprites = section.getAnimatedSprites();

                if (sprites == null) {
                    continue;
                }

                for (TextureAtlasSprite sprite : sprites) {
                    SpriteUtil.markSpriteActive(sprite);
                }
            }
        }
    }

    public boolean isSectionVisible(int x, int y, int z) {
        RenderSection render = this.getRenderSection(x, y, z);

        if (render == null) {
            return false;
        }

        return render.getLastVisibleFrame() == this.lastUpdatedFrame;
    }

    public void uploadChunks() {
        var results = this.collectChunkBuildResults();

        if (results.isEmpty()) {
            return;
        }

        // only mark as needing a graph update if the uploads could have changed the graph
        // (sort results never change the graph)
        // generally there's no sort results without a camera movement, which would also trigger
        // a graph update, but it can sometimes happen because of async task execution
        this.needsGraphUpdate |= this.processChunkBuildResults(results);

        for (var result : results) {
            result.destroy();
        }
    }

    private boolean processChunkBuildResults(ArrayList<BuilderTaskOutput> results) {
        var filtered = filterChunkBuildResults(results);

        this.regions.uploadResults(RenderDevice.INSTANCE.createCommandList(), filtered);

        boolean touchedSectionInfo = false;
        for (var result : filtered) {
            TranslucentData oldData = result.render.getTranslucentData();
            if (result instanceof ChunkBuildOutput chunkBuildOutput) {
                touchedSectionInfo |= this.updateSectionInfo(result.render, chunkBuildOutput.info);

                if (chunkBuildOutput.translucentData != null) {
                    this.sortTriggering.integrateTranslucentData(oldData, chunkBuildOutput.translucentData, this.cameraPosition, this::scheduleSort);

                    // a rebuild always generates new translucent data which means applyTriggerChanges isn't necessary
                    result.render.setTranslucentData(chunkBuildOutput.translucentData);
                }
            } else if (result instanceof ChunkSortOutput sortOutput
                    && sortOutput.getDynamicSorter() != null
                    && result.render.getTranslucentData() instanceof DynamicTopoData data) {
                this.sortTriggering.applyTriggerChanges(data, sortOutput.getDynamicSorter(), result.render.getPosition(), this.cameraPosition);
            }

            var job = result.render.getTaskCancellationToken();

            // clear the cancellation token (thereby marking the section as not having an
            // active task) if this job is the most recent submitted job for this section
            if (job != null && result.submitTime >= result.render.getLastSubmittedFrame()) {
                result.render.setTaskCancellationToken(null);
            }

            result.render.setLastUploadFrame(result.submitTime);
        }

        return touchedSectionInfo;
    }

    private boolean updateSectionInfo(RenderSection render, BuiltSectionInfo info) {
        var infoChanged = render.setInfo(info);

        if (info == null || ArrayUtils.isEmpty(info.globalBlockEntities)) {
            return this.sectionsWithGlobalEntities.remove(render) || infoChanged;
        } else {
            return this.sectionsWithGlobalEntities.add(render) || infoChanged;
        }
    }

    private static List<BuilderTaskOutput> filterChunkBuildResults(ArrayList<BuilderTaskOutput> outputs) {
        var map = new Reference2ReferenceLinkedOpenHashMap<RenderSection, BuilderTaskOutput>();

        for (var output : outputs) {
            // throw out outdated or duplicate outputs
            if (output.render.isDisposed() || output.render.getLastUploadFrame() > output.submitTime) {
                continue;
            }

            var render = output.render;
            var previous = map.get(render);

            if (previous == null || previous.submitTime < output.submitTime) {
                map.put(render, output);
            }
        }

        return new ArrayList<>(map.values());
    }

    private ArrayList<BuilderTaskOutput> collectChunkBuildResults() {
        ArrayList<BuilderTaskOutput> results = new ArrayList<>();
        ChunkJobResult<? extends BuilderTaskOutput> result;

        while ((result = this.buildResults.poll()) != null) {
            results.add(result.unwrap());
        }

        return results;
    }

    public void cleanupAndFlip() {
        this.sectionCache.cleanup();
        this.regions.update();
    }

    public void updateChunks(boolean updateImmediately) {
        var thisFrameBlockingCollector = this.lastBlockingCollector;
        this.lastBlockingCollector = null;
        if (thisFrameBlockingCollector == null) {
            thisFrameBlockingCollector = new ChunkJobCollector(this.buildResults::add);
        }

        if (updateImmediately) {
            // for a perfect frame where everything is finished use the last frame's blocking collector
            // and add all tasks to it so that they're waited on
            this.submitSectionTasks(thisFrameBlockingCollector, thisFrameBlockingCollector, thisFrameBlockingCollector);

            thisFrameBlockingCollector.awaitCompletion(this.builder);
        } else {
            var nextFrameBlockingCollector = new ChunkJobCollector(this.buildResults::add);
            var deferredCollector = new ChunkJobCollector(
                this.builder.getHighEffortSchedulingBudget(),
                this.builder.getLowEffortSchedulingBudget(),
                this.buildResults::add);

            // if zero frame delay is allowed, submit important sorts with the current frame blocking collector.
            // otherwise submit with the collector that the next frame is blocking on.
            if (SodiumClientMod.options().performance.getSortBehavior().getDeferMode() == DeferMode.ZERO_FRAMES) {
                this.submitSectionTasks(thisFrameBlockingCollector, nextFrameBlockingCollector, deferredCollector);
            } else {
                this.submitSectionTasks(nextFrameBlockingCollector, nextFrameBlockingCollector, deferredCollector);
            }

            // wait on this frame's blocking collector which contains the important tasks from this frame
            // and semi-important tasks from the last frame
            thisFrameBlockingCollector.awaitCompletion(this.builder);

            // store the semi-important collector to wait on it in the next frame
            this.lastBlockingCollector = nextFrameBlockingCollector;
        }
    }

    private void submitSectionTasks(
        ChunkJobCollector importantCollector,
        ChunkJobCollector semiImportantCollector,
        ChunkJobCollector deferredCollector) {
            this.submitSectionTasks(importantCollector, ChunkUpdateType.IMPORTANT_SORT, true);
            this.submitSectionTasks(semiImportantCollector, ChunkUpdateType.IMPORTANT_REBUILD, true);

            // since the sort tasks are run last, the effort category can be ignored and
            // simply fills up the remaining budget. Splitting effort categories is still
            // important to prevent high effort tasks from using up the entire budget if it
            // happens to divide evenly.
            this.submitSectionTasks(deferredCollector, ChunkUpdateType.REBUILD, false);
            this.submitSectionTasks(deferredCollector, ChunkUpdateType.INITIAL_BUILD, false);
            this.submitSectionTasks(deferredCollector, ChunkUpdateType.SORT, true);
    }

    private void submitSectionTasks(ChunkJobCollector collector, ChunkUpdateType type, boolean ignoreEffortCategory) {
        var queue = this.taskLists.get(type);

        while (!queue.isEmpty() && collector.hasBudgetFor(type.getTaskEffort(), ignoreEffortCategory)) {
            RenderSection section = queue.remove();

            if (section.isDisposed()) {
                continue;
            }

            // stop if the section is in this list but doesn't have this update type
            var pendingUpdate = section.getPendingUpdate();
            if (pendingUpdate != null && pendingUpdate != type) {
                continue;
            }

            int frame = this.lastUpdatedFrame;
            ChunkBuilderTask<? extends BuilderTaskOutput> task;
            if (type == ChunkUpdateType.SORT || type == ChunkUpdateType.IMPORTANT_SORT) {
                task = this.createSortTask(section, frame);

                if (task == null) {
                    // when a sort task is null it means the render section has no dynamic data and
                    // doesn't need to be sorted. Nothing needs to be done.
                    continue;
                }
            } else {
                task = this.createRebuildTask(section, frame);

                if (task == null) {
                    // if the section is empty or doesn't exist submit this null-task to set the
                    // built flag on the render section.
                    // It's important to use a NoData instead of null translucency data here in
                    // order for it to clear the old data from the translucency sorting system.
                    // This doesn't apply to sorting tasks as that would result in the section being
                    // marked as empty just because it was scheduled to be sorted and its dynamic
                    // data has since been removed. In that case simply nothing is done as the
                    // rebuild that must have happened in the meantime includes new non-dynamic
                    // index data.
                    var result = ChunkJobResult.successfully(new ChunkBuildOutput(
                            section, frame, NoData.forEmptySection(section.getPosition()),
                            BuiltSectionInfo.EMPTY, Collections.emptyMap()));
                    this.buildResults.add(result);

                    section.setTaskCancellationToken(null);
                }
            }

            if (task != null) {
                var job = this.builder.scheduleTask(task, type.isImportant(), collector::onJobFinished);
                collector.addSubmittedJob(job);

                section.setTaskCancellationToken(job);
            }

            section.setLastSubmittedFrame(frame);
            section.setPendingUpdate(null);
        }
    }

    public @Nullable ChunkBuilderMeshingTask createRebuildTask(RenderSection render, int frame) {
        ChunkRenderContext context = LevelSlice.prepare(this.level, render.getPosition(), this.sectionCache);

        if (context == null) {
            return null;
        }

        return new ChunkBuilderMeshingTask(render, frame, this.cameraPosition, context);
    }

    public ChunkBuilderSortingTask createSortTask(RenderSection render, int frame) {
        return ChunkBuilderSortingTask.createTask(render, frame, this.cameraPosition);
    }

    public void processGFNIMovement(CameraMovement movement) {
        this.sortTriggering.triggerSections(this::scheduleSort, movement);
    }

    public void markGraphDirty() {
        this.needsGraphUpdate = true;
    }

    public boolean needsUpdate() {
        return this.needsGraphUpdate;
    }

    public ChunkBuilder getBuilder() {
        return this.builder;
    }

    public void destroy() {
        this.builder.shutdown(); // stop all the workers, and cancel any tasks

        for (var result : this.collectChunkBuildResults()) {
            result.destroy(); // delete resources for any pending tasks (including those that were cancelled)
        }

        for (var section : this.sectionByPosition.values()) {
            section.delete();
        }

        this.sectionsWithGlobalEntities.clear();
        this.resetRenderLists();

        try (CommandList commandList = RenderDevice.INSTANCE.createCommandList()) {
            this.regions.delete(commandList);
            this.chunkRenderer.delete(commandList);
        }
    }

    public int getTotalSections() {
        return this.sectionByPosition.size();
    }

    public int getVisibleChunkCount() {
        var sections = 0;
        var iterator = this.renderLists.iterator();

        while (iterator.hasNext()) {
            var renderList = iterator.next();
            sections += renderList.getSectionsWithGeometryCount();
        }

        return sections;
    }

    public void scheduleSort(long sectionPos, boolean isDirectTrigger) {
        RenderSection section = this.sectionByPosition.get(sectionPos);

        if (section != null) {
            var pendingUpdate = ChunkUpdateType.SORT;
            var priorityMode = SodiumClientMod.options().performance.getSortBehavior().getPriorityMode();
            if (priorityMode == PriorityMode.ALL
                    || priorityMode == PriorityMode.NEARBY && this.shouldPrioritizeTask(section, NEARBY_SORT_DISTANCE)) {
                pendingUpdate = ChunkUpdateType.IMPORTANT_SORT;
            }
            pendingUpdate = ChunkUpdateType.getPromotionUpdateType(section.getPendingUpdate(), pendingUpdate);
            if (pendingUpdate != null) {
                section.setPendingUpdate(pendingUpdate);
                section.prepareTrigger(isDirectTrigger);
            }
        }
    }

    public void scheduleRebuild(int x, int y, int z, boolean important) {
        RenderAsserts.validateCurrentThread();

        this.sectionCache.invalidate(x, y, z);

        RenderSection section = this.sectionByPosition.get(SectionPos.asLong(x, y, z));

        if (section != null && section.isBuilt()) {
            ChunkUpdateType pendingUpdate;

            if (allowImportantRebuilds() && (important || this.shouldPrioritizeTask(section, NEARBY_REBUILD_DISTANCE))) {
                pendingUpdate = ChunkUpdateType.IMPORTANT_REBUILD;
            } else {
                pendingUpdate = ChunkUpdateType.REBUILD;
            }

            pendingUpdate = ChunkUpdateType.getPromotionUpdateType(section.getPendingUpdate(), pendingUpdate);
            if (pendingUpdate != null) {
                section.setPendingUpdate(pendingUpdate);

                // force update to schedule rebuild task on this section
                this.needsGraphUpdate = true;
            }
        }
    }

    private static final float NEARBY_REBUILD_DISTANCE = Mth.square(16.0f);
    private static final float NEARBY_SORT_DISTANCE = Mth.square(25.0f);

    private boolean shouldPrioritizeTask(RenderSection section, float distance) {
        return this.cameraBlockPos != null && section.getSquaredDistance(this.cameraBlockPos) < distance;
    }

    private static boolean allowImportantRebuilds() {
        return !SodiumClientMod.options().performance.alwaysDeferChunkUpdates;
    }

    private float getEffectiveRenderDistance() {
        var alpha = RenderSystem.getShaderFog().alpha();
        var distance = RenderSystem.getShaderFog().end();

        var renderDistance = this.getRenderDistance();

        // The fog must be fully opaque in order to skip rendering of chunks behind it
        if (!Mth.equal(alpha, 1.0f)) {
            return renderDistance;
        }

        return Math.min(renderDistance, distance + 0.5f);
    }

    private float getRenderDistance() {
        return this.renderDistance * 16.0f;
    }

    private void connectNeighborNodes(RenderSection render) {
        for (int direction = 0; direction < GraphDirection.COUNT; direction++) {
            RenderSection adj = this.getRenderSection(render.getChunkX() + GraphDirection.x(direction),
                    render.getChunkY() + GraphDirection.y(direction),
                    render.getChunkZ() + GraphDirection.z(direction));

            if (adj != null) {
                adj.setAdjacentNode(GraphDirection.opposite(direction), render);
                render.setAdjacentNode(direction, adj);
            }
        }
    }

    private void disconnectNeighborNodes(RenderSection render) {
        for (int direction = 0; direction < GraphDirection.COUNT; direction++) {
            RenderSection adj = render.getAdjacent(direction);

            if (adj != null) {
                adj.setAdjacentNode(GraphDirection.opposite(direction), null);
                render.setAdjacentNode(direction, null);
            }
        }
    }

    private RenderSection getRenderSection(int x, int y, int z) {
        return this.sectionByPosition.get(SectionPos.asLong(x, y, z));
    }

    public Collection<String> getDebugStrings() {
        List<String> list = new ArrayList<>();

        int count = 0;

        long geometryDeviceUsed = 0;
        long geometryDeviceAllocated = 0;
        long indexDeviceUsed = 0;
        long indexDeviceAllocated = 0;

        for (var region : this.regions.getLoadedRegions()) {
            var resources = region.getResources();

            if (resources == null) {
                continue;
            }

            var geometryArena = resources.getGeometryArena();
            geometryDeviceUsed += geometryArena.getDeviceUsedMemory();
            geometryDeviceAllocated += geometryArena.getDeviceAllocatedMemory();

            var indexArena = resources.getIndexArena();
            indexDeviceUsed += indexArena.getDeviceUsedMemory();
            indexDeviceAllocated += indexArena.getDeviceAllocatedMemory();

            count++;
        }

        list.add(String.format("Geometry Pool: %d/%d MiB (%d buffers)", MathUtil.toMib(geometryDeviceUsed), MathUtil.toMib(geometryDeviceAllocated), count));
        list.add(String.format("Index Pool: %d/%d MiB", MathUtil.toMib(indexDeviceUsed), MathUtil.toMib(indexDeviceAllocated)));
        list.add(String.format("Transfer Queue: %s", this.regions.getStagingBuffer().toString()));

        list.add(String.format("Chunk Builder: Permits=%02d (E %03d) | Busy=%02d | Total=%02d",
                this.builder.getScheduledJobCount(), this.builder.getScheduledEffort(), this.builder.getBusyThreadCount(), this.builder.getTotalThreadCount())
        );

        list.add(String.format("Chunk Queues: U=%02d (P0=%03d | P1=%03d | P2=%03d)",
                this.buildResults.size(),
                this.taskLists.get(ChunkUpdateType.IMPORTANT_REBUILD).size() + this.taskLists.get(ChunkUpdateType.IMPORTANT_SORT).size(),
                this.taskLists.get(ChunkUpdateType.REBUILD).size() + this.taskLists.get(ChunkUpdateType.SORT).size(),
                this.taskLists.get(ChunkUpdateType.INITIAL_BUILD).size())
        );

        this.sortTriggering.addDebugStrings(list);

        return list;
    }

    public @NotNull SortedRenderLists getRenderLists() {
        return this.renderLists;
    }

    public boolean isSectionBuilt(int x, int y, int z) {
        var section = this.getRenderSection(x, y, z);
        return section != null && section.isBuilt();
    }

    public void onChunkAdded(int x, int z) {
        for (int y = this.level.getMinSectionY(); y <= this.level.getMaxSectionY(); y++) {
            this.onSectionAdded(x, y, z);
        }
    }

    public void onChunkRemoved(int x, int z) {
        for (int y = this.level.getMinSectionY(); y <= this.level.getMaxSectionY(); y++) {
            this.onSectionRemoved(x, y, z);
        }
    }

    public Collection<RenderSection> getSectionsWithGlobalEntities() {
        return ReferenceSets.unmodifiable(this.sectionsWithGlobalEntities);
    }
}
