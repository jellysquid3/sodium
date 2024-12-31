package net.caffeinemc.mods.sodium.client.render.chunk;

/**
 * NOTE: Update types with not-ALWAYS defer mode should be prefixed "important".
 */
public enum ChunkUpdateType {
    SORT(DeferMode.ALWAYS, 2),
    INITIAL_BUILD(DeferMode.ALWAYS, 0),
    REBUILD(DeferMode.ALWAYS, 1),
    IMPORTANT_REBUILD(DeferMode.ONE_FRAME, 1),
    IMPORTANT_SORT(DeferMode.ZERO_FRAMES, 2);

    private final DeferMode deferMode;
    private final float priorityValue;

    ChunkUpdateType(DeferMode deferMode, float priorityValue) {
        this.deferMode = deferMode;
        this.priorityValue = priorityValue;
    }

    public static ChunkUpdateType getPromotionUpdateType(ChunkUpdateType prev, ChunkUpdateType next) {
        if (prev == null || prev == SORT || prev == next) {
            return next;
        }
        if (next == IMPORTANT_REBUILD
                || (prev == IMPORTANT_SORT && next == REBUILD)
                || (prev == REBUILD && next == IMPORTANT_SORT)) {
            return IMPORTANT_REBUILD;
        }
        return null;
    }

    public boolean isImportant() {
        return this.deferMode != DeferMode.ALWAYS;
    }

    public DeferMode getDeferMode() {
        return this.deferMode;
    }

    public float getPriorityValue() {
        return this.priorityValue;
    }
}
