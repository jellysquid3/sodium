package net.caffeinemc.mods.sodium.client.render.chunk.async;

import it.unimi.dsi.fastutil.longs.LongArrayList;
import net.caffeinemc.mods.sodium.client.render.chunk.lists.PendingTaskCollector;
import net.caffeinemc.mods.sodium.client.render.chunk.occlusion.CullType;
import net.caffeinemc.mods.sodium.client.render.chunk.occlusion.OcclusionCuller;
import net.caffeinemc.mods.sodium.client.render.chunk.occlusion.RayOcclusionSectionTree;
import net.caffeinemc.mods.sodium.client.render.chunk.occlusion.SectionTree;
import net.caffeinemc.mods.sodium.client.render.viewport.Viewport;

public class FrustumCullTask extends CullTask<FrustumCullResult> {
    public FrustumCullTask(OcclusionCuller occlusionCuller, Viewport viewport, float buildDistance, boolean useOcclusionCulling, int frame) {
        super(viewport, buildDistance, frame, occlusionCuller, useOcclusionCulling);
    }

    private static final LongArrayList timings = new LongArrayList();

    @Override
    public FrustumCullResult runTask() {
        var tree = new RayOcclusionSectionTree(this.viewport, this.buildDistance, this.frame, CullType.FRUSTUM);
        var start = System.nanoTime();
        this.occlusionCuller.findVisible(tree, this.viewport, this.buildDistance, this.useOcclusionCulling);
        tree.finalizeTrees();
        var end = System.nanoTime();
        var time = end - start;
        timings.add(time);
        final var count = 500;
        if (timings.size() > count) {
            var average = timings.longStream().average().orElse(0);
            System.out.println("Frustum culling took " + (average) / 1000 + "µs over " + count + " samples");
            timings.clear();
        }

        var frustumTaskLists = tree.getPendingTaskLists();

        return new FrustumCullResult() {
            @Override
            public SectionTree getTree() {
                return tree;
            }

            @Override
            public PendingTaskCollector.TaskListCollection getFrustumTaskLists() {
                return frustumTaskLists;
            }
        };
    }

    @Override
    public CullType getCullType() {
        return CullType.FRUSTUM;
    }
}
