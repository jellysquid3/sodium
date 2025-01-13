package net.caffeinemc.mods.sodium.client.render.chunk.occlusion;

import net.caffeinemc.mods.sodium.client.render.chunk.RenderSection;
import net.caffeinemc.mods.sodium.client.render.chunk.RenderSectionFlags;
import net.caffeinemc.mods.sodium.client.render.chunk.tree.*;
import net.caffeinemc.mods.sodium.client.render.viewport.CameraTransform;
import net.caffeinemc.mods.sodium.client.render.viewport.Viewport;
import net.minecraft.world.level.Level;

public class RayOcclusionSectionTree extends SectionTree {
    private static final float SECTION_HALF_DIAGONAL = (float) Math.sqrt(8 * 8 * 3);
    private static final float RAY_MIN_STEP_SIZE_INV = 1.0f / (SECTION_HALF_DIAGONAL * 2);
    private static final int RAY_TEST_MAX_STEPS = 12;
    private static final int MIN_RAY_TEST_DISTANCE_SQ = (int) Math.pow(16 * 3, 2);

    private final CameraTransform transform;
    private final int minSection, maxSection;

    private final Forest portalTree;

    public RayOcclusionSectionTree(Viewport viewport, float buildDistance, int frame, CullType cullType, Level level) {
        super(viewport, buildDistance, frame, cullType, level);

        this.transform = viewport.getTransform();
        this.portalTree = createPortalTree(this.baseOffsetX, this.baseOffsetY, this.baseOffsetZ, buildDistance, level);

        this.minSection = level.getMinSectionY();
        this.maxSection = level.getMaxSectionY();
    }

    @Override
    public boolean visitTestVisible(RenderSection section) {
        if (section.needsRender()) {
            this.lastSectionKnownEmpty = false;
            if (this.isRayBlockedStepped(section)) {
                return false;
            }
        } else {
            this.lastSectionKnownEmpty = true;
        }

        return super.visitTestVisible(section);
    }

    @Override
    public void visit(RenderSection section) {
        super.visit(section);
        this.lastSectionKnownEmpty = false;

        // mark all traversed sections as portals, even if they don't have terrain that needs rendering
        this.portalTree.add(section);
    }

    private boolean isRayBlockedStepped(RenderSection section) {
        // check if this section is visible through all so far traversed sections
        var x = (float) section.getCenterX();
        var y = (float) section.getCenterY();
        var z = (float) section.getCenterZ();
        var dX = (float) (this.transform.x - x);
        var dY = (float) (this.transform.y - y);
        var dZ = (float) (this.transform.z - z);

        var distanceSquared = dX * dX + dY * dY + dZ * dZ;
        if (distanceSquared < MIN_RAY_TEST_DISTANCE_SQ) {
            return false;
        }

        var length = (float) Math.sqrt(distanceSquared);
        var steps = Math.min((int) (length * RAY_MIN_STEP_SIZE_INV), RAY_TEST_MAX_STEPS);

        // avoid the last step being in the camera
        var stepsInv = 1.0f / steps;
        dX *= stepsInv;
        dY *= stepsInv;
        dZ *= stepsInv;

        for (int i = 1; i < steps; i++) {
            x += dX;
            y += dY;
            z += dZ;

            // if the section is not present in the tree, the path to the camera is blocked
            var result = this.blockHasObstruction((int) x, (int) y, (int) z);
            if (result == Tree.NOT_PRESENT) {
                // also test radius around to avoid false negatives
                var radius = SECTION_HALF_DIAGONAL * (steps - i) * stepsInv;

                // this pattern simulates a shape similar to the sweep of the section towards the camera
                if (this.blockHasObstruction((int) (x - radius), (int) (y - radius), (int) (z - radius)) != Tree.NOT_PRESENT ||
                        this.blockHasObstruction((int) (x + radius), (int) (y + radius), (int) (z + radius)) != Tree.NOT_PRESENT) {
                    continue;
                }

                // the path is blocked because there's no visited section that gives a clear line of sight
                return true;
            } else if (result == Tree.OUT_OF_BOUNDS) {
                break;
            }
        }

        return false;
    }

    private int blockHasObstruction(int x, int y, int z) {
        x >>= 4;
        y >>= 4;
        z >>= 4;

        if (y < this.minSection || y > this.maxSection) {
            return Tree.OUT_OF_BOUNDS;
        }

        return this.portalTree.getPresence(x, y, z);
    }

    private static Forest createPortalTree(int baseOffsetX,int baseOffsetY, int baseOffsetZ, float buildDistance, Level level) {
        if (BaseBiForest.checkApplicable(buildDistance, level)) {
            return new PortalBiForest(baseOffsetX, baseOffsetY, baseOffsetZ, buildDistance);
        }

        return new PortalMultiForest(baseOffsetX, baseOffsetY, baseOffsetZ, buildDistance);
    }

    private static class PortalBiForest extends BaseBiForest<FlatTree> {
        public PortalBiForest(int baseOffsetX, int baseOffsetY, int baseOffsetZ, float buildDistance) {
            super(baseOffsetX, baseOffsetY, baseOffsetZ, buildDistance);
        }

        @Override
        protected FlatTree makeTree(int offsetX, int offsetY, int offsetZ) {
            return new FlatTree(offsetX, offsetY, offsetZ);
        }
    }

    private static class PortalMultiForest extends BaseMultiForest<FlatTree> {
        public PortalMultiForest(int baseOffsetX, int baseOffsetY, int baseOffsetZ, float buildDistance) {
            super(baseOffsetX, baseOffsetY, baseOffsetZ, buildDistance);
        }

        @Override
        protected FlatTree makeTree(int offsetX, int offsetY, int offsetZ) {
            return new FlatTree(offsetX, offsetY, offsetZ);
        }

        @Override
        protected FlatTree[] makeTrees(int length) {
            return new FlatTree[length];
        }
    }

    protected static class FlatTree extends Tree {
        public FlatTree(int offsetX, int offsetY, int offsetZ) {
            super(offsetX, offsetY, offsetZ);
        }

        @Override
        public int getPresence(int x, int y, int z) {
            x -= this.offsetX;
            y -= this.offsetY;
            z -= this.offsetZ;
            if (isOutOfBounds(x, y, z)) {
                return Tree.OUT_OF_BOUNDS;
            }

            var bitIndex = interleave6x3(x, y, z);
            var mask = 1L << (bitIndex & 0b111111);
            return (this.tree[bitIndex >> 6] & mask) == 0 ? Tree.NOT_PRESENT : Tree.PRESENT;
        }
    }
}
