package net.caffeinemc.mods.sodium.client.render.chunk.tree;

import net.caffeinemc.mods.sodium.client.render.chunk.occlusion.OcclusionCuller;
import net.caffeinemc.mods.sodium.client.render.chunk.occlusion.SectionTree;
import net.caffeinemc.mods.sodium.client.render.viewport.Viewport;
import org.joml.FrustumIntersection;

public class TraversableTree extends Tree {
    private static final int INSIDE_FRUSTUM = 0b01;
    private static final int INSIDE_DISTANCE = 0b10;
    private static final int FULLY_INSIDE = INSIDE_FRUSTUM | INSIDE_DISTANCE;

    protected final long[] treeReduced = new long[64];
    public long treeDoubleReduced = 0L;

    // set temporarily during traversal
    private int cameraOffsetX, cameraOffsetY, cameraOffsetZ;
    private SectionTree.VisibleSectionVisitor visitor;
    protected Viewport viewport;
    private float distanceLimit;

    public TraversableTree(int offsetX, int offsetY, int offsetZ) {
        super(offsetX, offsetY, offsetZ);
    }

    public void calculateReduced() {
        long doubleReduced = 0;
        for (int i = 0; i < 64; i++) {
            long reduced = 0;
            var reducedOffset = i << 6;
            for (int j = 0; j < 64; j++) {
                reduced |= this.tree[reducedOffset + j] == 0 ? 0L : 1L << j;
            }
            this.treeReduced[i] = reduced;
            doubleReduced |= reduced == 0 ? 0L : 1L << i;
        }
        this.treeDoubleReduced = doubleReduced;
    }

    @Override
    public int getPresence(int x, int y, int z) {
        x -= this.offsetX;
        y -= this.offsetY;
        z -= this.offsetZ;
        if (isOutOfBounds(x, y, z)) {
            return OUT_OF_BOUNDS;
        }

        var bitIndex = interleave6x3(x, y, z);
        int doubleReducedBitIndex = bitIndex >> 12;
        if ((this.treeDoubleReduced & (1L << doubleReducedBitIndex)) == 0) {
            return NOT_PRESENT;
        }

        int reducedBitIndex = bitIndex >> 6;
        return (this.tree[reducedBitIndex] & (1L << (bitIndex & 0b111111))) != 0 ? PRESENT : NOT_PRESENT;
    }

    public void traverse(SectionTree.VisibleSectionVisitor visitor, Viewport viewport, float distanceLimit, float buildDistance) {
        this.visitor = visitor;
        this.viewport = viewport;
        this.distanceLimit = distanceLimit;

        var transform = viewport.getTransform();

        // + 1 to offset section position to compensate for shifted global offset
        // adjust camera block position to account for fractional part of camera position
        this.cameraOffsetX = ((transform.intX + (int) Math.signum(transform.fracX)) >> 4) - this.offsetX + 1;
        this.cameraOffsetY = ((transform.intY + (int) Math.signum(transform.fracY)) >> 4) - this.offsetY + 1;
        this.cameraOffsetZ = ((transform.intZ + (int) Math.signum(transform.fracZ)) >> 4) - this.offsetZ + 1;

        // everything is already inside the distance limit if the build distance is smaller
        var initialInside = this.distanceLimit >= buildDistance ? INSIDE_DISTANCE : 0;
        this.traverse(getChildOrderModulator(0, 0, 0, 1 << 5), 0, 5, initialInside);

        this.visitor = null;
        this.viewport = null;
    }

    void traverse(int orderModulator, int nodeOrigin, int level, int inside) {
        // half of the dimension of a child of this node, in blocks
        int childHalfDim = 1 << (level + 3); // * 16 / 2

        // odd levels (the higher levels of each reduction) need to modulate indexes that are multiples of 8
        if ((level & 1) == 1) {
            orderModulator <<= 3;
        }

        if (level <= 1) {
            // check using the full bitmap
            int childOriginBase = nodeOrigin & 0b111111_111111_000000;
            long map = this.tree[nodeOrigin >> 6];

            if (level == 0) {
                int startBit = nodeOrigin & 0b111111;
                int endBit = startBit + 8;

                for (int bitIndex = startBit; bitIndex < endBit; bitIndex++) {
                    int childIndex = bitIndex ^ orderModulator;
                    if ((map & (1L << childIndex)) != 0) {
                        int sectionOrigin = childOriginBase | childIndex;
                        int x = deinterleave6(sectionOrigin) + this.offsetX;
                        int y = deinterleave6(sectionOrigin >> 1) + this.offsetY;
                        int z = deinterleave6(sectionOrigin >> 2) + this.offsetZ;

                        if (inside == FULLY_INSIDE || testLeafNode(x, y, z, inside)) {
                            this.visitor.visit(x, y, z);
                        }
                    }
                }
            } else {
                for (int bitIndex = 0; bitIndex < 64; bitIndex += 8) {
                    int childIndex = bitIndex ^ orderModulator;
                    if ((map & (0xFFL << childIndex)) != 0) {
                        this.testChild(childOriginBase | childIndex, childHalfDim, level, inside);
                    }
                }
            }
        } else if (level <= 3) {
            int childOriginBase = nodeOrigin & 0b111111_000000_000000;
            long map = this.treeReduced[nodeOrigin >> 12];

            if (level == 2) {
                int startBit = (nodeOrigin >> 6) & 0b111111;
                int endBit = startBit + 8;

                for (int bitIndex = startBit; bitIndex < endBit; bitIndex++) {
                    int childIndex = bitIndex ^ orderModulator;
                    if ((map & (1L << childIndex)) != 0) {
                        this.testChild(childOriginBase | (childIndex << 6), childHalfDim, level, inside);
                    }
                }
            } else {
                for (int bitIndex = 0; bitIndex < 64; bitIndex += 8) {
                    int childIndex = bitIndex ^ orderModulator;
                    if ((map & (0xFFL << childIndex)) != 0) {
                        this.testChild(childOriginBase | (childIndex << 6), childHalfDim, level, inside);
                    }
                }
            }
        } else {
            if (level == 4) {
                int startBit = nodeOrigin >> 12;
                int endBit = startBit + 8;

                for (int bitIndex = startBit; bitIndex < endBit; bitIndex++) {
                    int childIndex = bitIndex ^ orderModulator;
                    if ((this.treeDoubleReduced & (1L << childIndex)) != 0) {
                        this.testChild(childIndex << 12, childHalfDim, level, inside);
                    }
                }
            } else {
                for (int bitIndex = 0; bitIndex < 64; bitIndex += 8) {
                    int childIndex = bitIndex ^ orderModulator;
                    if ((this.treeDoubleReduced & (0xFFL << childIndex)) != 0) {
                        this.testChild(childIndex << 12, childHalfDim, level, inside);
                    }
                }
            }
        }
    }

    void testChild(int childOrigin, int childHalfDim, int level, int inside) {
        // calculate section coordinates in tree-space
        int x = deinterleave6(childOrigin);
        int y = deinterleave6(childOrigin >> 1);
        int z = deinterleave6(childOrigin >> 2);

        // immediately traverse if fully inside
        if (inside == FULLY_INSIDE) {
            level--;
            this.traverse(getChildOrderModulator(x, y, z, 1 << level), childOrigin, level, inside);
            return;
        }

        // convert to world-space section origin in blocks, then to camera space
        var transform = this.viewport.getTransform();
        int worldX = ((x + this.offsetX) << 4) - transform.intX;
        int worldY = ((y + this.offsetY) << 4) - transform.intY;
        int worldZ = ((z + this.offsetZ) << 4) - transform.intZ;

        boolean visible = true;

        if ((inside & INSIDE_FRUSTUM) == 0) {
            var intersectionResult = this.viewport.getBoxIntersectionDirect(
                    (worldX + childHalfDim) - transform.fracX,
                    (worldY + childHalfDim) - transform.fracY,
                    (worldZ + childHalfDim) - transform.fracZ,
                    childHalfDim + OcclusionCuller.CHUNK_SECTION_MARGIN);
            if (intersectionResult == FrustumIntersection.INSIDE) {
                inside |= INSIDE_FRUSTUM;
            } else {
                visible = intersectionResult == FrustumIntersection.INTERSECT;
            }
        }

        if ((inside & INSIDE_DISTANCE) == 0) {
            // calculate the point of the node closest to the camera
            int childFullDim = childHalfDim << 1;
            float dx = nearestToZero(worldX, worldX + childFullDim) - transform.fracX;
            float dy = nearestToZero(worldY, worldY + childFullDim) - transform.fracY;
            float dz = nearestToZero(worldZ, worldZ + childFullDim) - transform.fracZ;

            // check if closest point inside the cylinder
            visible = cylindricalDistanceTest(dx, dy, dz, this.distanceLimit);
            if (visible) {
                // if the farthest point is also visible, the node is fully inside
                dx = farthestFromZero(worldX, worldX + childFullDim) - transform.fracX;
                dy = farthestFromZero(worldY, worldY + childFullDim) - transform.fracY;
                dz = farthestFromZero(worldZ, worldZ + childFullDim) - transform.fracZ;

                if (cylindricalDistanceTest(dx, dy, dz, this.distanceLimit)) {
                    inside |= INSIDE_DISTANCE;
                }
            }
        }

        if (visible) {
            level--;
            this.traverse(getChildOrderModulator(x, y, z, 1 << level), childOrigin, level, inside);
        }
    }

    boolean testLeafNode(int x, int y, int z, int inside) {
        // input coordinates are section coordinates in world-space

        var transform = this.viewport.getTransform();

        // convert to blocks and move into integer camera space
        x = (x << 4) - transform.intX;
        y = (y << 4) - transform.intY;
        z = (z << 4) - transform.intZ;

        // test frustum if not already inside frustum
        if ((inside & INSIDE_FRUSTUM) == 0 && !this.viewport.isBoxVisibleDirect(
                (x + 8) - transform.fracX,
                (y + 8) - transform.fracY,
                (z + 8) - transform.fracZ,
                OcclusionCuller.CHUNK_SECTION_RADIUS)) {
            return false;
        }

        // test distance if not already inside distance
        if ((inside & INSIDE_DISTANCE) == 0) {
            // coordinates of the point to compare (in view space)
            // this is the closest point within the bounding box to the center (0, 0, 0)
            float dx = nearestToZero(x, x + 16) - transform.fracX;
            float dy = nearestToZero(y, y + 16) - transform.fracY;
            float dz = nearestToZero(z, z + 16) - transform.fracZ;

            return cylindricalDistanceTest(dx, dy, dz, this.distanceLimit);
        }

        return true;
    }

    static boolean cylindricalDistanceTest(float dx, float dy, float dz, float distanceLimit) {
        // vanilla's "cylindrical fog" algorithm
        // max(length(distance.xz), abs(distance.y))
        return (((dx * dx) + (dz * dz)) < (distanceLimit * distanceLimit)) &&
                (Math.abs(dy) < distanceLimit);
    }

    @SuppressWarnings("ManualMinMaxCalculation") // we know what we are doing.
    private static int nearestToZero(int min, int max) {
        // this compiles to slightly better code than Math.min(Math.max(0, min), max)
        int clamped = 0;
        if (min > 0) {
            clamped = min;
        }
        if (max < 0) {
            clamped = max;
        }
        return clamped;
    }

    private static int farthestFromZero(int min, int max) {
        int clamped = 0;
        if (min > 0) {
            clamped = max;
        }
        if (max < 0) {
            clamped = min;
        }
        if (clamped == 0) {
            if (Math.abs(min) > Math.abs(max)) {
                clamped = min;
            } else {
                clamped = max;
            }
        }
        return clamped;
    }

    int getChildOrderModulator(int x, int y, int z, int childFullSectionDim) {
        return (x + childFullSectionDim - this.cameraOffsetX) >>> 31
                | ((y + childFullSectionDim - this.cameraOffsetY) >>> 31) << 1
                | ((z + childFullSectionDim - this.cameraOffsetZ) >>> 31) << 2;
    }
}
