package net.caffeinemc.mods.sodium.client.render.chunk.occlusion;

import net.caffeinemc.mods.sodium.client.render.chunk.RenderSection;
import net.caffeinemc.mods.sodium.client.render.chunk.RenderSectionFlags;
import net.caffeinemc.mods.sodium.client.render.viewport.CameraTransform;
import net.caffeinemc.mods.sodium.client.render.viewport.Viewport;

public class RayOcclusionSectionTree extends SectionTree {
    private static final float SECTION_HALF_DIAGONAL = (float) Math.sqrt(8 * 8 * 3);
    private static final float RAY_MIN_STEP_SIZE_INV = 1.0f / (SECTION_HALF_DIAGONAL * 2);
    private static final int RAY_TEST_MAX_STEPS = 12;
    private static final int MIN_RAY_TEST_DISTANCE_SQ = (int) Math.pow(16 * 3, 2);

    private final CameraTransform transform;

    private final PortalMap mainPortalTree;
    private PortalMap secondaryPortalTree;

    public RayOcclusionSectionTree(Viewport viewport, float buildDistance, int frame, CullType cullType) {
        super(viewport, buildDistance, frame, cullType);

        this.transform = viewport.getTransform();
        this.mainPortalTree = new PortalMap(this.baseOffsetX, this.baseOffsetY, this.baseOffsetZ);
    }

    @Override
    public boolean visitTestVisible(RenderSection section) {
        if ((section.getRegion().getSectionFlags(section.getSectionIndex()) & RenderSectionFlags.MASK_NEEDS_RENDER) == 0) {
            this.lastSectionKnownEmpty = true;
        } else {
            this.lastSectionKnownEmpty = false;
            if (this.isRayBlockedStepped(section)) {
                return false;
            }
        }

        return super.visitTestVisible(section);
    }

    @Override
    public void visit(RenderSection section) {
        super.visit(section);
        this.lastSectionKnownEmpty = false;

        // mark all traversed sections as portals, even if they don't have terrain that needs rendering
        this.markPortal(section.getChunkX(), section.getChunkY(), section.getChunkZ());
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

            if (this.blockHasObstruction((int) x, (int) y, (int) z)) {
                // also test radius around to avoid false negatives
                var radius = SECTION_HALF_DIAGONAL * (steps - i) * stepsInv;

                // this pattern simulates a shape similar to the sweep of the section towards the camera
                if (!this.blockHasObstruction((int) (x - radius), (int) (y - radius), (int) (z - radius)) ||
                        !this.blockHasObstruction((int) (x + radius), (int) (y + radius), (int) (z + radius))) {
                    continue;
                }
                return true;
            }
        }

        return false;
    }

    protected void markPortal(int x, int y, int z) {
        if (this.mainPortalTree.add(x, y, z)) {
            if (this.secondaryPortalTree == null) {
                this.secondaryPortalTree = new PortalMap(
                        this.baseOffsetX + SECONDARY_TREE_OFFSET_XZ,
                        this.baseOffsetY,
                        this.baseOffsetZ + SECONDARY_TREE_OFFSET_XZ);
            }
            if (this.secondaryPortalTree.add(x, y, z)) {
                throw new IllegalStateException("Failed to add section to portal trees");
            }
        }
    }

    private static final int IS_OBSTRUCTED = 0;
    private static final int NOT_OBSTRUCTED = 1;
    private static final int OUT_OF_BOUNDS = 2;

    private boolean blockHasObstruction(int x, int y, int z) {
        return this.hasObstruction(x >> 4, y >> 4, z >> 4);
    }

    private boolean hasObstruction(int x, int y, int z) {
        var result = this.mainPortalTree.getObstruction(x, y, z);
        if (result == OUT_OF_BOUNDS) {
            return this.secondaryPortalTree != null &&
                    this.secondaryPortalTree.getObstruction(x, y, z) == IS_OBSTRUCTED;
        }
        return result == IS_OBSTRUCTED;
    }

    protected class PortalMap {
        protected final long[] bitmap = new long[64 * 64];
        protected final int offsetX, offsetY, offsetZ;

        public PortalMap(int offsetX, int offsetY, int offsetZ) {
            this.offsetX = offsetX;
            this.offsetY = offsetY;
            this.offsetZ = offsetZ;
        }

        public boolean add(int x, int y, int z) {
            x -= this.offsetX;
            y -= this.offsetY;
            z -= this.offsetZ;
            if (Tree.isOutOfBounds(x, y, z)) {
                return true;
            }

            var bitIndex = Tree.interleave6x3(x, y, z);
            this.bitmap[bitIndex >> 6] |= 1L << (bitIndex & 0b111111);

            return false;
        }


        public int getObstruction(int x, int y, int z) {
            x -= this.offsetX;
            y -= this.offsetY;
            z -= this.offsetZ;
            if (Tree.isOutOfBounds(x, y, z)) {
                return OUT_OF_BOUNDS;
            }

            var bitIndex = Tree.interleave6x3(x, y, z);
            var mask = 1L << (bitIndex & 0b111111);
            return (this.bitmap[bitIndex >> 6] & mask) == 0 ? IS_OBSTRUCTED : NOT_OBSTRUCTED;
        }
    }
}
