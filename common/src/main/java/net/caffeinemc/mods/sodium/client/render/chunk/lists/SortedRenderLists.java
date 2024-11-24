package net.caffeinemc.mods.sodium.client.render.chunk.lists;

import it.unimi.dsi.fastutil.objects.ObjectArrayList;
import net.caffeinemc.mods.sodium.client.util.iterator.ReversibleObjectArrayIterator;

/**
 * Stores one render list of sections per region, sorted by the order in which
 * they were discovered in the BFS of the occlusion culler. It also generates
 * render lists for sections of previously unseen regions.
 */
public class SortedRenderLists implements ChunkRenderListIterable {
    private static final SortedRenderLists EMPTY = new SortedRenderLists(ObjectArrayList.of());

    private final ObjectArrayList<ChunkRenderList> lists;

    SortedRenderLists(ObjectArrayList<ChunkRenderList> lists) {
        this.lists = lists;
    }

    @Override
    public ReversibleObjectArrayIterator<ChunkRenderList> iterator(boolean reverse) {
        return new ReversibleObjectArrayIterator<>(this.lists, reverse);
    }

    public static SortedRenderLists empty() {
        return EMPTY;
    }
}
