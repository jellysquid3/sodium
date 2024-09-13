package net.caffeinemc.mods.sodium.client.render.chunk.lists;

import net.caffeinemc.mods.sodium.client.render.chunk.ChunkUpdateType;
import net.caffeinemc.mods.sodium.client.render.chunk.RenderSection;
import net.caffeinemc.mods.sodium.client.render.chunk.occlusion.CullType;
import net.caffeinemc.mods.sodium.client.render.chunk.occlusion.SectionTree;
import net.caffeinemc.mods.sodium.client.render.viewport.Viewport;

public class TaskSectionTree extends SectionTree {
    private final Tree mainTaskTree;
    private Tree secondaryTaskTree;

    public TaskSectionTree(Viewport viewport, float buildDistance, int frame, CullType cullType) {
        super(viewport, buildDistance, frame, cullType);

        this.mainTaskTree = new Tree(this.baseOffsetX, this.baseOffsetY, this.baseOffsetZ);
    }

    @Override
    protected void addPendingSection(RenderSection section, ChunkUpdateType type) {
        super.addPendingSection(section, type);

        this.markTaskPresent(section.getChunkX(), section.getChunkY(), section.getChunkZ());
    }

    protected void markTaskPresent(int x, int y, int z) {
        if (this.mainTaskTree.add(x, y, z)) {
            if (this.secondaryTaskTree == null) {
                this.secondaryTaskTree = this.makeSecondaryTree();
            }
            if (this.secondaryTaskTree.add(x, y, z)) {
                throw new IllegalStateException("Failed to add section to trees");
            }
        }
    }

    public void traverseVisiblePendingTasks(VisibleSectionVisitor visitor, Viewport viewport, float distanceLimit) {
        this.mainTaskTree.traverse(visitor, viewport, distanceLimit);
        if (this.secondaryTaskTree != null) {
            this.secondaryTaskTree.traverse(visitor, viewport, distanceLimit);
        }
    }
}