package net.caffeinemc.mods.sodium.client.render.chunk.compile.executor;

import net.caffeinemc.mods.sodium.client.render.chunk.compile.ChunkBuildContext;
import net.caffeinemc.mods.sodium.client.util.task.CancellationToken;

public interface ChunkJob extends CancellationToken {
    void execute(ChunkBuildContext context);

    boolean isStarted();

    long getEstimatedDuration();
}
