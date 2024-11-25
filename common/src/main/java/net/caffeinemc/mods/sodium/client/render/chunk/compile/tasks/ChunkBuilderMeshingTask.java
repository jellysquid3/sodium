package net.caffeinemc.mods.sodium.client.render.chunk.compile.tasks;

import it.unimi.dsi.fastutil.objects.Reference2ReferenceOpenHashMap;
import net.caffeinemc.mods.sodium.client.SodiumClientMod;
import net.caffeinemc.mods.sodium.client.render.chunk.ExtendedBlockEntityType;
import net.caffeinemc.mods.sodium.client.render.chunk.DefaultChunkRenderer;
import net.caffeinemc.mods.sodium.client.render.chunk.RenderSection;
import net.caffeinemc.mods.sodium.client.render.chunk.compile.ChunkBuildBuffers;
import net.caffeinemc.mods.sodium.client.render.chunk.compile.ChunkBuildContext;
import net.caffeinemc.mods.sodium.client.render.chunk.compile.ChunkBuildOutput;
import net.caffeinemc.mods.sodium.client.render.chunk.compile.executor.ChunkBuilder;
import net.caffeinemc.mods.sodium.client.render.chunk.compile.pipeline.BlockRenderCache;
import net.caffeinemc.mods.sodium.client.render.chunk.compile.pipeline.BlockRenderer;
import net.caffeinemc.mods.sodium.client.render.chunk.data.BuiltSectionInfo;
import net.caffeinemc.mods.sodium.client.render.chunk.data.BuiltSectionMeshParts;
import net.caffeinemc.mods.sodium.client.render.chunk.terrain.DefaultTerrainRenderPasses;
import net.caffeinemc.mods.sodium.client.render.chunk.terrain.TerrainRenderPass;
import net.caffeinemc.mods.sodium.client.render.chunk.terrain.material.DefaultMaterials;
import net.caffeinemc.mods.sodium.client.render.chunk.translucent_sorting.SortBehavior;
import net.caffeinemc.mods.sodium.client.render.chunk.translucent_sorting.SortType;
import net.caffeinemc.mods.sodium.client.render.chunk.translucent_sorting.TranslucentGeometryCollector;
import net.caffeinemc.mods.sodium.client.render.chunk.translucent_sorting.data.PresentTranslucentData;
import net.caffeinemc.mods.sodium.client.render.chunk.translucent_sorting.data.TranslucentData;
import net.caffeinemc.mods.sodium.client.services.PlatformLevelRenderHooks;
import net.caffeinemc.mods.sodium.client.util.task.CancellationToken;
import net.caffeinemc.mods.sodium.client.world.LevelSlice;
import net.caffeinemc.mods.sodium.client.world.cloned.ChunkRenderContext;
import net.minecraft.CrashReport;
import net.minecraft.CrashReportCategory;
import net.minecraft.ReportedException;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.blockentity.BlockEntityRenderer;
import net.minecraft.client.renderer.chunk.VisGraph;
import net.minecraft.client.resources.model.BakedModel;
import net.minecraft.core.BlockPos;
import net.minecraft.util.profiling.Profiler;
import net.minecraft.util.profiling.ProfilerFiller;
import net.minecraft.world.level.block.RenderShape;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.material.FluidState;
import org.joml.Vector3dc;

import java.util.Map;

/**
 * Rebuilds all the meshes of a chunk for each given render pass with non-occluded blocks. The result is then uploaded
 * to graphics memory on the main thread.
 *
 * This task takes a slice of the level from the thread it is created on. Since these slices require rather large
 * array allocations, they are pooled to ensure that the garbage collector doesn't become overloaded.
 */
public class ChunkBuilderMeshingTask extends ChunkBuilderTask<ChunkBuildOutput> {
    private final ChunkRenderContext renderContext;

    public ChunkBuilderMeshingTask(RenderSection render, int buildTime, Vector3dc absoluteCameraPos, ChunkRenderContext renderContext) {
        super(render, buildTime, absoluteCameraPos);
        this.renderContext = renderContext;
    }

    @Override
    public ChunkBuildOutput execute(ChunkBuildContext buildContext, CancellationToken cancellationToken) {
        ProfilerFiller profiler = Profiler.get();
        BuiltSectionInfo.Builder renderData = new BuiltSectionInfo.Builder();
        VisGraph occluder = new VisGraph();

        ChunkBuildBuffers buffers = buildContext.buffers;
        buffers.init(renderData, this.render.getSectionIndex());

        BlockRenderCache cache = buildContext.cache;
        cache.init(this.renderContext);

        LevelSlice slice = cache.getWorldSlice();

        int minX = this.render.getOriginX();
        int minY = this.render.getOriginY();
        int minZ = this.render.getOriginZ();

        int maxX = minX + 16;
        int maxY = minY + 16;
        int maxZ = minZ + 16;

        // Initialise with minX/minY/minZ so initial getBlockState crash context is correct
        BlockPos.MutableBlockPos blockPos = new BlockPos.MutableBlockPos(minX, minY, minZ);
        BlockPos.MutableBlockPos modelOffset = new BlockPos.MutableBlockPos();

        TranslucentGeometryCollector collector;
        if (SodiumClientMod.options().performance.getSortBehavior() != SortBehavior.OFF) {
            collector = new TranslucentGeometryCollector(this.render.getPosition());
        } else {
            collector = null;
        }
        BlockRenderer blockRenderer = cache.getBlockRenderer();
        blockRenderer.prepare(buffers, slice, collector);

        profiler.push("render blocks");
        try {
            for (int y = minY; y < maxY; y++) {
                if (cancellationToken.isCancelled()) {
                    return null;
                }

                for (int z = minZ; z < maxZ; z++) {
                    for (int x = minX; x < maxX; x++) {
                        BlockState blockState = slice.getBlockState(x, y, z);

                        if (blockState.isAir() && !blockState.hasBlockEntity()) {
                            continue;
                        }

                        blockPos.set(x, y, z);
                        modelOffset.set(x & 15, y & 15, z & 15);

                        if (blockState.getRenderShape() == RenderShape.MODEL) {
                            BakedModel model = cache.getBlockModels()
                                    .getBlockModel(blockState);
                            blockRenderer.renderModel(model, blockState, blockPos, modelOffset);
                        }

                        FluidState fluidState = blockState.getFluidState();

                        if (!fluidState.isEmpty()) {
                            cache.getFluidRenderer().render(slice, blockState, fluidState, blockPos, modelOffset, collector, buffers);
                        }

                        if (blockState.hasBlockEntity()) {
                            BlockEntity entity = slice.getBlockEntity(blockPos);

                            if (entity != null && ExtendedBlockEntityType.shouldRender(entity.getType(), slice, blockPos, entity)) {
                                BlockEntityRenderer<BlockEntity> renderer = Minecraft.getInstance().getBlockEntityRenderDispatcher().getRenderer(entity);

                                if (renderer != null) {
                                    renderData.addBlockEntity(entity, !renderer.shouldRenderOffScreen(entity));
                                }
                            }
                        }

                        if (blockState.isSolidRender()) {
                            occluder.setOpaque(blockPos);
                        }
                    }
                }
            }
        } catch (ReportedException ex) {
            // Propagate existing crashes (add context)
            throw fillCrashInfo(ex.getReport(), slice, blockPos);
        } catch (Exception ex) {
            // Create a new crash report for other exceptions (e.g. thrown in getQuads)
            throw fillCrashInfo(CrashReport.forThrowable(ex, "Encountered exception while building chunk meshes"), slice, blockPos);
        }
        profiler.popPush("mesh appenders");

        PlatformLevelRenderHooks.INSTANCE.runChunkMeshAppenders(this.renderContext.getRenderers(), type -> buffers.get(DefaultMaterials.forRenderLayer(type)).asFallbackVertexConsumer(DefaultMaterials.forRenderLayer(type), collector),
                slice);

        blockRenderer.release();

        SortType sortType = SortType.NONE;
        if (collector != null) {
            sortType = collector.finishRendering();
        }

        Map<TerrainRenderPass, BuiltSectionMeshParts> meshes = new Reference2ReferenceOpenHashMap<>();
        var visibleSlices = DefaultChunkRenderer.getVisibleFaces(
                (int) this.absoluteCameraPos.x(), (int) this.absoluteCameraPos.y(), (int) this.absoluteCameraPos.z(),
                this.render.getChunkX(), this.render.getChunkY(), this.render.getChunkZ());
        profiler.popPush("meshing");

        for (TerrainRenderPass pass : DefaultTerrainRenderPasses.ALL) {
            // if the translucent geometry needs to share an index buffer between the directions,
            // consolidate all translucent geometry into UNASSIGNED
            boolean translucentBehavior = collector != null && pass.isTranslucent();
            boolean forceUnassigned = translucentBehavior && sortType.needsDirectionMixing;
            boolean sliceReordering = !translucentBehavior || sortType.allowSliceReordering;
            BuiltSectionMeshParts mesh = buffers.createMesh(pass, visibleSlices, forceUnassigned, sliceReordering);

            if (mesh != null) {
                meshes.put(pass, mesh);
                renderData.addRenderPass(pass);
            }
        }

        // cancellation opportunity right before translucent sorting
        if (cancellationToken.isCancelled()) {
            meshes.forEach((pass, mesh) -> mesh.getVertexData().free());
            profiler.pop();
            return null;
        }

        renderData.setOcclusionData(occluder.resolve());

        profiler.popPush("translucency sorting");

        boolean reuseUploadedData = false;
        TranslucentData translucentData = null;
        if (collector != null) {
            var oldData = this.render.getTranslucentData();
            translucentData = collector.getTranslucentData(
                    oldData, meshes.get(DefaultTerrainRenderPasses.TRANSLUCENT), this);
            reuseUploadedData = translucentData == oldData;
        }

        var output = new ChunkBuildOutput(this.render, this.submitTime, translucentData, renderData.build(), meshes);

        if (collector != null) {
            if (reuseUploadedData) {
                output.markAsReusingUploadedData();
            } else if (translucentData instanceof PresentTranslucentData present) {
                var sorter = present.getSorter();
                sorter.writeIndexBuffer(this, true);
                output.setSorter(sorter);
            }
        }

        profiler.pop();

        return output;
    }

    private ReportedException fillCrashInfo(CrashReport report, LevelSlice slice, BlockPos pos) {
        CrashReportCategory crashReportSection = report.addCategory("Block being rendered", 1);

        BlockState state = null;
        try {
            state = slice.getBlockState(pos);
        } catch (Exception ignored) {}
        CrashReportCategory.populateBlockDetails(crashReportSection, slice, pos, state);

        crashReportSection.setDetail("Chunk section", this.render);
        if (this.renderContext != null) {
            crashReportSection.setDetail("Render context volume", this.renderContext.getVolume());
        }

        return new ReportedException(report);
    }

    @Override
    public int getEffort() {
        return ChunkBuilder.HIGH_EFFORT;
    }
}
