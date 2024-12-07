package net.caffeinemc.mods.sodium.client.render.chunk.lists;

import it.unimi.dsi.fastutil.ints.IntArrays;
import it.unimi.dsi.fastutil.objects.ObjectArrayList;
import net.caffeinemc.mods.sodium.client.render.chunk.ChunkUpdateType;
import net.caffeinemc.mods.sodium.client.render.chunk.RenderSection;
import net.caffeinemc.mods.sodium.client.render.chunk.occlusion.OcclusionCuller;
import net.caffeinemc.mods.sodium.client.render.chunk.region.RenderRegion;
import net.caffeinemc.mods.sodium.client.render.viewport.Viewport;

import java.util.ArrayDeque;
import java.util.EnumMap;
import java.util.Map;
import java.util.Queue;

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
    public void visit(RenderSection section) {
        // only process section (and associated render list) if it has content that needs rendering
        if (section.getFlags() != 0) {
            RenderRegion region = section.getRegion();
            ChunkRenderList renderList = region.getRenderList();

            if (renderList.getLastVisibleFrame() != this.frame) {
                renderList.reset(this.frame);

                this.sortedRenderLists.add(renderList);
            }

            renderList.add(section);
        }

        // always add to rebuild lists though, because it might just not be built yet
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

    private static int[] sortItems = new int[RenderRegion.REGION_SIZE];

    public SortedRenderLists createRenderLists(Viewport viewport) {
        // sort the regions by distance to fix rare region ordering bugs
        var sectionPos = viewport.getChunkCoord();
        var cameraX = sectionPos.getX() >> RenderRegion.REGION_WIDTH_SH;
        var cameraY = sectionPos.getY() >> RenderRegion.REGION_HEIGHT_SH;
        var cameraZ = sectionPos.getZ() >> RenderRegion.REGION_LENGTH_SH;
        var size = this.sortedRenderLists.size();

        if (sortItems.length < size) {
            sortItems = new int[size];
        }

        for (var i = 0; i < size; i++) {
            var region = this.sortedRenderLists.get(i).getRegion();
            var x = Math.abs(region.getX() - cameraX);
            var y = Math.abs(region.getY() - cameraY);
            var z = Math.abs(region.getZ() - cameraZ);
            sortItems[i] = (x + y + z) << 16 | i;
        }

        IntArrays.unstableSort(sortItems, 0, size);

        var sorted = new ObjectArrayList<ChunkRenderList>(size);
        for (var i = 0; i < size; i++) {
            var key = sortItems[i];
            var renderList = this.sortedRenderLists.get(key & 0xFFFF);
            sorted.add(renderList);
        }

        for (var list : sorted) {
            list.sortSections(sectionPos, sortItems);
        }

        return new SortedRenderLists(sorted);
    }

    public Map<ChunkUpdateType, ArrayDeque<RenderSection>> getRebuildLists() {
        return this.sortedRebuildLists;
    }
}
