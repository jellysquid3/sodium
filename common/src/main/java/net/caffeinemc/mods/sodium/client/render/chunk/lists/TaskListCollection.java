package net.caffeinemc.mods.sodium.client.render.chunk.lists;

import it.unimi.dsi.fastutil.longs.Long2ReferenceMap;
import it.unimi.dsi.fastutil.longs.LongHeapPriorityQueue;
import net.caffeinemc.mods.sodium.client.render.chunk.DeferMode;
import net.caffeinemc.mods.sodium.client.render.chunk.RenderSection;
import net.minecraft.core.SectionPos;

import java.util.EnumMap;

public class TaskListCollection extends EnumMap<DeferMode, LongHeapPriorityQueue> {
    private final long creationTime;
    private final boolean isFrustumTested;
    private final int baseOffsetX;
    private final int baseOffsetZ;

    public TaskListCollection(Class<DeferMode> keyType, long creationTime, boolean isFrustumTested, int baseOffsetX, int baseOffsetZ) {
        super(keyType);
        this.creationTime = creationTime;
        this.isFrustumTested = isFrustumTested;
        this.baseOffsetX = baseOffsetX;
        this.baseOffsetZ = baseOffsetZ;
    }

    public float getCollectorPriorityBias(long now) {
        // compensate for creation time of the list and whether the sections are in the frustum
        return (now - this.creationTime) * PendingTaskCollector.PENDING_TIME_FACTOR +
                (this.isFrustumTested ? PendingTaskCollector.WITHIN_FRUSTUM_BIAS : 0);
    }

    public RenderSection decodeAndFetchSection(Long2ReferenceMap<RenderSection> sectionByPosition, long encoded) {
        var localX = (int) (encoded >>> 20) & 0b1111111111;
        var localY = (int) (encoded >>> 10) & 0b1111111111;
        var localZ = (int) (encoded & 0b1111111111);

        var globalX = localX + this.baseOffsetX;
        var globalY = localY + PendingTaskCollector.SECTION_Y_MIN;
        var globalZ = localZ + this.baseOffsetZ;

        return sectionByPosition.get(SectionPos.asLong(globalX, globalY, globalZ));
    }
}
