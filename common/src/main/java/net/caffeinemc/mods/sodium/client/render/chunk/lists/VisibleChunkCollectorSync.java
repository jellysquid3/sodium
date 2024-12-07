package net.caffeinemc.mods.sodium.client.render.chunk.lists;

import it.unimi.dsi.fastutil.ints.IntArrays;
import it.unimi.dsi.fastutil.objects.ObjectArrayList;
import net.caffeinemc.mods.sodium.client.render.chunk.RenderSection;
import net.caffeinemc.mods.sodium.client.render.chunk.RenderSectionFlags;
import net.caffeinemc.mods.sodium.client.render.chunk.occlusion.CullType;
import net.caffeinemc.mods.sodium.client.render.chunk.occlusion.SectionTree;
import net.caffeinemc.mods.sodium.client.render.chunk.region.RenderRegion;
import net.caffeinemc.mods.sodium.client.render.viewport.Viewport;
import net.minecraft.world.level.Level;

/**
 * The sync visible chunk collector is passed into the graph search occlusion culler to collect visible chunks.
 */
public class VisibleChunkCollectorSync extends SectionTree {
    private final ObjectArrayList<ChunkRenderList> sortedRenderLists;

    public VisibleChunkCollectorSync(Viewport viewport, float buildDistance, int frame, CullType cullType, Level level) {
        super(viewport, buildDistance, frame, cullType, level);
        this.sortedRenderLists = new ObjectArrayList<>();
    }

    @Override
    public void visit(RenderSection section) {
        super.visit(section);

        RenderRegion region = section.getRegion();
        ChunkRenderList renderList = region.getRenderList();

        // Even if a section does not have render objects, we must ensure the render list is initialized and put
        // into the sorted queue of lists, so that we maintain the correct order of draw calls.
        if (renderList.getLastVisibleFrame() != this.frame) {
            renderList.reset(this.frame);

            this.sortedRenderLists.add(renderList);
        }

        var index = section.getSectionIndex();
        if ((region.getSectionFlags(index) & RenderSectionFlags.MASK_NEEDS_RENDER) != 0) {
            renderList.add(index);
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
}
