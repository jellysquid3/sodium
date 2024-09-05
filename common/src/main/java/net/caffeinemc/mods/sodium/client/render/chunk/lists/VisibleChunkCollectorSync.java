package net.caffeinemc.mods.sodium.client.render.chunk.lists;

import it.unimi.dsi.fastutil.objects.ObjectArrayList;
import net.caffeinemc.mods.sodium.client.render.chunk.LocalSectionIndex;
import net.caffeinemc.mods.sodium.client.render.chunk.RenderSection;
import net.caffeinemc.mods.sodium.client.render.chunk.RenderSectionFlags;
import net.caffeinemc.mods.sodium.client.render.chunk.occlusion.LinearSectionOctree;
import net.caffeinemc.mods.sodium.client.render.chunk.occlusion.OcclusionCuller;
import net.caffeinemc.mods.sodium.client.render.chunk.region.RenderRegion;
import net.caffeinemc.mods.sodium.client.render.chunk.region.RenderRegionManager;

public class VisibleChunkCollectorSync implements OcclusionCuller.GraphOcclusionVisitor {
    private final ObjectArrayList<ChunkRenderList> sortedRenderLists;

    private final LinearSectionOctree tree;
    private final int frame;

    public VisibleChunkCollectorSync(LinearSectionOctree tree, int frame) {
        this.tree = tree;
        this.frame = frame;

        this.sortedRenderLists = new ObjectArrayList<>();
    }

    @Override
    public void visit(RenderSection section, boolean visible) {
        this.tree.visit(section, visible);

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
