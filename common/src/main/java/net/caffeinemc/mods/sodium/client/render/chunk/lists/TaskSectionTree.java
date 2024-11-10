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

    public TaskSectionTree(Viewport viewport, float buildDistance, int frame, CullType cullType, Level level) {
        super(viewport, buildDistance, frame, cullType, level);

        this.taskTree = TraversableForest.createTraversableForest(this.baseOffsetX, this.baseOffsetY, this.baseOffsetZ, buildDistance, level);
    }

    @Override
    protected void addPendingSection(RenderSection section, ChunkUpdateType type) {
        super.addPendingSection(section, type);

        this.taskTree.add(section.getChunkX(), section.getChunkY(), section.getChunkZ());
    }

    @Override
    public void finalizeTrees() {
        super.finalizeTrees();
        this.taskTree.calculateReduced();
    }

    public void traverseVisiblePendingTasks(VisibleSectionVisitor visitor, Viewport viewport, float distanceLimit) {
        this.taskTree.traverse(visitor, viewport, distanceLimit);
    }
}