package net.caffeinemc.mods.sodium.client.render.chunk.async;

import it.unimi.dsi.fastutil.longs.Long2ReferenceMap;
import net.caffeinemc.mods.sodium.client.render.chunk.RenderSection;
import net.caffeinemc.mods.sodium.client.render.chunk.lists.FrustumTaskCollector;
import net.caffeinemc.mods.sodium.client.render.chunk.lists.PendingTaskCollector;
import net.caffeinemc.mods.sodium.client.render.chunk.lists.TaskSectionTree;
import net.caffeinemc.mods.sodium.client.render.chunk.occlusion.CullType;
import net.caffeinemc.mods.sodium.client.render.chunk.occlusion.OcclusionCuller;
import net.caffeinemc.mods.sodium.client.render.viewport.Viewport;

public class GlobalCullTask extends CullTask<GlobalCullResult> {
    private final Long2ReferenceMap<RenderSection> sectionByPosition;
    private final CullType cullType;

    public GlobalCullTask(OcclusionCuller occlusionCuller, Viewport viewport, float buildDistance, boolean useOcclusionCulling, int frame, Long2ReferenceMap<RenderSection> sectionByPosition, CullType cullType) {
        super(viewport, buildDistance, frame, occlusionCuller, useOcclusionCulling);
        this.sectionByPosition = sectionByPosition;
        this.cullType = cullType;
    }

    @Override
    public GlobalCullResult runTask() {
        var tree = new TaskSectionTree(this.viewport, this.buildDistance, this.frame, this.cullType);
        this.occlusionCuller.findVisible(tree, this.viewport, this.buildDistance, this.useOcclusionCulling, this.getOcclusionToken());

        var collector = new FrustumTaskCollector(this.viewport, this.buildDistance, this.sectionByPosition);
        tree.traverseVisiblePendingTasks(collector, this.viewport, this.buildDistance);

        var globalTaskLists = tree.getPendingTaskLists();
        var frustumTaskLists = collector.getPendingTaskLists();

        return new GlobalCullResult() {
            @Override
            public TaskSectionTree getTaskTree() {
                return tree;
            }

            @Override
            public PendingTaskCollector.TaskListCollection getFrustumTaskLists() {
                return frustumTaskLists;
            }

            @Override
            public PendingTaskCollector.TaskListCollection getGlobalTaskLists() {
                return globalTaskLists;
            }
        };
    }

    @Override
    public CullType getCullType() {
        return this.cullType;
    }
}
