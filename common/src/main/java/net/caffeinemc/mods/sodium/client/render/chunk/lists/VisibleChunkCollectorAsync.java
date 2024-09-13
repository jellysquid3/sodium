package net.caffeinemc.mods.sodium.client.render.chunk.lists;

import it.unimi.dsi.fastutil.ints.IntArrays;
import it.unimi.dsi.fastutil.objects.ObjectArrayList;
import net.caffeinemc.mods.sodium.client.render.chunk.LocalSectionIndex;
import net.caffeinemc.mods.sodium.client.render.chunk.occlusion.SectionTree;
import net.caffeinemc.mods.sodium.client.render.chunk.region.RenderRegion;
import net.caffeinemc.mods.sodium.client.render.chunk.region.RenderRegionManager;
import net.caffeinemc.mods.sodium.client.render.viewport.Viewport;

import java.util.ArrayDeque;
import java.util.EnumMap;
import java.util.Map;
import java.util.Queue;

/**
 * The visible chunk collector is passed to the occlusion graph search culler to
 * collect the visible chunks.
 */
public class VisibleChunkCollectorAsync implements SectionTree.VisibleSectionVisitor  {
    private final ObjectArrayList<ChunkRenderList> sortedRenderLists;

    private final RenderRegionManager regions;

    private final int frame;

    public VisibleChunkCollectorAsync(RenderRegionManager regions, int frame) {
        this.regions = regions;
        this.frame = frame;

        this.sortedRenderLists = new ObjectArrayList<>();
    }

    @Override
    public void visit(int x, int y, int z) {
        var region = this.regions.getForChunk(x, y, z);

        // since this is async, the region might have been removed in the meantime
        if (region == null) {
            return;
        }

        int rX = x & (RenderRegion.REGION_WIDTH - 1);
        int rY = y & (RenderRegion.REGION_HEIGHT - 1);
        int rZ = z & (RenderRegion.REGION_LENGTH - 1);
        var sectionIndex = LocalSectionIndex.pack(rX, rY, rZ);

        ChunkRenderList renderList = region.getRenderList();

        if (renderList.getLastVisibleFrame() != this.frame) {
            renderList.reset(this.frame);

            this.sortedRenderLists.add(renderList);
        }

        // flags don't need to be checked here since only sections with contents (RenderSectionFlags.MASK_NEEDS_RENDER) are added to the octree
        renderList.add(sectionIndex);
    }

    private static int[] sortItems = new int[RenderRegion.REGION_SIZE];

    public SortedRenderLists createSortedRenderLists(Viewport viewport) {
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

    public SortedRenderLists createRenderLists() {
        return new SortedRenderLists(this.sortedRenderLists);
    }
}
