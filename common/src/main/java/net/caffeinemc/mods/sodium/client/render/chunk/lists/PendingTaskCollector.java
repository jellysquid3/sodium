package net.caffeinemc.mods.sodium.client.render.chunk.lists;

import it.unimi.dsi.fastutil.longs.LongArrayList;
import it.unimi.dsi.fastutil.longs.LongHeapPriorityQueue;
import net.caffeinemc.mods.sodium.client.render.chunk.ChunkUpdateType;
import net.caffeinemc.mods.sodium.client.render.chunk.DeferMode;
import net.caffeinemc.mods.sodium.client.render.chunk.RenderSection;
import net.caffeinemc.mods.sodium.client.render.chunk.occlusion.OcclusionCuller;
import net.caffeinemc.mods.sodium.client.render.viewport.Viewport;
import net.caffeinemc.mods.sodium.client.util.MathUtil;
import net.minecraft.util.Mth;

public class PendingTaskCollector implements OcclusionCuller.GraphOcclusionVisitor {
    public static final int SECTION_Y_MIN = -128; // used instead of baseOffsetY to accommodate all permissible y values (-2048 to 2048 blocks)

    // tunable parameters for the priority calculation.
    // each "gained" point means a reduction in the final priority score (lowest score processed first)
    static final float PENDING_TIME_FACTOR = -1.0f / 500_000_000.0f; // 1 point gained per 500ms
    static final float WITHIN_FRUSTUM_BIAS = -3.0f; // points for being within the frustum
    static final float PROXIMITY_FACTOR = 3.0f; // penalty for being far away
    static final float CLOSE_DISTANCE = 50.0f; // distance at which another proximity bonus is applied
    static final float CLOSE_PROXIMITY_FACTOR = 0.6f; // penalty for being CLOSE_DISTANCE or farther away
    static final float INV_MAX_DISTANCE_CLOSE = CLOSE_PROXIMITY_FACTOR / CLOSE_DISTANCE;

    private final LongArrayList[] pendingTasks = new LongArrayList[DeferMode.values().length];

    protected final boolean isFrustumTested;
    protected final int baseOffsetX, baseOffsetY, baseOffsetZ;

    protected final int cameraX, cameraY, cameraZ;
    private final float invMaxDistance;
    private final long creationTime;

    public PendingTaskCollector(Viewport viewport, float buildDistance, boolean frustumTested) {
        this.creationTime = System.nanoTime();
        this.isFrustumTested = frustumTested;
        var offsetDistance = Mth.ceil(buildDistance / 16.0f) + 1;

        // the offset applied to section coordinates to encode their position in the octree
        var sectionPos = viewport.getChunkCoord();
        var cameraSectionX = sectionPos.getX();
        var cameraSectionY = sectionPos.getY();
        var cameraSectionZ = sectionPos.getZ();
        this.baseOffsetX = cameraSectionX - offsetDistance;
        this.baseOffsetY = cameraSectionY - offsetDistance;
        this.baseOffsetZ = cameraSectionZ - offsetDistance;

        this.invMaxDistance = PROXIMITY_FACTOR / buildDistance;

        if (frustumTested) {
            var blockPos = viewport.getBlockCoord();
            this.cameraX = blockPos.getX();
            this.cameraY = blockPos.getY();
            this.cameraZ = blockPos.getZ();
        } else {
            this.cameraX = (cameraSectionX << 4);
            this.cameraY = (cameraSectionY << 4);
            this.cameraZ = (cameraSectionZ << 4);
        }
    }

    @Override
    public void visit(RenderSection section) {
        this.checkForTask(section);
    }

    protected void checkForTask(RenderSection section) {
        ChunkUpdateType type = section.getPendingUpdate();

        if (type != null && section.getTaskCancellationToken() == null) {
            this.addPendingSection(section, type);
        }
    }

    protected void addPendingSection(RenderSection section, ChunkUpdateType type) {
        // start with a base priority value, lowest priority of task gets processed first
        float priority = getSectionPriority(section, type);

        // encode the absolute position of the section
        var localX = section.getChunkX() - this.baseOffsetX;
        var localY = section.getChunkY() - SECTION_Y_MIN;
        var localZ = section.getChunkZ() - this.baseOffsetZ;
        long taskCoordinate = (long) (localX & 0b1111111111) << 20 | (long) (localY & 0b1111111111) << 10 | (long) (localZ & 0b1111111111);

        var queue = this.pendingTasks[type.getDeferMode().ordinal()];
        if (queue == null) {
            queue = new LongArrayList();
            this.pendingTasks[type.getDeferMode().ordinal()] = queue;
        }

        // encode the priority and the section position into a single long such that all parts can be later decoded
        queue.add((long) MathUtil.floatToComparableInt(priority) << 32 | taskCoordinate);
    }

    private float getSectionPriority(RenderSection section, ChunkUpdateType type) {
        float priority = type.getPriorityValue();

        // calculate the relative distance to the camera
        // alternatively: var distance = deltaX + deltaY + deltaZ;
        var deltaX = Math.abs(section.getCenterX() - this.cameraX);
        var deltaY = Math.abs(section.getCenterY() - this.cameraY);
        var deltaZ = Math.abs(section.getCenterZ() - this.cameraZ);
        var distance = (float) Math.sqrt(deltaX * deltaX + deltaY * deltaY + deltaZ * deltaZ);
        priority += distance * this.invMaxDistance; // distance / maxDistance * PROXIMITY_FACTOR
        priority += Math.max(distance, CLOSE_DISTANCE) * INV_MAX_DISTANCE_CLOSE;

        // tasks that have been waiting for longer are more urgent
        var taskPendingTimeNanos = this.creationTime - section.getPendingUpdateSince();
        priority += taskPendingTimeNanos * PENDING_TIME_FACTOR; // upgraded by one point every second

        // explain how priority was calculated
//        System.out.println("Priority " + priority + " from: distance " + distance + " = " + (distance * this.invMaxDistance) +
//                ", time " + taskPendingTimeNanos + " = " + (taskPendingTimeNanos * PENDING_TIME_FACTOR) +
//                ", type " + type + " = " + type.getPriorityValue() +
//                ", frustum " + this.isFrustumTested + " = " + (this.isFrustumTested ? WITHIN_FRUSTUM_BIAS : 0));

        return priority;
    }

    public static float decodePriority(long encoded) {
        return MathUtil.comparableIntToFloat((int) (encoded >>> 32));
    }

    public TaskListCollection getPendingTaskLists() {
        var result = new TaskListCollection(DeferMode.class, this.creationTime, this.isFrustumTested, this.baseOffsetX, this.baseOffsetZ);

        for (var mode : DeferMode.values()) {
            var list = this.pendingTasks[mode.ordinal()];
            if (list != null) {
                var queue = new LongHeapPriorityQueue(list.elements(), list.size());
                result.put(mode, queue);
            }
        }

        return result;
    }

}
