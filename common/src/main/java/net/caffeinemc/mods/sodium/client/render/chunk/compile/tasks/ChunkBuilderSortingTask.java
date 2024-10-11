package net.caffeinemc.mods.sodium.client.render.chunk.compile.tasks;

import net.caffeinemc.mods.sodium.client.render.chunk.translucent_sorting.data.DynamicSorter;
import net.minecraft.util.profiling.Profiler;
import net.minecraft.util.profiling.ProfilerFiller;
import org.joml.Vector3dc;

import net.caffeinemc.mods.sodium.client.render.chunk.RenderSection;
import net.caffeinemc.mods.sodium.client.render.chunk.compile.ChunkBuildContext;
import net.caffeinemc.mods.sodium.client.render.chunk.compile.ChunkSortOutput;
import net.caffeinemc.mods.sodium.client.render.chunk.translucent_sorting.data.DynamicData;
import net.caffeinemc.mods.sodium.client.util.task.CancellationToken;

public class ChunkBuilderSortingTask extends ChunkBuilderTask<ChunkSortOutput> {
    private final DynamicSorter sorter;

    public ChunkBuilderSortingTask(RenderSection render, int frame, Vector3dc absoluteCameraPos, DynamicSorter sorter) {
        super(render, frame, absoluteCameraPos);
        this.sorter = sorter;
    }

    @Override
    public ChunkSortOutput execute(ChunkBuildContext context, CancellationToken cancellationToken) {
        if (cancellationToken.isCancelled()) {
            return null;
        }

        ProfilerFiller profiler = Profiler.get();
        profiler.push("translucency sorting");

        this.sorter.writeIndexBuffer(this, false);

        profiler.pop();
        return new ChunkSortOutput(this.render, this.submitTime, this.sorter);
    }

    public static ChunkBuilderSortingTask createTask(RenderSection render, int frame, Vector3dc absoluteCameraPos) {
        if (render.getTranslucentData() instanceof DynamicData dynamicData) {
            return new ChunkBuilderSortingTask(render, frame, absoluteCameraPos, dynamicData.getSorter());
        }
        return null;
    }

    @Override
    public long getEffort() {
        return this.sorter.getQuadCount();
    }
}
