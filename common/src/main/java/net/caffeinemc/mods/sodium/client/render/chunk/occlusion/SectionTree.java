package net.caffeinemc.mods.sodium.client.render.chunk.occlusion;

import net.caffeinemc.mods.sodium.client.render.chunk.RenderSection;
import net.caffeinemc.mods.sodium.client.render.chunk.RenderSectionFlags;
import net.caffeinemc.mods.sodium.client.render.chunk.lists.PendingTaskCollector;
import net.caffeinemc.mods.sodium.client.render.chunk.tree.TraversableForest;
import net.caffeinemc.mods.sodium.client.render.chunk.tree.Tree;
import net.caffeinemc.mods.sodium.client.render.viewport.Viewport;
import net.minecraft.core.SectionPos;
import net.minecraft.world.level.Level;

/*
 * - make another tree similar to this one that is used to track invalidation cubes in the bfs to make it possible to reuse some of its results (?)
 * - make another tree that that is filled with all bfs-visited sections to do ray-cast culling during traversal. This is fast if we can just check for certain bits in the tree instead of stepping through many sections. If the top node is 1, that means a ray might be able to get through, traverse further in that case. If it's 0, that means it's definitely blocked since we haven't visited sections that it might go through, but since bfs goes outwards, no such sections will be added later. Delete this auxiliary tree after traversal. Would need to check the projection of the entire section to the camera (potentially irregular hexagonal frustum, or just check each of the at most six visible corners.) Do a single traversal where each time the node is checked against all participating rays/visibility shapes. Alternatively, check a cylinder that encompasses the section's elongation towards the camera plane. (would just require some distance checks, maybe faster?)
 * - are incremental bfs updates possible or useful? Since bfs order doesn't matter with the render list being generated from the tree, that might reduce the load on the async cull thread. (essentially just bfs but with the queue initialized to the set of changed sections.) Problem: might result in more sections being visible than intended, since sections aren't removed when another bfs is run starting from updated sections.
 */
public class SectionTree extends PendingTaskCollector implements OcclusionCuller.GraphOcclusionVisitor {
    private final TraversableForest tree;

    private final int bfsWidth;

    public final float buildDistance;
    protected final int frame;
    protected boolean lastSectionKnownEmpty = false;

    public interface VisibleSectionVisitor {
        void visit(int x, int y, int z);
    }

    public SectionTree(Viewport viewport, float buildDistance, int frame, CullType cullType, Level level) {
        super(viewport, buildDistance, cullType.isFrustumTested);

        this.bfsWidth = cullType.bfsWidth;
        this.buildDistance = buildDistance;
        this.frame = frame;

        this.tree = TraversableForest.createTraversableForest(this.baseOffsetX, this.baseOffsetY, this.baseOffsetZ, buildDistance, level);
    }

    public int getFrame() {
        return this.frame;
    }

    public boolean isValidFor(Viewport viewport, float searchDistance) {
        var cameraPos = viewport.getChunkCoord();
        return this.cameraX >> 4 == cameraPos.getX() &&
                this.cameraY >> 4 == cameraPos.getY() &&
                this.cameraZ >> 4 == cameraPos.getZ() &&
                this.buildDistance >= searchDistance;
    }

    @Override
    public boolean isWithinFrustum(Viewport viewport, RenderSection section) {
        return !this.isFrustumTested || super.isWithinFrustum(viewport, section);
    }

    @Override
    public int getOutwardDirections(SectionPos origin, RenderSection section) {
        int planes = 0;

        planes |= section.getChunkX() <= origin.getX() + this.bfsWidth ? 1 << GraphDirection.WEST : 0;
        planes |= section.getChunkX() >= origin.getX() - this.bfsWidth ? 1 << GraphDirection.EAST : 0;

        planes |= section.getChunkY() <= origin.getY() + this.bfsWidth ? 1 << GraphDirection.DOWN : 0;
        planes |= section.getChunkY() >= origin.getY() - this.bfsWidth ? 1 << GraphDirection.UP : 0;

        planes |= section.getChunkZ() <= origin.getZ() + this.bfsWidth ? 1 << GraphDirection.NORTH : 0;
        planes |= section.getChunkZ() >= origin.getZ() - this.bfsWidth ? 1 << GraphDirection.SOUTH : 0;

        return planes;
    }

    @Override
    public void visit(RenderSection section) {
        super.visit(section);

        // discard invisible or sections that don't need to be rendered,
        // only perform this test if it hasn't already been done before
        if (this.lastSectionKnownEmpty || (section.getRegion().getSectionFlags(section.getSectionIndex()) & RenderSectionFlags.MASK_NEEDS_RENDER) == 0) {
            return;
        }

        this.markPresent(section.getChunkX(), section.getChunkY(), section.getChunkZ());
    }

    protected void markPresent(int x, int y, int z) {
        this.tree.add(x, y, z);
    }

    public void finalizeTrees() {
        this.tree.calculateReduced();
    }

    public boolean isBoxVisible(double x1, double y1, double z1, double x2, double y2, double z2) {
        // check if there's a section at any part of the box
        int minX = SectionPos.posToSectionCoord(x1 - 0.5D);
        int minY = SectionPos.posToSectionCoord(y1 - 0.5D);
        int minZ = SectionPos.posToSectionCoord(z1 - 0.5D);

        int maxX = SectionPos.posToSectionCoord(x2 + 0.5D);
        int maxY = SectionPos.posToSectionCoord(y2 + 0.5D);
        int maxZ = SectionPos.posToSectionCoord(z2 + 0.5D);

        for (int x = minX; x <= maxX; x++) {
            for (int z = minZ; z <= maxZ; z++) {
                for (int y = minY; y <= maxY; y++) {
                    if (this.isSectionPresent(x, y, z)) {
                        return true;
                    }
                }
            }
        }

        return false;
    }

    private boolean isSectionPresent(int x, int y, int z) {
        return this.tree.getPresence(x, y, z) == Tree.PRESENT;
    }

    public void traverseVisible(VisibleSectionVisitor visitor, Viewport viewport, float distanceLimit) {
        this.tree.traverse(visitor, viewport, distanceLimit);
    }
}
