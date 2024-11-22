package net.caffeinemc.mods.sodium.client.render.chunk.compile.executor;

import net.caffeinemc.mods.sodium.client.render.chunk.compile.BuilderTaskOutput;
import net.caffeinemc.mods.sodium.client.render.chunk.compile.ChunkBuildContext;
import net.caffeinemc.mods.sodium.client.render.chunk.compile.estimation.JobEffort;
import net.caffeinemc.mods.sodium.client.render.chunk.compile.tasks.ChunkBuilderTask;

import java.util.function.Consumer;

public class ChunkJobTyped<TASK extends ChunkBuilderTask<OUTPUT>, OUTPUT extends BuilderTaskOutput>
        implements ChunkJob
{
    private final TASK task;
    private final Consumer<ChunkJobResult<OUTPUT>> consumer;

    private volatile boolean cancelled;
    private volatile boolean started;

    ChunkJobTyped(TASK task, Consumer<ChunkJobResult<OUTPUT>> consumer) {
        this.task = task;
        this.consumer = consumer;
    }

    @Override
    public boolean isCancelled() {
        return this.cancelled;
    }

    @Override
    public void setCancelled() {
        this.cancelled = true;
    }

    @Override
    public void execute(ChunkBuildContext context) {
        // Task was cancelled before starting
        if (this.cancelled) {
            return;
        }

        this.started = true;

        ChunkJobResult<OUTPUT> result;

        try {
            var start = System.nanoTime();
            var output = this.task.execute(context, this);

            // Task was cancelled while executing
            if (output == null) {
                return;
            }

            result = ChunkJobResult.successfully(output, JobEffort.untilNowWithEffort(this.task.getClass(), start, output.getResultSize()));
        } catch (Throwable throwable) {
            result = ChunkJobResult.exceptionally(throwable);
            ChunkBuilder.LOGGER.error("Chunk build failed", throwable);
        }

        try {
            this.consumer.accept(result);
        } catch (Throwable throwable) {
            throw new RuntimeException("Exception while consuming result", throwable);
        }
    }

    @Override
    public boolean isStarted() {
        return this.started;
    }

    @Override
    public long getEstimatedSize() {
        return this.task.getEstimatedSize();
    }

    @Override
    public long getEstimatedDuration() {
        return this.task.getEstimatedDuration();
    }
}
