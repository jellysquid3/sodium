package net.caffeinemc.mods.sodium.client.render.chunk.lists;

import it.unimi.dsi.fastutil.objects.ObjectArrayFIFOQueue;
import net.caffeinemc.mods.sodium.client.render.chunk.ChunkUpdateType;
import net.caffeinemc.mods.sodium.client.render.chunk.RenderSection;
import net.caffeinemc.mods.sodium.client.render.chunk.occlusion.OcclusionCuller;

import java.util.EnumMap;
import java.util.Map;

public class PendingTaskCollector implements OcclusionCuller.GraphOcclusionVisitor {
    private final EnumMap<ChunkUpdateType, ObjectArrayFIFOQueue<RenderSection>> sortedRebuildLists;

    public PendingTaskCollector() {
        this.sortedRebuildLists = new EnumMap<>(ChunkUpdateType.class);

        for (var type : ChunkUpdateType.values()) {
            this.sortedRebuildLists.put(type, new ObjectArrayFIFOQueue<>());
        }
    }

    @Override
    public void visit(RenderSection section) {
        ChunkUpdateType type = section.getPendingUpdate();

        if (type != null && section.getTaskCancellationToken() == null) {
            ObjectArrayFIFOQueue<RenderSection> queue = this.sortedRebuildLists.get(type);

            if (queue.size() < type.getMaximumQueueSize()) {
                queue.enqueue(section);
            }
        }
    }

    public Map<ChunkUpdateType, ObjectArrayFIFOQueue<RenderSection>> getRebuildLists() {
        return this.sortedRebuildLists;
    }
}
