package net.caffeinemc.mods.sodium.client.render.chunk.region;

import it.unimi.dsi.fastutil.longs.Long2ReferenceOpenHashMap;
import it.unimi.dsi.fastutil.objects.Reference2ReferenceMap;
import it.unimi.dsi.fastutil.objects.Reference2ReferenceOpenHashMap;
import net.caffeinemc.mods.sodium.client.SodiumClientMod;
import net.caffeinemc.mods.sodium.client.gl.arena.PendingUpload;
import net.caffeinemc.mods.sodium.client.gl.arena.staging.FallbackStagingBuffer;
import net.caffeinemc.mods.sodium.client.gl.arena.staging.MappedStagingBuffer;
import net.caffeinemc.mods.sodium.client.gl.arena.staging.StagingBuffer;
import net.caffeinemc.mods.sodium.client.gl.device.CommandList;
import net.caffeinemc.mods.sodium.client.gl.device.RenderDevice;
import net.caffeinemc.mods.sodium.client.render.chunk.RenderSection;
import net.caffeinemc.mods.sodium.client.render.chunk.compile.BuilderTaskOutput;
import net.caffeinemc.mods.sodium.client.render.chunk.compile.ChunkBuildOutput;
import net.caffeinemc.mods.sodium.client.render.chunk.compile.ChunkSortOutput;
import net.caffeinemc.mods.sodium.client.render.chunk.data.BuiltSectionMeshParts;
import net.caffeinemc.mods.sodium.client.render.chunk.terrain.DefaultTerrainRenderPasses;
import net.caffeinemc.mods.sodium.client.render.chunk.terrain.TerrainRenderPass;

import net.minecraft.util.profiling.Profiler;
import net.minecraft.util.profiling.ProfilerFiller;
import net.caffeinemc.mods.sodium.client.render.chunk.translucent_sorting.data.SharedIndexSorter;
import org.jetbrains.annotations.NotNull;

import java.util.*;

public class RenderRegionManager {
    private final Long2ReferenceOpenHashMap<RenderRegion> regions = new Long2ReferenceOpenHashMap<>();

    private final StagingBuffer stagingBuffer;

    public RenderRegionManager(CommandList commandList) {
        this.stagingBuffer = createStagingBuffer(commandList);
    }

    public void update() {
        this.stagingBuffer.flip();

        try (CommandList commandList = RenderDevice.INSTANCE.createCommandList()) {
            Iterator<RenderRegion> it = this.regions.values()
                    .iterator();

            while (it.hasNext()) {
                RenderRegion region = it.next();
                region.update(commandList);

                if (region.isEmpty()) {
                    region.delete(commandList);

                    it.remove();
                }
            }
        }
    }

    public void uploadResults(CommandList commandList, Collection<BuilderTaskOutput> results) {
        for (var entry : this.createMeshUploadQueues(results)) {
            this.uploadResults(commandList, entry.getKey(), entry.getValue());
        }
    }

    private void uploadResults(CommandList commandList, RenderRegion region, Collection<BuilderTaskOutput> results) {
        var uploads = new ArrayList<PendingSectionMeshUpload>();
        var indexUploads = new ArrayList<PendingSectionIndexBufferUpload>();

        for (BuilderTaskOutput result : results) {
            int renderSectionIndex = result.render.getSectionIndex();

            if (result.render.isDisposed()) {
                throw new IllegalStateException("Render section is disposed");
            }

            if (result instanceof ChunkBuildOutput chunkBuildOutput) {
                for (TerrainRenderPass pass : DefaultTerrainRenderPasses.ALL) {
                    var storage = region.getStorage(pass);

                    if (storage != null) {
                        storage.removeVertexData(renderSectionIndex);
                    }

                    BuiltSectionMeshParts mesh = chunkBuildOutput.getMesh(pass);

                    if (mesh != null) {
                        uploads.add(new PendingSectionMeshUpload(result.render, mesh, pass,
                                new PendingUpload(mesh.getVertexData())));
                    }
                }
            }

            if (result instanceof ChunkSortOutput indexDataOutput && !indexDataOutput.isReusingUploadedIndexData()) {
                var sorter = indexDataOutput.getSorter();
                if (sorter instanceof SharedIndexSorter sharedIndexSorter) {
                    var storage = region.createStorage(DefaultTerrainRenderPasses.TRANSLUCENT);
                    storage.removeIndexData(renderSectionIndex);
                    storage.setSharedIndexUsage(renderSectionIndex, sharedIndexSorter.quadCount());
                } else {
                    var storage = region.getStorage(DefaultTerrainRenderPasses.TRANSLUCENT);
                    if (storage != null) {
                        storage.removeIndexData(renderSectionIndex);
                        storage.setSharedIndexUsage(renderSectionIndex, 0);
                    }

                    if (sorter == null) {
                        continue;
                    }
                    // when a non-present TranslucentData is used like NoData, the indexBuffer is null
                    var buffer = sorter.getIndexBuffer();
                    if (buffer == null) {
                        continue;
                    }

                    indexUploads.add(new PendingSectionIndexBufferUpload(result.render, new PendingUpload(buffer)));
                }
            }
        }

        ProfilerFiller profiler = Profiler.get();

        // If we have nothing to upload, abort!
        var translucentStorage = region.getStorage(DefaultTerrainRenderPasses.TRANSLUCENT);
        var needsSharedIndexUpdate = translucentStorage != null && translucentStorage.needsSharedIndexUpdate();
        if (uploads.isEmpty() && indexUploads.isEmpty() && !needsSharedIndexUpdate) {
            return;
        }

        var resources = region.createResources(commandList);

        profiler.push("upload_vertices");

        if (!uploads.isEmpty()) {
            var arena = resources.getGeometryArena();
            boolean bufferChanged = arena.upload(commandList, uploads.stream()
                    .map(upload -> upload.vertexUpload));

            // If any of the buffers changed, the tessellation will need to be updated
            // Once invalidated the tessellation will be re-created on the next attempted use
            if (bufferChanged) {
                region.refreshTesselation(commandList);
            }

            // Collect the upload results
            for (PendingSectionMeshUpload upload : uploads) {
                var storage = region.createStorage(upload.pass);
                storage.setVertexData(upload.section.getSectionIndex(),
                        upload.vertexUpload.getResult(), upload.meshData.getVertexSegments());
            }
        }

        profiler.popPush("upload_indices");
        var indexBufferChanged = false;

        if (!indexUploads.isEmpty()) {
            var arena = resources.getIndexArena();
            indexBufferChanged = arena.upload(commandList, indexUploads.stream()
                    .map(upload -> upload.indexBufferUpload));

            for (PendingSectionIndexBufferUpload upload : indexUploads) {
                var storage = region.createStorage(DefaultTerrainRenderPasses.TRANSLUCENT);
                storage.setIndexData(upload.section.getSectionIndex(), upload.indexBufferUpload.getResult());
            }
        }

        if (needsSharedIndexUpdate) {
            indexBufferChanged |= translucentStorage.updateSharedIndexData(commandList, resources.getIndexArena());
        }

        if (indexBufferChanged) {
            region.refreshIndexedTesselation(commandList);
        }

        profiler.pop();
    }

    private Reference2ReferenceMap.FastEntrySet<RenderRegion, List<BuilderTaskOutput>> createMeshUploadQueues(Collection<BuilderTaskOutput> results) {
        var map = new Reference2ReferenceOpenHashMap<RenderRegion, List<BuilderTaskOutput>>();

        for (var result : results) {
            var queue = map.computeIfAbsent(result.render.getRegion(), k -> new ArrayList<>());
            queue.add(result);
        }

        return map.reference2ReferenceEntrySet();
    }

    public void delete(CommandList commandList) {
        for (RenderRegion region : this.regions.values()) {
            region.delete(commandList);
        }

        this.regions.clear();
        this.stagingBuffer.delete(commandList);
    }

    public Collection<RenderRegion> getLoadedRegions() {
        return this.regions.values();
    }

    public StagingBuffer getStagingBuffer() {
        return this.stagingBuffer;
    }

    public RenderRegion createForChunk(int chunkX, int chunkY, int chunkZ) {
        return this.create(chunkX >> RenderRegion.REGION_WIDTH_SH,
                chunkY >> RenderRegion.REGION_HEIGHT_SH,
                chunkZ >> RenderRegion.REGION_LENGTH_SH);
    }

    @NotNull
    private RenderRegion create(int x, int y, int z) {
        var key = RenderRegion.key(x, y, z);
        var instance = this.regions.get(key);

        if (instance == null) {
            this.regions.put(key, instance = new RenderRegion(x, y, z, this.stagingBuffer));
        }

        return instance;
    }

    private record PendingSectionMeshUpload(RenderSection section, BuiltSectionMeshParts meshData, TerrainRenderPass pass, PendingUpload vertexUpload) {
    }

    private record PendingSectionIndexBufferUpload(RenderSection section, PendingUpload indexBufferUpload) {
    }

    private static StagingBuffer createStagingBuffer(CommandList commandList) {
        if (SodiumClientMod.options().advanced.useAdvancedStagingBuffers && MappedStagingBuffer.isSupported(RenderDevice.INSTANCE)) {
            return new MappedStagingBuffer(commandList);
        }

        return new FallbackStagingBuffer(commandList);
    }
}
