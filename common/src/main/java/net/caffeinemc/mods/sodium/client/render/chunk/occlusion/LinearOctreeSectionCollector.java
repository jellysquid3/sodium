package net.caffeinemc.mods.sodium.client.render.chunk.occlusion;

import it.unimi.dsi.fastutil.longs.Long2ReferenceMap;
import net.caffeinemc.mods.sodium.client.render.chunk.RenderSection;
import net.caffeinemc.mods.sodium.client.render.viewport.Viewport;
import net.minecraft.core.SectionPos;
import net.minecraft.util.Mth;

class LinearOctreeSectionCollector extends TreeSectionCollector {
    private final Long2ReferenceMap<RenderSection> sections;
    final Tree mainTree;
    Tree secondaryTree;
    final int baseOffsetX, baseOffsetY, baseOffsetZ;

    OcclusionCuller.Visitor visitor;
    Viewport viewport;

    LinearOctreeSectionCollector(Long2ReferenceMap<RenderSection> sections, Viewport viewport, float searchDistance) {
        this.sections = sections;

        // offset is shifted by 1 to encompass all sections towards the negative
        // + 1 to block position approximate fractional part of camera position (correct?)
        // TODO: is this the correct way of calculating the minimum possible section index?
        var transform = viewport.getTransform();
        int offsetDistance = Mth.floor(searchDistance / 16.0f) + 1;
        this.baseOffsetX = ((transform.intX + 1) >> 4) - offsetDistance;
        this.baseOffsetY = ((transform.intY + 1) >> 4) - offsetDistance;
        this.baseOffsetZ = ((transform.intZ + 1) >> 4) - offsetDistance;

        this.mainTree = new Tree(this.baseOffsetX, this.baseOffsetY, this.baseOffsetZ);
    }

    @Override
    void add(RenderSection section) {
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

    @Override
    void traverseVisible(OcclusionCuller.Visitor visitor, Viewport viewport) {
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
        final int offsetX, offsetY, offsetZ;
        int cameraOffsetX, cameraOffsetY, cameraOffsetZ;

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
            n &=               0b000000000000111111;
            n = (n | n << 8) & 0b000011000000001111;
            n = (n | n << 4) & 0b000011000011000011;
            n = (n | n << 2) & 0b001001001001001001;
            return n;
        }

        private static int deinterleave6(int n) {
            n &=                        0b001001001001001001;
            n = (n | n >> 2) &          0b000011000011000011;
            n = (n | n >> 4 | n >> 8) & 0b000000000000111111;
            return n;
        }

        void traverse(Viewport viewport) {
            var transform = viewport.getTransform();

            // + 1 to block position approximate fractional part of camera position
            // + 1 to section position to compensate for shifted global offset
            this.cameraOffsetX = ((transform.intX + 1) >> 4) - this.offsetX + 1;
            this.cameraOffsetY = ((transform.intY + 1) >> 4) - this.offsetY + 1;
            this.cameraOffsetZ = ((transform.intZ + 1) >> 4) - this.offsetZ + 1;

            this.traverse(0, 0, 0, 0, 5);
        }

        void traverse(int nodeX, int nodeY, int nodeZ, int nodeOrigin, int level) {
            int childHalfDim = 1 << (level + 3); // * 16 / 2
            int orderModulator = getNodeTraversalModulator(nodeX, nodeY, nodeZ, childHalfDim >> 3);
            if ((level & 1) == 1) {
                orderModulator <<= 3;
            }

            if (level <= 1) {
                // check using the full bitmap
                int bitStep = 1 << (level * 3);
                long mask = (1L << bitStep) - 1;
                int startBit = nodeOrigin & 0b111111;
                int endBit = startBit + (bitStep << 3);
                int childOriginBase = nodeOrigin & 0b111111_111111_000000;
                long map = this.tree[nodeOrigin >> 6];

                if (level == 0) {
                    for (int bitIndex = startBit; bitIndex < endBit; bitIndex += bitStep) {
                        int childIndex = bitIndex ^ orderModulator;
                        if ((map & (mask << childIndex)) != 0) {
                            int sectionOrigin = childOriginBase | childIndex;
                            int x = deinterleave6(sectionOrigin) + this.offsetX;
                            int y = deinterleave6(sectionOrigin >> 1) + this.offsetY;
                            int z = deinterleave6(sectionOrigin >> 2) + this.offsetZ;
                            if (testNode(x, y, z, childHalfDim)) {
                                // TODO: profile if it's faster to do a hashmap lookup to get the region
                                // and then use the region's array to get the render section
                                // TODO: also profile if it's worth it to store an array of all render sections
                                // for each node to accelerate "fully inside frustum" situations,
                                // otherwise also optimize "fully inside" situations with a different traversal type
                                // NOTE: such an array would need to be traversed in the correct front-to-back order
                                // for this the sections should be in it in x, y, z order and then front-to-back iteration is easy
                                var section = LinearOctreeSectionCollector.this.sections.get(SectionPos.asLong(x, y, z));
                                LinearOctreeSectionCollector.this.visitor.visit(section, true);
                            }
                        }
                    }
                } else {
                    for (int bitIndex = startBit; bitIndex < endBit; bitIndex += bitStep) {
                        int childIndex = bitIndex ^ orderModulator;
                        if ((map & (mask << childIndex)) != 0) {
                            this.testChild(childOriginBase | childIndex, childHalfDim, level);
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
                        this.testChild(childOriginBase | (childIndex << 6), childHalfDim, level);
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
                        this.testChild(childIndex << 12, childHalfDim, level);
                    }
                }
            }
        }

        void testChild(int childOrigin, int childDim, int level) {
            int x = deinterleave6(childOrigin);
            int y = deinterleave6(childOrigin >> 1);
            int z = deinterleave6(childOrigin >> 2);
            if (testNode(x + this.offsetX, y + this.offsetY, z + this.offsetZ, childDim)) {
                this.traverse(x, y, z, childOrigin, level - 1);
            }
        }

        boolean testNode(int x, int y, int z, int childDim) {
            return LinearOctreeSectionCollector.this.viewport.isBoxVisible(
                    (x << 4) + childDim,
                    (y << 4) + childDim,
                    (z << 4) + childDim,
                    childDim + OcclusionCuller.CHUNK_SECTION_SIZE,
                    childDim + OcclusionCuller.CHUNK_SECTION_SIZE,
                    childDim + OcclusionCuller.CHUNK_SECTION_SIZE);
        }

        int getNodeTraversalModulator(int x, int y, int z, int childSections) {
            return (x + childSections - this.cameraOffsetX) >>> 31
                    | ((y + childSections - this.cameraOffsetY) >>> 31) << 1
                    | ((z + childSections - this.cameraOffsetZ) >>> 31) << 2;
        }
    }
}
