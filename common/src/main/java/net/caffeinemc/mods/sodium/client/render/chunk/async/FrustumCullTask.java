package net.caffeinemc.mods.sodium.client.render.chunk.async;

import it.unimi.dsi.fastutil.longs.LongArrayList;
import net.caffeinemc.mods.sodium.client.render.chunk.lists.PendingTaskCollector;
import net.caffeinemc.mods.sodium.client.render.chunk.occlusion.CullType;
import net.caffeinemc.mods.sodium.client.render.chunk.occlusion.OcclusionCuller;
import net.caffeinemc.mods.sodium.client.render.chunk.occlusion.RayOcclusionSectionTree;
import net.caffeinemc.mods.sodium.client.render.chunk.occlusion.SectionTree;
import net.caffeinemc.mods.sodium.client.render.viewport.Viewport;
import net.minecraft.world.level.Level;

public class FrustumCullTask extends CullTask<FrustumCullResult> {
    private final Level level;

    public FrustumCullTask(Viewport viewport, float buildDistance, int frame, OcclusionCuller occlusionCuller, boolean useOcclusionCulling, Level level) {
        super(viewport, buildDistance, frame, occlusionCuller, useOcclusionCulling);
        this.level = level;
    }

    private static final LongArrayList timings = new LongArrayList();

    @Override
    public FrustumCullResult runTask() {
        var tree = new RayOcclusionSectionTree(this.viewport, this.buildDistance, this.frame, CullType.FRUSTUM, this.level);

        var start = System.nanoTime();

        this.occlusionCuller.findVisible(tree, this.viewport, this.buildDistance, this.useOcclusionCulling, this);
        tree.prepareForTraversal();

        var end = System.nanoTime();
        var time = end - start;
        timings.add(time);
        if (timings.size() >= 500) {
            var average = timings.longStream().average().orElse(0);
            System.out.println("Frustum culling took " + (average) / 1000 + "µs over " + timings.size() + " samples");
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
    public AsyncTaskType getTaskType() {
        return AsyncTaskType.FRUSTUM_CULL;
    }

    @Override
    public CullType getCullType() {
        return CullType.FRUSTUM;
    }
}
