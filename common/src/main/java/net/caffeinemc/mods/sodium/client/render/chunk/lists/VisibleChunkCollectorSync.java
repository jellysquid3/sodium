package net.caffeinemc.mods.sodium.client.render.chunk.lists;

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
public class VisibleChunkCollectorSync extends SectionTree implements RenderListProvider {
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

    @Override
    public ObjectArrayList<ChunkRenderList> getUnsortedRenderLists() {
        return this.sortedRenderLists;
    }

    @Override
    public int[] getCachedSortItems() {
        return sortItems;
    }

    @Override
    public void setCachedSortItems(int[] sortItems) {
        VisibleChunkCollectorSync.sortItems = sortItems;
    }
}
