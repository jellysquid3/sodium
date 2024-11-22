package net.caffeinemc.mods.sodium.client.render.chunk.compile.executor;

import net.caffeinemc.mods.sodium.client.render.chunk.compile.estimation.JobEffort;
import net.minecraft.ReportedException;

public class ChunkJobResult<OUTPUT> {
    private final OUTPUT output;
    private final Throwable throwable;
    private final JobEffort jobEffort;

    private ChunkJobResult(OUTPUT output, Throwable throwable, JobEffort jobEffort) {
        this.output = output;
        this.throwable = throwable;
        this.jobEffort = jobEffort;
    }

    public static <OUTPUT> ChunkJobResult<OUTPUT> exceptionally(Throwable throwable) {
        return new ChunkJobResult<>(null, throwable, null);
    }

    public static <OUTPUT> ChunkJobResult<OUTPUT> successfully(OUTPUT output, JobEffort jobEffort) {
        return new ChunkJobResult<>(output, null, jobEffort);
    }

    public static <OUTPUT> ChunkJobResult<OUTPUT> successfully(OUTPUT output) {
        return new ChunkJobResult<>(output, null, null);
    }

    public OUTPUT unwrap() {
        if (this.throwable instanceof ReportedException exception) {
            // Propagate ReportedExceptions directly to provide extra information
            throw exception;
        } else if (this.throwable != null) {
            throw new RuntimeException("Exception thrown while executing job", this.throwable);
        }

        return this.output;
    }

    public JobEffort getJobEffort() {
        return this.jobEffort;
    }
}
