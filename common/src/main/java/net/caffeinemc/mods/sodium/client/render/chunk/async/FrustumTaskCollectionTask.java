package net.caffeinemc.mods.sodium.client.render.chunk.async;

import it.unimi.dsi.fastutil.longs.Long2ReferenceMap;
import net.caffeinemc.mods.sodium.client.render.chunk.RenderSection;
import net.caffeinemc.mods.sodium.client.render.chunk.lists.FrustumTaskCollector;
import net.caffeinemc.mods.sodium.client.render.chunk.lists.PendingTaskCollector;
import net.caffeinemc.mods.sodium.client.render.chunk.lists.TaskSectionTree;
import net.caffeinemc.mods.sodium.client.render.viewport.Viewport;

public class FrustumTaskCollectionTask extends AsyncRenderTask<FrustumTaskListsResult> {
    private final Long2ReferenceMap<RenderSection> sectionByPosition;
    private final TaskSectionTree globalTaskTree;

    public FrustumTaskCollectionTask(Viewport viewport, float buildDistance, int frame, Long2ReferenceMap<RenderSection> sectionByPosition, TaskSectionTree globalTaskTree) {
        super(viewport, buildDistance, frame);
        this.sectionByPosition = sectionByPosition;
        this.globalTaskTree = globalTaskTree;
    }

    @Override
    public FrustumTaskListsResult runTask() {
        var collector = new FrustumTaskCollector(this.viewport, this.buildDistance, this.sectionByPosition);
        this.globalTaskTree.traverseVisiblePendingTasks(collector, this.viewport, this.buildDistance);

        var frustumTaskLists = collector.getPendingTaskLists();
        return () -> frustumTaskLists;
    }
}
