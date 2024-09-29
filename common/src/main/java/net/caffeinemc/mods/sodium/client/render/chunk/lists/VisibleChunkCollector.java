package net.caffeinemc.mods.sodium.client.render.chunk.lists;

import it.unimi.dsi.fastutil.objects.ObjectArrayList;
import net.caffeinemc.mods.sodium.client.render.chunk.ChunkUpdateType;
import net.caffeinemc.mods.sodium.client.render.chunk.RenderSection;
import net.caffeinemc.mods.sodium.client.render.chunk.occlusion.OcclusionCuller;
import net.caffeinemc.mods.sodium.client.render.chunk.region.RenderRegion;
import net.caffeinemc.mods.sodium.client.render.viewport.Viewport;

import java.util.*;

/**
 * The visible chunk collector is passed to the occlusion graph search culler to
 * collect the visible chunks.
 */
public class VisibleChunkCollector implements OcclusionCuller.Visitor {
    private final ObjectArrayList<ChunkRenderList> sortedRenderLists;
    private final EnumMap<ChunkUpdateType, ArrayDeque<RenderSection>> sortedRebuildLists;

    private final int frame;

    public VisibleChunkCollector(int frame) {
        this.frame = frame;

        this.sortedRenderLists = new ObjectArrayList<>();
        this.sortedRebuildLists = new EnumMap<>(ChunkUpdateType.class);

        for (var type : ChunkUpdateType.values()) {
            this.sortedRebuildLists.put(type, new ArrayDeque<>());
        }
    }

    @Override
    public void visit(RenderSection section, boolean visible) {
        RenderRegion region = section.getRegion();
        ChunkRenderList renderList = region.getRenderList();

        // Even if a section does not have render objects, we must ensure the render list is initialized and put
        // into the sorted queue of lists, so that we maintain the correct order of draw calls.
        if (renderList.getLastVisibleFrame() != this.frame) {
            renderList.reset(this.frame);

            this.sortedRenderLists.add(renderList);
        }

        if (visible && section.getFlags() != 0) {
            renderList.add(section);
        }

        this.addToRebuildLists(section);
    }

    private void addToRebuildLists(RenderSection section) {
        ChunkUpdateType type = section.getPendingUpdate();

        if (type != null && section.getTaskCancellationToken() == null) {
            Queue<RenderSection> queue = this.sortedRebuildLists.get(type);

            if (queue.size() < type.getMaximumQueueSize()) {
                queue.add(section);
            }
        }
    }

    public SortedRenderLists createRenderLists(Viewport viewport) {
        // sort the regions by distance to fix rare region ordering bugs
        var transform = viewport.getTransform();
        var cameraX = transform.intX >> (4 + RenderRegion.REGION_WIDTH_SH);
        var cameraY = transform.intY >> (4 + RenderRegion.REGION_HEIGHT_SH);
        var cameraZ = transform.intZ >> (4 + RenderRegion.REGION_LENGTH_SH);
        var size = this.sortedRenderLists.size();

        var items = new int[size];
        for (var i = 0; i < size; i++) {
            var region = this.sortedRenderLists.get(i).getRegion();
            var x = Math.abs(region.getRawX() - cameraX);
            var y = Math.abs(region.getRawY() - cameraY);
            var z = Math.abs(region.getRawZ() - cameraZ);
            items[i] = (x + y + z) << 16 | i;
        }

        Arrays.sort(items);

        var sorted = new ObjectArrayList<ChunkRenderList>(size);
        for (var key : items) {
            sorted.add(this.sortedRenderLists.get(key & 0xFFFF));
        }

        return new SortedRenderLists(sorted);
    }

    public Map<ChunkUpdateType, ArrayDeque<RenderSection>> getRebuildLists() {
        return this.sortedRebuildLists;
    }
}
