package me.jellysquid.mods.sodium.client.level;

/**
 * Defines a listener that can be attached to a world's chunk manager to receive chunk load and unload events.
 */
public interface ChunkStatusListener {
    /**
     * Called after a chunk is added to the world and loaded.
     * @param x The x-coordinate of the loaded chunk
     * @param z The z-coordinate of the loaded chunk
     */
    void onChunkAdded(int x, int z);

    /**
     * Called after a chunk is removed from the world and unloaded.
     * @param x The x-coordiante of the unloaded chunk
     * @param z The z-coordinate of the unloaded chunk
     */
    void onChunkRemoved(int x, int z);
}
