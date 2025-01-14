package net.caffeinemc.mods.sodium.client.render.chunk;

import org.jetbrains.annotations.NotNull;

/**
 * Important: Whether the task is scheduled immediately after its creation. Otherwise, they're scheduled through asynchronous culling that collects non-important tasks.
 * Defer mode: For important tasks, how fast they are going to be executed. One or zero frame deferral only allows one or zero frames to pass before the frame blocks on the task. Always deferral allows the task to be deferred indefinitely, but if it's important it will still be put to the front of the queue.
 */
public enum ChunkUpdateType {
    SORT(2),
    INITIAL_BUILD(0),
    REBUILD(1),
    IMPORTANT_REBUILD(DeferMode.ZERO_FRAMES, 1),
    IMPORTANT_SORT(DeferMode.ZERO_FRAMES, 2);

    private final DeferMode deferMode;
    private final boolean important;
    private final float priorityValue;

    ChunkUpdateType(float priorityValue) {
        this.deferMode = DeferMode.ALWAYS;
        this.important = false;
        this.priorityValue = priorityValue;
    }

    ChunkUpdateType(@NotNull DeferMode deferMode, float priorityValue) {
        this.deferMode = deferMode;
        this.important = true;
        this.priorityValue = priorityValue;
    }

    /**
     * Returns a promoted update type if the new update type is more important than the previous one. Nothing is returned if the update type is the same or less important.
     *
     * @param prev Previous update type
     * @param next New update type
     * @return Promoted update type or {@code null} if the update type is the same or less important
     */
    public static ChunkUpdateType getPromotedTypeChange(ChunkUpdateType prev, ChunkUpdateType next) {
        if (prev == next) {
            return null;
        }
        if (prev == null || prev == SORT || prev == INITIAL_BUILD) {
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
        return this.important;
    }

    public DeferMode getDeferMode(DeferMode importantRebuildDeferMode) {
        return this == IMPORTANT_REBUILD ? importantRebuildDeferMode : this.deferMode;
    }

    public float getPriorityValue() {
        return this.priorityValue;
    }
}
