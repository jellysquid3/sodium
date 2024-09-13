package net.caffeinemc.mods.sodium.client.render.chunk.compile.executor;

import net.caffeinemc.mods.sodium.client.render.chunk.compile.BuilderTaskOutput;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.Semaphore;
import java.util.function.Consumer;

public class ChunkJobCollector {
    private final Semaphore semaphore = new Semaphore(0);
    private final Consumer<ChunkJobResult<? extends BuilderTaskOutput>> collector;
    private final List<ChunkJob> submitted = new ArrayList<>();

    private int budget;

    public ChunkJobCollector(Consumer<ChunkJobResult<? extends BuilderTaskOutput>> collector) {
        this.budget = Integer.MAX_VALUE;
        this.collector = collector;
    }

    public ChunkJobCollector(int budget, Consumer<ChunkJobResult<? extends BuilderTaskOutput>> collector) {
        this.budget = budget;
        this.collector = collector;
    }

    public void onJobFinished(ChunkJobResult<? extends BuilderTaskOutput> result) {
        this.semaphore.release(1);
        this.collector.accept(result);
    }

    public void awaitCompletion(ChunkBuilder builder) {
        if (this.submitted.isEmpty()) {
            return;
        }

        for (var job : this.submitted) {
            if (job.isStarted() || job.isCancelled()) {
                continue;
            }

            builder.tryStealTask(job);
        }

        this.semaphore.acquireUninterruptibly(this.submitted.size());
    }

    public void addSubmittedJob(ChunkJob job) {
        this.submitted.add(job);
        this.budget -= job.getEffort();
    }

    public boolean hasBudgetRemaining() {
        return this.budget > 0;
    }
}
