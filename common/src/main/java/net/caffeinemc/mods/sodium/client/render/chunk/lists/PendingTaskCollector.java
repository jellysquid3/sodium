package net.caffeinemc.mods.sodium.client.render.chunk.lists;

import it.unimi.dsi.fastutil.longs.Long2ReferenceMap;
import it.unimi.dsi.fastutil.longs.LongArrayList;
import it.unimi.dsi.fastutil.longs.LongHeapPriorityQueue;
import net.caffeinemc.mods.sodium.client.render.chunk.ChunkUpdateType;
import net.caffeinemc.mods.sodium.client.render.chunk.DeferMode;
import net.caffeinemc.mods.sodium.client.render.chunk.RenderSection;
import net.caffeinemc.mods.sodium.client.render.chunk.occlusion.OcclusionCuller;
import net.caffeinemc.mods.sodium.client.render.viewport.Viewport;
import net.caffeinemc.mods.sodium.client.util.MathUtil;
import net.minecraft.core.SectionPos;
import net.minecraft.util.Mth;

import java.util.EnumMap;
import java.util.Map;

/*
TODO:
- check if there's also bumps in the fps when crossing chunk borders on dev
- tune priority values, test frustum effect by giving it a large value
- experiment with non-linear distance scaling (if < some radius, bonus priority for being close)
 */
public class PendingTaskCollector implements OcclusionCuller.GraphOcclusionVisitor {
    public static final int SECTION_Y_MIN = -128; // used instead of baseOffsetY to accommodate all permissible y values (-2048 to 2048 blocks)

    // tunable parameters for the priority calculation.
    // each "gained" point means a reduction in the final priority score (lowest score processed first)
    private static final float PENDING_TIME_FACTOR = -1.0f / 500_000_000.0f; // 1 point gained per 500ms
    private static final float WITHIN_FRUSTUM_BIAS = -3.0f; // points for being within the frustum
    private static final float PROXIMITY_FACTOR = 3.0f; // penalty for being far away
    private static final float CLOSE_DISTANCE = 50.0f; // distance at which another proximity bonus is applied
    private static final float CLOSE_PROXIMITY_FACTOR = 0.6f; // penalty for being CLOSE_DISTANCE or farther away
    private static final float INV_MAX_DISTANCE_CLOSE = CLOSE_PROXIMITY_FACTOR / CLOSE_DISTANCE;

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

        var transform = viewport.getTransform();

        // the offset applied to section coordinates to encode their position in the octree
        var cameraSectionX = transform.intX >> 4;
        var cameraSectionY = transform.intY >> 4;
        var cameraSectionZ = transform.intZ >> 4;
        this.baseOffsetX = cameraSectionX - offsetDistance;
        this.baseOffsetY = cameraSectionY - offsetDistance;
        this.baseOffsetZ = cameraSectionZ - offsetDistance;

        this.invMaxDistance = PROXIMITY_FACTOR / buildDistance;

        if (frustumTested) {
            this.cameraX = transform.intX;
            this.cameraY = transform.intY;
            this.cameraZ = transform.intZ;
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
        var result = new EnumMap<DeferMode, LongHeapPriorityQueue>(DeferMode.class);

        for (var mode : DeferMode.values()) {
            var list = this.pendingTasks[mode.ordinal()];
            if (list != null) {
                var queue = new LongHeapPriorityQueue(list.elements(), list.size());
                result.put(mode, queue);
            }
        }

        return new TaskListCollection(result);
    }

    public class TaskListCollection {
        public final Map<DeferMode, LongHeapPriorityQueue> pendingTasks;

        public TaskListCollection(Map<DeferMode, LongHeapPriorityQueue> pendingTasks) {
            this.pendingTasks = pendingTasks;
        }

        public float getCollectorPriorityBias(long now) {
            // compensate for creation time of the list and whether the sections are in the frustum
            return (now - PendingTaskCollector.this.creationTime) * PENDING_TIME_FACTOR +
                    (PendingTaskCollector.this.isFrustumTested ? WITHIN_FRUSTUM_BIAS : 0);
        }

        public RenderSection decodeAndFetchSection(Long2ReferenceMap<RenderSection> sectionByPosition, long encoded) {
            var localX = (int) (encoded >>> 20) & 0b1111111111;
            var localY = (int) (encoded >>> 10) & 0b1111111111;
            var localZ = (int) (encoded & 0b1111111111);

            var globalX = localX + PendingTaskCollector.this.baseOffsetX;
            var globalY = localY + SECTION_Y_MIN;
            var globalZ = localZ + PendingTaskCollector.this.baseOffsetZ;

            return sectionByPosition.get(SectionPos.asLong(globalX, globalY, globalZ));
        }
    }
}
