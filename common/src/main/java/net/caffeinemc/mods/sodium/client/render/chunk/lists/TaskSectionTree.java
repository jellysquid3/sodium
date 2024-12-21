package net.caffeinemc.mods.sodium.client.render.chunk.lists;

import net.caffeinemc.mods.sodium.client.render.chunk.ChunkUpdateType;
import net.caffeinemc.mods.sodium.client.render.chunk.RenderSection;
import net.caffeinemc.mods.sodium.client.render.chunk.occlusion.CullType;
import net.caffeinemc.mods.sodium.client.render.chunk.occlusion.RayOcclusionSectionTree;
import net.caffeinemc.mods.sodium.client.render.chunk.tree.TraversableForest;
import net.caffeinemc.mods.sodium.client.render.viewport.Viewport;
import net.minecraft.world.level.Level;

public class TaskSectionTree extends RayOcclusionSectionTree {
    private final TraversableForest taskTree;
    private boolean taskTreeFinalized = false;

    public TaskSectionTree(Viewport viewport, float buildDistance, int frame, CullType cullType, Level level) {
        super(viewport, buildDistance, frame, cullType, level);

        this.taskTree = TraversableForest.createTraversableForest(this.baseOffsetX, this.baseOffsetY, this.baseOffsetZ, buildDistance, level);
    }

    public void markSectionTask(RenderSection section) {
        this.taskTree.add(section);
        this.taskTreeFinalized = false;
    }

    @Override
    protected void addPendingSection(RenderSection section, ChunkUpdateType type) {
        super.addPendingSection(section, type);

        this.markSectionTask(section);
    }

    public void traverseVisiblePendingTasks(VisibleSectionVisitor visitor, Viewport viewport, float distanceLimit) {
        if (!this.taskTreeFinalized) {
            this.taskTree.prepareForTraversal();
            this.taskTreeFinalized = true;
        }

        this.taskTree.traverse(visitor, viewport, distanceLimit);
    }
}