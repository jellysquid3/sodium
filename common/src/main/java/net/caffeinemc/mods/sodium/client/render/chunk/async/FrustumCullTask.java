package net.caffeinemc.mods.sodium.client.render.chunk.async;

import net.caffeinemc.mods.sodium.client.render.chunk.lists.PendingTaskCollector;
import net.caffeinemc.mods.sodium.client.render.chunk.occlusion.CullType;
import net.caffeinemc.mods.sodium.client.render.chunk.occlusion.OcclusionCuller;
import net.caffeinemc.mods.sodium.client.render.chunk.occlusion.SectionTree;
import net.caffeinemc.mods.sodium.client.render.viewport.Viewport;

public class FrustumCullTask extends CullTask<FrustumCullResult> {
    public FrustumCullTask(OcclusionCuller occlusionCuller, Viewport viewport, float buildDistance, boolean useOcclusionCulling, int frame) {
        super(viewport, buildDistance, frame, occlusionCuller, useOcclusionCulling);
    }

    @Override
    public FrustumCullResult runTask() {
        var tree = new SectionTree(this.viewport, this.buildDistance, this.frame, CullType.FRUSTUM);
        this.occlusionCuller.findVisible(tree, this.viewport, this.buildDistance, this.useOcclusionCulling, this.frame);

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
