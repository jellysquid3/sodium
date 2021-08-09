package me.jellysquid.mods.sodium.client.level;

import it.unimi.dsi.fastutil.longs.LongCollection;

public interface ClientChunkManagerExtended {
    // TODO: allow multiple listeners to be added?
    void setListener(ChunkStatusListener listener);

    LongCollection getLoadedChunks();
}
