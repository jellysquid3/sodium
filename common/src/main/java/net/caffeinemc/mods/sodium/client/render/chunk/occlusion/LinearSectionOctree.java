package net.caffeinemc.mods.sodium.client.render.chunk.occlusion;

import net.caffeinemc.mods.sodium.client.render.chunk.RenderSection;
import net.caffeinemc.mods.sodium.client.render.chunk.RenderSectionFlags;
import net.caffeinemc.mods.sodium.client.render.chunk.lists.PendingTaskCollector;
import net.caffeinemc.mods.sodium.client.render.viewport.Viewport;
import net.minecraft.core.SectionPos;
import net.minecraft.util.Mth;
import org.joml.FrustumIntersection;

/**
 * TODO: do distance test here? what happens when the camera moves but the bfs doesn't know that? expand the distance limit?
 * ideas to prevent one frame of wrong display when BFS is recalculated but not ready yet:
 * - preemptively do the bfs from the next section the camera is going to be in, and maybe pad the render distance by how far the player can move before we need to recalculate. (if there's padding, then I guess the distance check would need to also be put in the traversal's test)
 * - a more experimental idea would be to allow the BFS to go both left and right (as it currently does in sections that are aligned with the origin section) in the sections aligned with the origin section's neighbors. This would mean we can safely use the bfs result in all neighbors, but could slightly increase the number of false positives (which is a problem already...)
 */
public class LinearSectionOctree extends PendingTaskCollector implements OcclusionCuller.GraphOcclusionVisitor {
    final Tree mainTree;
    Tree secondaryTree;
    final int baseOffsetX, baseOffsetY, baseOffsetZ;
    final int buildSectionCenterX, buildSectionCenterY, buildSectionCenterZ;

    VisibleSectionVisitor visitor;
    Viewport viewport;

    // offset is shifted by 1 to encompass all sections towards the negative
    // TODO: is this the correct way of calculating the minimum possible section index?
    private static final int TREE_OFFSET = 1;
    private static final int REUSE_MAX_DISTANCE = 8;

    public interface VisibleSectionVisitor {
        void visit(int x, int y, int z);
    }

    public LinearSectionOctree(Viewport viewport, float searchDistance) {

        var transform = viewport.getTransform();
        int offsetDistance = Mth.floor(searchDistance / 16.0f) + TREE_OFFSET;
        this.buildSectionCenterX = (transform.intX & ~0b1111) + 8;
        this.buildSectionCenterY = (transform.intY & ~0b1111) + 8;
        this.buildSectionCenterZ = (transform.intZ & ~0b1111) + 8;
        this.baseOffsetX = (transform.intX >> 4) - offsetDistance;
        this.baseOffsetY = (transform.intY >> 4) - offsetDistance;
        this.baseOffsetZ = (transform.intZ >> 4) - offsetDistance;

        this.mainTree = new Tree(this.baseOffsetX, this.baseOffsetY, this.baseOffsetZ);
    }

    public boolean isAcceptableFor(Viewport viewport) {
        var transform = viewport.getTransform();
        return Math.abs(transform.intX - this.buildSectionCenterX) <= REUSE_MAX_DISTANCE
                && Math.abs(transform.intY - this.buildSectionCenterY) <= REUSE_MAX_DISTANCE
                && Math.abs(transform.intZ - this.buildSectionCenterZ) <= REUSE_MAX_DISTANCE;
    }

    @Override
    public boolean isWithinFrustum(Viewport viewport, RenderSection section) {
        return true;
    }

    @Override
    public void visit(RenderSection section) {
        super.visit(section);

        // discard invisible or sections that don't need to be rendered
        if (!visible || (section.getRegion().getSectionFlags(section.getSectionIndex()) & RenderSectionFlags.MASK_NEEDS_RENDER) == 0) {
            return;
        }

        int x = section.getChunkX();
        int y = section.getChunkY();
        int z = section.getChunkZ();

        if (this.mainTree.add(x, y, z)) {
            if (this.secondaryTree == null) {
                // offset diagonally to fully encompass the required area
                this.secondaryTree = new Tree(this.baseOffsetX + 4, this.baseOffsetY, this.baseOffsetZ + 4);
            }
            if (this.secondaryTree.add(x, y, z)) {
                throw new IllegalStateException("Failed to add section to trees");
            }
        }
    }

    public boolean isBoxVisible(Viewport viewport, double x1, double y1, double z1, double x2, double y2, double z2) {
        if (!viewport.isBoxVisible(x1, y1, z1, x2, y2, z2)) {
            return false;
        }

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
        return this.mainTree.isSectionPresent(x, y, z) ||
                (this.secondaryTree != null && this.secondaryTree.isSectionPresent(x, y, z));
    }

    public void traverseVisible(VisibleSectionVisitor visitor, Viewport viewport) {
        this.visitor = visitor;
        this.viewport = viewport;

        this.mainTree.traverse(viewport);
        if (this.secondaryTree != null) {
            this.secondaryTree.traverse(viewport);
        }

        this.visitor = null;
        this.viewport = null;
    }

    private class Tree {
        private final long[] tree = new long[64 * 64];
        private final long[] treeReduced = new long[64];
        private long treeDoubleReduced = 0L;
        private final int offsetX, offsetY, offsetZ;

        private int cameraOffsetX, cameraOffsetY, cameraOffsetZ;

        Tree(int offsetX, int offsetY, int offsetZ) {
            this.offsetX = offsetX;
            this.offsetY = offsetY;
            this.offsetZ = offsetZ;
        }

        boolean add(int x, int y, int z) {
            x -= this.offsetX;
            y -= this.offsetY;
            z -= this.offsetZ;
            if (x > 63 || y > 63 || z > 63 || x < 0 || y < 0 || z < 0) {
                return true;
            }

            var bitIndex = interleave6x3(x, y, z);
            int reducedBitIndex = bitIndex >> 6;
            int doubleReducedBitIndex = bitIndex >> 12;
            this.tree[reducedBitIndex] |= 1L << (bitIndex & 0b111111);
            this.treeReduced[doubleReducedBitIndex] |= 1L << (reducedBitIndex & 0b111111);
            this.treeDoubleReduced |= 1L << doubleReducedBitIndex;

            return false;
        }

        private static int interleave6x3(int x, int y, int z) {
            return interleave6(x) | interleave6(y) << 1 | interleave6(z) << 2;
        }

        private static int interleave6(int n) {
            n &= 0b000000000000111111;
            n = (n | n << 8) & 0b000011000000001111;
            n = (n | n << 4) & 0b000011000011000011;
            n = (n | n << 2) & 0b001001001001001001;
            return n;
        }

        private static int deinterleave6(int n) {
            n &= 0b001001001001001001;
            n = (n | n >> 2) & 0b000011000011000011;
            n = (n | n >> 4 | n >> 8) & 0b000000000000111111;
            return n;
        }

        boolean isSectionPresent(int x, int y, int z) {
            x -= this.offsetX;
            y -= this.offsetY;
            z -= this.offsetZ;
            if (x > 63 || y > 63 || z > 63 || x < 0 || y < 0 || z < 0) {
                return false;
            }

            var bitIndex = interleave6x3(x, y, z);
            int doubleReducedBitIndex = bitIndex >> 12;
            if ((this.treeDoubleReduced & (1L << doubleReducedBitIndex)) == 0) {
                return false;
            }

            int reducedBitIndex = bitIndex >> 6;
            return (this.tree[reducedBitIndex] & (1L << (bitIndex & 0b111111))) != 0;
        }

        void traverse(Viewport viewport) {
            var transform = viewport.getTransform();

            // + 1 to section position to compensate for shifted global offset
            this.cameraOffsetX = (transform.intX >> 4) - this.offsetX + 1;
            this.cameraOffsetY = (transform.intY >> 4) - this.offsetY + 1;
            this.cameraOffsetZ = (transform.intZ >> 4) - this.offsetZ + 1;

            this.traverse(0, 0, 0, 0, 5, false);
        }

        void traverse(int nodeX, int nodeY, int nodeZ, int nodeOrigin, int level, boolean inside) {
            int childHalfDim = 1 << (level + 3); // * 16 / 2
            int orderModulator = getChildOrderModulator(nodeX, nodeY, nodeZ, childHalfDim >> 3);
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

                            if (inside || testNode(x, y, z, childHalfDim)) {
                                LinearSectionOctree.this.visitor.visit(x, y, z);
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
                // check using the single reduced bitmap
                int bitStep = 1 << (level * 3 - 6);
                long mask = (1L << bitStep) - 1;
                int startBit = (nodeOrigin >> 6) & 0b111111;
                int endBit = startBit + (bitStep << 3);
                int childOriginBase = nodeOrigin & 0b111111_000000_000000;
                long map = this.treeReduced[nodeOrigin >> 12];

                for (int bitIndex = startBit; bitIndex < endBit; bitIndex += bitStep) {
                    int childIndex = bitIndex ^ orderModulator;
                    if ((map & (mask << childIndex)) != 0) {
                        this.testChild(childOriginBase | (childIndex << 6), childHalfDim, level, inside);
                    }
                }
            } else {
                // check using the double reduced bitmap
                int bitStep = 1 << (level * 3 - 12);
                long mask = (1L << bitStep) - 1;
                int startBit = nodeOrigin >> 12;
                int endBit = startBit + (bitStep << 3);

                for (int bitIndex = startBit; bitIndex < endBit; bitIndex += bitStep) {
                    int childIndex = bitIndex ^ orderModulator;
                    if ((this.treeDoubleReduced & (mask << childIndex)) != 0) {
                        this.testChild(childIndex << 12, childHalfDim, level, inside);
                    }
                }
            }
        }

        void testChild(int childOrigin, int childDim, int level, boolean inside) {
            int x = deinterleave6(childOrigin);
            int y = deinterleave6(childOrigin >> 1);
            int z = deinterleave6(childOrigin >> 2);

            boolean intersection = false;
            if (!inside) {
                // TODO: actually measure time to generate a render list on dev and compare
                var result = intersectNode(x + this.offsetX, y + this.offsetY, z + this.offsetZ, childDim);
                inside = result == FrustumIntersection.INSIDE;
                intersection = result == FrustumIntersection.INTERSECT;
            }

            if (inside || intersection) {
                this.traverse(x, y, z, childOrigin, level - 1, inside);
            }
        }

        boolean testNode(int x, int y, int z, int childDim) {
            return LinearSectionOctree.this.viewport.isBoxVisible(
                    (x << 4) + childDim,
                    (y << 4) + childDim,
                    (z << 4) + childDim,
                    childDim + OcclusionCuller.CHUNK_SECTION_SIZE,
                    childDim + OcclusionCuller.CHUNK_SECTION_SIZE,
                    childDim + OcclusionCuller.CHUNK_SECTION_SIZE);
        }

        int intersectNode(int x, int y, int z, int childDim) {
            return LinearSectionOctree.this.viewport.getBoxIntersection(
                    (x << 4) + childDim,
                    (y << 4) + childDim,
                    (z << 4) + childDim,
                    childDim + OcclusionCuller.CHUNK_SECTION_SIZE,
                    childDim + OcclusionCuller.CHUNK_SECTION_SIZE,
                    childDim + OcclusionCuller.CHUNK_SECTION_SIZE);
        }

        int getChildOrderModulator(int x, int y, int z, int childSectionDim) {
            return (x + childSectionDim - this.cameraOffsetX) >>> 31
                    | ((y + childSectionDim - this.cameraOffsetY) >>> 31) << 1
                    | ((z + childSectionDim - this.cameraOffsetZ) >>> 31) << 2;
        }
    }

}
