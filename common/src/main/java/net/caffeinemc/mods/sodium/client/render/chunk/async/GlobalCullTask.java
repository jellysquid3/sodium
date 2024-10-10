package net.caffeinemc.mods.sodium.client.render.chunk.async;

import it.unimi.dsi.fastutil.longs.Long2ReferenceMap;
import it.unimi.dsi.fastutil.longs.LongArrayList;
import net.caffeinemc.mods.sodium.client.render.chunk.RenderSection;
import net.caffeinemc.mods.sodium.client.render.chunk.lists.FrustumTaskCollector;
import net.caffeinemc.mods.sodium.client.render.chunk.lists.PendingTaskCollector;
import net.caffeinemc.mods.sodium.client.render.chunk.lists.TaskSectionTree;
import net.caffeinemc.mods.sodium.client.render.chunk.occlusion.CullType;
import net.caffeinemc.mods.sodium.client.render.chunk.occlusion.OcclusionCuller;
import net.caffeinemc.mods.sodium.client.render.viewport.Viewport;
import net.minecraft.world.level.Level;

public class GlobalCullTask extends CullTask<GlobalCullResult> {
    private final Long2ReferenceMap<RenderSection> sectionByPosition;
    private final CullType cullType;
    private final Level level;

    public GlobalCullTask(Viewport viewport, float buildDistance, int frame, OcclusionCuller occlusionCuller, boolean useOcclusionCulling, Long2ReferenceMap<RenderSection> sectionByPosition, CullType cullType, Level level) {
        super(viewport, buildDistance, frame, occlusionCuller, useOcclusionCulling);
        this.sectionByPosition = sectionByPosition;
        this.cullType = cullType;
        this.level = level;
    }

    private static final LongArrayList timings = new LongArrayList();

    @Override
    public GlobalCullResult runTask() {
        var tree = new TaskSectionTree(this.viewport, this.buildDistance, this.frame, this.cullType, this.level);
        var start = System.nanoTime();
        this.occlusionCuller.findVisible(tree, this.viewport, this.buildDistance, this.useOcclusionCulling, this);
        tree.finalizeTrees();
        var end = System.nanoTime();
        var time = end - start;
        timings.add(time);
        if (timings.size() >= 500) {
            var average = timings.longStream().average().orElse(0);
            System.out.println("Global culling took " + (average) / 1000 + "µs over " + timings.size() + " samples");
            timings.clear();
        }
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
