package net.caffeinemc.mods.sodium.client.render.chunk;

import com.mojang.blaze3d.systems.RenderSystem;
import it.unimi.dsi.fastutil.longs.Long2ReferenceMap;
import it.unimi.dsi.fastutil.longs.Long2ReferenceMaps;
import it.unimi.dsi.fastutil.longs.Long2ReferenceOpenHashMap;
import it.unimi.dsi.fastutil.longs.LongHeapPriorityQueue;
import it.unimi.dsi.fastutil.objects.*;
import net.caffeinemc.mods.sodium.client.SodiumClientMod;
import net.caffeinemc.mods.sodium.client.gl.device.CommandList;
import net.caffeinemc.mods.sodium.client.gl.device.RenderDevice;
import net.caffeinemc.mods.sodium.client.render.chunk.async.*;
import net.caffeinemc.mods.sodium.client.render.chunk.compile.BuilderTaskOutput;
import net.caffeinemc.mods.sodium.client.render.chunk.compile.ChunkBuildOutput;
import net.caffeinemc.mods.sodium.client.render.chunk.compile.ChunkSortOutput;
import net.caffeinemc.mods.sodium.client.render.chunk.compile.executor.ChunkBuilder;
import net.caffeinemc.mods.sodium.client.render.chunk.compile.executor.ChunkJobCollector;
import net.caffeinemc.mods.sodium.client.render.chunk.compile.executor.ChunkJobResult;
import net.caffeinemc.mods.sodium.client.render.chunk.compile.tasks.ChunkBuilderMeshingTask;
import net.caffeinemc.mods.sodium.client.render.chunk.compile.tasks.ChunkBuilderSortingTask;
import net.caffeinemc.mods.sodium.client.render.chunk.compile.tasks.ChunkBuilderTask;
import net.caffeinemc.mods.sodium.client.render.chunk.data.BuiltSectionInfo;
import net.caffeinemc.mods.sodium.client.render.chunk.lists.*;
import net.caffeinemc.mods.sodium.client.render.chunk.occlusion.*;
import net.caffeinemc.mods.sodium.client.render.chunk.region.RenderRegion;
import net.caffeinemc.mods.sodium.client.render.chunk.region.RenderRegionManager;
import net.caffeinemc.mods.sodium.client.render.chunk.terrain.TerrainRenderPass;
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
import java.util.concurrent.*;

public class RenderSectionManager {
    private static final float NEARBY_REBUILD_DISTANCE = Mth.square(16.0f);
    private static final float NEARBY_SORT_DISTANCE = Mth.square(25.0f);

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

    private PendingTaskCollector.TaskListCollection frustumTaskLists;
    private PendingTaskCollector.TaskListCollection globalTaskLists;

    private int frame;
    private int lastGraphDirtyFrame;
    private long lastFrameAtTime = System.nanoTime();

    private boolean needsGraphUpdate = true;
    private boolean needsRenderListUpdate = true;

    private @Nullable BlockPos cameraBlockPos;
    private @Nullable Vector3dc cameraPosition;

    private final ExecutorService asyncCullExecutor = Executors.newSingleThreadExecutor(runnable -> {
        Thread thread = new Thread(runnable);
        thread.setName("Sodium Async Cull Thread");
        return thread;
    });
    private final ObjectArrayList<AsyncRenderTask<?>> pendingTasks = new ObjectArrayList<>();
    private SectionTree renderTree = null;
    private TaskSectionTree globalTaskTree = null;
    private final Map<CullType, SectionTree> trees = new EnumMap<>(CullType.class);

    private final AsyncCameraTimingControl cameraTimingControl = new AsyncCameraTimingControl();

    public RenderSectionManager(ClientLevel level, int renderDistance, CommandList commandList) {
        this.chunkRenderer = new DefaultChunkRenderer(RenderDevice.INSTANCE, ChunkMeshFormats.COMPACT);

        this.level = level;
        this.builder = new ChunkBuilder(level, ChunkMeshFormats.COMPACT);

        this.renderDistance = renderDistance;

        this.sortTriggering = new SortTriggering();

        this.regions = new RenderRegionManager(commandList);
        this.sectionCache = new ClonedChunkSectionCache(this.level);

        this.renderLists = SortedRenderLists.empty();
        this.occlusionCuller = new OcclusionCuller(Long2ReferenceMaps.unmodifiable(this.sectionByPosition), this.level);
    }

    public void updateCameraState(Vector3dc cameraPosition, Camera camera) {
        this.cameraBlockPos = camera.getBlockPosition();
        this.cameraPosition = cameraPosition;
    }

    // TODO: schedule only as many tasks as fit within the frame time (measure how long the last frame took and how long the tasks took, though both of these will change over time)

    // TODO idea: increase and decrease chunk builder thread budget based on if the upload buffer was filled if the entire budget was used up. if the fallback way of uploading buffers is used, just doing 3 * the budget actually slows down frames while things are getting uploaded. For this it should limit how much (or how often?) things are uploaded. In the case of the mapped upload, just making sure we don't exceed its size is probably enough.

    // TODO: use narrow-to-wide order for scheduling tasks if we can expect the player not to move much

    public void updateRenderLists(Camera camera, Viewport viewport, boolean spectator, boolean updateImmediately) {
        this.frame += 1;
        this.lastFrameAtTime = System.nanoTime();

        // do sync bfs based on update immediately (flawless frames) or if the camera moved too much
        var shouldRenderSync = this.cameraTimingControl.getShouldRenderSync(camera);
        if ((updateImmediately || shouldRenderSync) && (this.needsGraphUpdate || this.needsRenderListUpdate)) {
            renderSync(camera, viewport, spectator);
            return;
        }

        if (this.needsGraphUpdate) {
            this.lastGraphDirtyFrame = this.frame;
            this.needsGraphUpdate = false;
        }

        // discard unusable present and pending frustum-tested trees
        if (this.needsRenderListUpdate) {
            this.trees.remove(CullType.FRUSTUM);

            this.pendingTasks.removeIf(task -> {
                if (task instanceof CullTask<?> cullTask && cullTask.getCullType() == CullType.FRUSTUM) {
                    cullTask.cancelImmediately();
                    return true;
                }
                return false;
            });
        }

        // remove all tasks that aren't in progress yet
        this.pendingTasks.removeIf(task -> {
            if (!task.hasStarted()) {
                task.cancelImmediately();
                return true;
            }
            return false;
        });

        this.unpackTaskResults(false);

        this.scheduleAsyncWork(camera, viewport, spectator);

        if (this.needsRenderListUpdate) {
            processRenderListUpdate(viewport);
        }

        var frameEnd = System.nanoTime();
//        System.out.println("Frame " + this.frame + " took " + (frameEnd - this.lastFrameAtTime) / 1000 + "µs");
    }

    private void renderSync(Camera camera, Viewport viewport, boolean spectator) {
        final var searchDistance = this.getSearchDistance();
        final var useOcclusionCulling = this.shouldUseOcclusionCulling(camera, spectator);

        // cancel running tasks to prevent two bfs running at the same time, which will cause race conditions
        for (var task : this.pendingTasks) {
            task.cancelImmediately();
        }
        this.pendingTasks.clear();

        var tree = new VisibleChunkCollectorSync(viewport, searchDistance, this.frame, CullType.FRUSTUM);
        this.occlusionCuller.findVisible(tree, viewport, searchDistance, useOcclusionCulling, this.frame);

        this.frustumTaskLists = tree.getPendingTaskLists();
        this.globalTaskLists = null;
        this.trees.put(CullType.FRUSTUM, tree);
        this.renderTree = tree;

        // remove the other trees, they're very wrong by now
        this.trees.remove(CullType.WIDE);
        this.trees.remove(CullType.REGULAR);

        this.renderLists = tree.createRenderLists();
        this.needsRenderListUpdate = false;
        this.needsGraphUpdate = false;
    }

    private SectionTree unpackTaskResults(boolean wait) {
        SectionTree latestTree = null;

        var it = this.pendingTasks.iterator();
        while (it.hasNext()) {
            var task = it.next();
            if (!wait && !task.isDone()) {
                continue;
            }
            it.remove();

            // unpack the task and its result based on its type
            switch (task) {
                case FrustumCullTask frustumCullTask -> {
                    var result = frustumCullTask.getResult();
                    this.frustumTaskLists = result.getFrustumTaskLists();

                    // ensure no useless frustum tree is accepted
                    if (!this.needsRenderListUpdate) {
                        var tree = result.getTree();
                        this.trees.put(CullType.FRUSTUM, tree);
                        latestTree = tree;

                        this.needsRenderListUpdate = true;
                    }
                }
                case GlobalCullTask globalCullTask -> {
                    var result = globalCullTask.getResult();
                    var tree = result.getTaskTree();
                    this.globalTaskLists = result.getGlobalTaskLists();
                    this.frustumTaskLists = result.getFrustumTaskLists();
                    this.globalTaskTree = tree;
                    this.trees.put(globalCullTask.getCullType(), tree);
                    latestTree = tree;

                    this.needsRenderListUpdate = true;
                }
                case FrustumTaskCollectionTask collectionTask ->
                        this.frustumTaskLists = collectionTask.getResult().getFrustumTaskLists();
                default -> {
                }
            }
        }

        return latestTree;
    }

    private void scheduleAsyncWork(Camera camera, Viewport viewport, boolean spectator) {
        // submit tasks of types that are applicable and not yet running
        AsyncRenderTask<?> currentRunningTask = null;
        if (!this.pendingTasks.isEmpty()) {
            currentRunningTask = this.pendingTasks.getFirst();
        }

        var transform = viewport.getTransform();
        var cameraSectionX = transform.intX >> 4;
        var cameraSectionY = transform.intY >> 4;
        var cameraSectionZ = transform.intZ >> 4;
        for (var type : CullType.WIDE_TO_NARROW) {
            var tree = this.trees.get(type);

            // don't schedule frustum-culled bfs until there hasn't been a dirty render list.
            // otherwise a bfs is done each frame that always gets thrown away.
            if (type == CullType.FRUSTUM && this.needsRenderListUpdate) {
                continue;
            }

            if ((tree == null || tree.getFrame() < this.lastGraphDirtyFrame ||
                    !tree.isValidFor(cameraSectionX, cameraSectionY, cameraSectionZ)) && (
                    currentRunningTask == null ||
                            currentRunningTask instanceof CullTask<?> cullTask && cullTask.getCullType() != type ||
                            currentRunningTask.getFrame() < this.lastGraphDirtyFrame)) {
                var searchDistance = this.getSearchDistance();
                var useOcclusionCulling = this.shouldUseOcclusionCulling(camera, spectator);

                var task = switch (type) {
                    case WIDE, REGULAR ->
                            new GlobalCullTask(this.occlusionCuller, viewport, searchDistance, useOcclusionCulling, this.frame, this.sectionByPosition, type);
                    case FRUSTUM ->
                            new FrustumCullTask(this.occlusionCuller, viewport, searchDistance, useOcclusionCulling, this.frame);
                };
                task.submitTo(this.asyncCullExecutor);
                this.pendingTasks.add(task);
            }
        }
    }

    private void processRenderListUpdate(Viewport viewport) {
        // schedule generating a frustum task list if there's no frustum tree task running
        if (this.globalTaskTree != null) {
            var frustumTaskListPending = false;
            for (var task : this.pendingTasks) {
                if (task instanceof CullTask<?> cullTask && cullTask.getCullType() == CullType.FRUSTUM ||
                        task instanceof FrustumTaskCollectionTask) {
                    frustumTaskListPending = true;
                    break;
                }
            }
            if (!frustumTaskListPending) {
                var searchDistance = this.getSearchDistance();
                var task = new FrustumTaskCollectionTask(viewport, searchDistance, this.frame, this.sectionByPosition, this.globalTaskTree);
                task.submitTo(this.asyncCullExecutor);
                this.pendingTasks.add(task);
            }
        }

        // pick the narrowest up-to-date tree, if this tree is insufficiently up to date we would've switched to sync bfs earlier
        SectionTree bestTree = null;
        for (var type : CullType.NARROW_TO_WIDE) {
            var tree = this.trees.get(type);
            if (tree != null && (bestTree == null || tree.getFrame() > bestTree.getFrame())) {
                bestTree = tree;
            }
        }

        this.needsRenderListUpdate = false;

        // wait if there's no current tree (first frames after initial load/reload)
        if (bestTree == null) {
            bestTree = this.unpackTaskResults(true);

            if (bestTree == null) {
                throw new IllegalStateException("Unpacked tree was not valid but a tree is required to render.");
            }
        }

        var visibleCollector = new VisibleChunkCollectorAsync(this.regions, this.frame);
        bestTree.traverseVisible(visibleCollector, viewport, this.getSearchDistance());
        this.renderLists = visibleCollector.createRenderLists();

        this.renderTree = bestTree;
    }

    public void markGraphDirty() {
        this.needsGraphUpdate = true;
    }

    public void markRenderListDirty() {
        this.needsRenderListUpdate = true;
    }

    public boolean needsUpdate() {
        return this.needsGraphUpdate;
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
                .isSolidRender()) {
            useOcclusionCulling = false;
        } else {
            useOcclusionCulling = Minecraft.getInstance().smartCull;
        }
        return useOcclusionCulling;
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
            renderSection.setPendingUpdate(ChunkUpdateType.INITIAL_BUILD, this.lastFrameAtTime);
        }

        this.connectNeighborNodes(renderSection);

        // force update to schedule build task
        this.markGraphDirty();
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
        this.markGraphDirty();
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
                var sprites = region.getAnimatedSprites(iterator.nextByteAsInt());

                if (sprites == null) {
                    continue;
                }

                for (TextureAtlasSprite sprite : sprites) {
                    SpriteUtil.markSpriteActive(sprite);
                }
            }
        }
    }

    public boolean isBoxVisible(double x1, double y1, double z1, double x2, double y2, double z2) {
        return this.renderTree == null || this.renderTree.isBoxVisible(x1, y1, z1, x2, y2, z2);
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
        if (this.processChunkBuildResults(results)) {
            this.markGraphDirty();
        }

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
                    && sortOutput.getTopoSorter() != null
                    && result.render.getTranslucentData() instanceof DynamicTopoData data) {
                this.sortTriggering.applyTriggerChanges(data, sortOutput.getTopoSorter(), result.render.getPosition(), this.cameraPosition);
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
                    this.builder.getTotalRemainingBudget(),
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
        for (var deferMode : DeferMode.values()) {
            var collector = switch (deferMode) {
                case ZERO_FRAMES -> importantCollector;
                case ONE_FRAME -> semiImportantCollector;
                case ALWAYS -> deferredCollector;
            };

            submitSectionTasks(collector, deferMode);
        }
    }

    private void submitSectionTasks(ChunkJobCollector collector, DeferMode deferMode) {
        LongHeapPriorityQueue frustumQueue = null;
        LongHeapPriorityQueue globalQueue = null;
        float frustumPriorityBias = 0;
        float globalPriorityBias = 0;
        if (this.frustumTaskLists != null) {
            frustumQueue = this.frustumTaskLists.pendingTasks.get(deferMode);
        }
        if (frustumQueue != null) {
            frustumPriorityBias = this.frustumTaskLists.getCollectorPriorityBias(this.lastFrameAtTime);
        } else {
            frustumQueue = new LongHeapPriorityQueue();
        }

        if (this.globalTaskLists != null) {
            globalQueue = this.globalTaskLists.pendingTasks.get(deferMode);
        }
        if (globalQueue != null) {
            globalPriorityBias = this.globalTaskLists.getCollectorPriorityBias(this.lastFrameAtTime);
        } else {
            globalQueue = new LongHeapPriorityQueue();
        }

        float frustumPriority = Float.POSITIVE_INFINITY;
        float globalPriority = Float.POSITIVE_INFINITY;
        long frustumItem = 0;
        long globalItem = 0;

        while ((!frustumQueue.isEmpty() || !globalQueue.isEmpty()) && collector.hasBudgetRemaining()) {
            // get the first item from the non-empty queues and see which one has higher priority.
            // if the priority is not infinity, then the item priority was fetched the last iteration and doesn't need updating.
            if (!frustumQueue.isEmpty() && Float.isInfinite(frustumPriority)) {
                frustumItem = frustumQueue.firstLong();
                frustumPriority = PendingTaskCollector.decodePriority(frustumItem) + frustumPriorityBias;
            }
            if (!globalQueue.isEmpty() && Float.isInfinite(globalPriority)) {
                globalItem = globalQueue.firstLong();
                globalPriority = PendingTaskCollector.decodePriority(globalItem) + globalPriorityBias;
            }

            RenderSection section;
            if (frustumPriority < globalPriority) {
                frustumQueue.dequeueLong();
                frustumPriority = Float.POSITIVE_INFINITY;

                section = this.frustumTaskLists.decodeAndFetchSection(this.sectionByPosition, frustumItem);
            } else {
                globalQueue.dequeueLong();
                globalPriority = Float.POSITIVE_INFINITY;

                section = this.globalTaskLists.decodeAndFetchSection(this.sectionByPosition, globalItem);
            }

            if (section == null || section.isDisposed()) {
                continue;
            }

            // stop if the section doesn't need an update anymore
            // TODO: check for section having a running task?
            var type = section.getPendingUpdate();
            if (type == null) {
                continue;
            }

            int frame = this.frame;
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
            section.clearPendingUpdate();
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

    public ChunkBuilder getBuilder() {
        return this.builder;
    }

    public void destroy() {
        this.builder.shutdown(); // stop all the workers, and cancel any tasks

        this.asyncCullExecutor.shutdownNow();

        for (var result : this.collectChunkBuildResults()) {
            result.destroy(); // delete resources for any pending tasks (including those that were cancelled)
        }

        for (var section : this.sectionByPosition.values()) {
            section.delete();
        }

        this.sectionsWithGlobalEntities.clear();

        this.renderLists = SortedRenderLists.empty();

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
                section.setPendingUpdate(pendingUpdate, this.lastFrameAtTime);
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
                section.setPendingUpdate(pendingUpdate, this.lastFrameAtTime);

                // force update to schedule rebuild task on this section
                this.markGraphDirty();
            }
        }
    }

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

        long deviceUsed = 0;
        long deviceAllocated = 0;

        for (var region : this.regions.getLoadedRegions()) {
            var resources = region.getResources();

            if (resources == null) {
                continue;
            }

            var buffer = resources.getGeometryArena();

            deviceUsed += buffer.getDeviceUsedMemory();
            deviceAllocated += buffer.getDeviceAllocatedMemory();

            count++;
        }

        list.add(String.format("Geometry Pool: %d/%d MiB (%d buffers)", MathUtil.toMib(deviceUsed), MathUtil.toMib(deviceAllocated), count));
        list.add(String.format("Transfer Queue: %s", this.regions.getStagingBuffer().toString()));

        list.add(String.format("Chunk Builder: Permits=%02d (E %03d) | Busy=%02d | Total=%02d",
                this.builder.getScheduledJobCount(), this.builder.getScheduledEffort(), this.builder.getBusyThreadCount(), this.builder.getTotalThreadCount())
        );

//        list.add(String.format("Chunk Queues: U=%02d (P0=%03d | P1=%03d | P2=%03d)",
//                this.buildResults.size(),
//                this.frustumTaskLists.get(ChunkUpdateType.IMPORTANT_REBUILD).size() + this.frustumTaskLists.get(ChunkUpdateType.IMPORTANT_SORT).size(),
//                this.frustumTaskLists.get(ChunkUpdateType.REBUILD).size() + this.frustumTaskLists.get(ChunkUpdateType.SORT).size(),
//                this.frustumTaskLists.get(ChunkUpdateType.INITIAL_BUILD).size())
//        );

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
