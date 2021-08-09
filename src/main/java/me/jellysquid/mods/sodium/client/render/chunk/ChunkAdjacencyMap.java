package me.jellysquid.mods.sodium.client.render.chunk;

import it.unimi.dsi.fastutil.longs.*;
import net.minecraft.world.level.ChunkPos;

public class ChunkAdjacencyMap {
    private final Long2ReferenceMap<LongSet> map = new Long2ReferenceOpenHashMap<>();

    public boolean hasNeighbors(int x, int z) {
        LongSet set = this.map.get(ChunkPos.asLong(x, z));

        if (set == null) {
            return false;
        }

        return set.size() >= 9;
    }
    public void onChunkLoaded(int x, int z) {
        long pos = ChunkPos.asLong(x, z);

        for (int xd = -1; xd <= 1; xd++) {
            for (int zd = -1; zd <= 1; zd++) {
                this.add(x + xd, z + zd, pos);
            }
        }
    }

    public void onChunkUnloaded(int x, int z) {
        long pos = ChunkPos.asLong(x, z);

        for (int xd = -1; xd <= 1; xd++) {
            for (int zd = -1; zd <= 1; zd++) {
                this.remove(x + xd, z + zd, pos);
            }
        }
    }

    private void add(int x, int z, long pos) {
        long key = ChunkPos.asLong(x, z);
        LongSet set = this.map.get(key);

        if (set == null) {
            this.map.put(key, set = new LongArraySet(8));
        }

        if (!set.add(pos)) {
            throw new IllegalStateException();
        }
    }

    private void remove(int x, int z, long pos) {
        long key = ChunkPos.asLong(x, z);
        LongSet set = this.map.get(key);

        if (set == null) {
            throw new NullPointerException();
        }

        if (!set.remove(pos)) {
            throw new IllegalStateException();
        }

        if (set.isEmpty()) {
            this.map.remove(key);
        }
    }
}
