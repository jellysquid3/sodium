package net.caffeinemc.mods.sodium.client.render.chunk.lists;

import it.unimi.dsi.fastutil.objects.ObjectArrayList;
import net.caffeinemc.mods.sodium.client.render.chunk.RenderSection;
import net.caffeinemc.mods.sodium.client.render.chunk.RenderSectionFlags;
import net.caffeinemc.mods.sodium.client.render.chunk.occlusion.CullType;
import net.caffeinemc.mods.sodium.client.render.chunk.occlusion.SectionTree;
import net.caffeinemc.mods.sodium.client.render.chunk.region.RenderRegion;
import net.caffeinemc.mods.sodium.client.render.viewport.Viewport;

public class VisibleChunkCollectorSync extends SectionTree {
    private final ObjectArrayList<ChunkRenderList> sortedRenderLists;

    public VisibleChunkCollectorSync(Viewport viewport, float buildDistance, int frame, CullType cullType) {
        super(viewport, buildDistance, frame, cullType);
        this.sortedRenderLists = new ObjectArrayList<>();
    }

    @Override
    public void visit(RenderSection section, boolean visible) {
        super.visit(section, visible);

        RenderRegion region = section.getRegion();
        ChunkRenderList renderList = region.getRenderList();

        // Even if a section does not have render objects, we must ensure the render list is initialized and put
        // into the sorted queue of lists, so that we maintain the correct order of draw calls.
        if (renderList.getLastVisibleFrame() != this.frame) {
            renderList.reset(this.frame);

            this.sortedRenderLists.add(renderList);
        }

        var index = section.getSectionIndex();
        if (visible && (region.getSectionFlags(index) & RenderSectionFlags.MASK_NEEDS_RENDER) != 0) {
            renderList.add(index);
        }
    }

    public SortedRenderLists createRenderLists() {
        return new SortedRenderLists(this.sortedRenderLists);
    }
}
