package net.caffeinemc.mods.sodium.client.render.chunk.translucent_sorting.data;

import net.caffeinemc.mods.sodium.client.render.chunk.translucent_sorting.SortType;
import net.caffeinemc.mods.sodium.client.render.chunk.translucent_sorting.TQuad;
import net.minecraft.core.SectionPos;

/**
 * With this sort type the section's translucent quads can be rendered in any
 * order. However, they do need to be rendered with some index buffer, so that
 * vertices are assembled into quads. Since the sort order doesn't matter, all
 * sections with this sort type can share the same data in the index buffer.
 * 
 * NOTE: A possible optimization would be to share the buffer for unordered
 * translucent sections on the CPU and on the GPU. It would essentially be the
 * same as SharedQuadIndexBuffer, but it has to be compatible with sections in
 * the same region using custom index buffers which makes the management
 * complicated. The shared buffer would be a member amongst the other non-shared
 * buffer segments and would need to be resized when a larger section wants to
 * use it.
 */
public class AnyOrderData extends SplitDirectionData {
    private Sorter sorterOnce;

    AnyOrderData(SectionPos sectionPos, int[] vertexCounts, int quadCount) {
        super(sectionPos, vertexCounts, quadCount);
    }

    @Override
    public SortType getSortType() {
        return SortType.NONE;
    }

    @Override
    public Sorter getSorter() {
        var sorter = this.sorterOnce;
        if (sorter == null) {
            throw new IllegalStateException("Sorter already used!");
        }
        this.sorterOnce = null;
        return sorter;
    }

    /**
     * Important: The vertex indexes must start at zero for each facing.
     */
    public static AnyOrderData fromMesh(int[] vertexCounts,
                                        TQuad[] quads, SectionPos sectionPos) {
        var anyOrderData = new AnyOrderData(sectionPos, vertexCounts, quads.length);
        anyOrderData.sorterOnce = new SharedIndexSorter(quads.length);
        return anyOrderData;
    }
}
