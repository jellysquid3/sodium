package net.caffeinemc.mods.sodium.client.render.chunk.translucent_sorting.bsp_tree;

import it.unimi.dsi.fastutil.ints.Int2IntOpenHashMap;
import it.unimi.dsi.fastutil.ints.IntArrayList;
import it.unimi.dsi.fastutil.ints.IntConsumer;
import it.unimi.dsi.fastutil.ints.IntOpenHashSet;
import it.unimi.dsi.fastutil.longs.LongArrayList;
import it.unimi.dsi.fastutil.objects.ReferenceArrayList;
import net.caffeinemc.mods.sodium.client.model.quad.properties.ModelQuadFacing;
import net.caffeinemc.mods.sodium.client.render.chunk.translucent_sorting.TQuad;
import net.caffeinemc.mods.sodium.client.render.chunk.translucent_sorting.TranslucentGeometryCollector;
import net.caffeinemc.mods.sodium.client.render.chunk.translucent_sorting.data.TopoGraphSorting;
import net.caffeinemc.mods.sodium.client.util.MathUtil;
import net.caffeinemc.mods.sodium.client.util.sorting.RadixSort;
import net.minecraft.util.Mth;
import org.joml.Vector3fc;

import java.util.Arrays;
import java.util.Random;

/**
 * Performs aligned BSP partitioning of many nodes and constructs appropriate
 * BSP nodes based on the result.
 * <p>
 * Implementation notes:
 * - Presorting the points in block-sized buckets doesn't help. It seems the
 * sort algorithm is just fast enough to handle this.
 * - Eliminating the use of partition objects doesn't help. Since there's
 * usually just very few partitions, it's not worth it, it seems.
 * - Using fastutil's LongArrays sorting options (radix and quicksort) is slower
 * than using Arrays.sort (which uses DualPivotQuicksort internally), even on
 * worlds with player-built structures.
 * - A simple attempt at lazily writing index data to the buffer didn't yield a
 * performance improvement. Maybe applying it to the multi partition node would
 * be more effective (but also much more complex and slower).
 * <p>
 * The encoding doesn't currently support negative distances (nor does such
 * support appear to be required). Their ordering is wrong when sorting them by
 * their binary representation. To fix this: "XOR all positive numbers with
 * 0x8000... and negative numbers with 0xffff... This should flip the sign bit
 * on both (so negative numbers go first), and then reverse the ordering on
 * negative numbers." from <a href="https://stackoverflow.com/q/43299299">StackOverflow</a>
 * <p>
 * When aligned partitioning fails the geometry is checked for intersection. If
 * there is intersection it means the section is unsortable and an approximation
 * is used instead. When it doesn't intersect but is not aligned partitionable,
 * it either requires unaligned partitioning (a hard problem not solved here)
 * or it's unpartitionable. It would be possible to insert a topo sorting node
 * here, but it's not worth the implementation effort unless it's found to be a
 * reasonable and common use case (I haven't been able to determine that it is).
 */
abstract class InnerPartitionBSPNode extends BSPNode {
    private static final int NODE_REUSE_THRESHOLD = 30;
    private static final int MAX_INTERSECTION_ATTEMPTS = 500;

    final Vector3fc planeNormal;
    final int axis;

    int[] indexMap;
    int fixedIndexOffset = BSPSortState.NO_FIXED_OFFSET;
    final NodeReuseData reuseData; // nullable

    /**
     * Stores data required for testing if the node can be re-used. This data is
     * only generated for select candidate nodes.
     * <p>
     * It only stores the set of indexes that this node was constructed from and
     * their extents since the BSP construction only cares about the "opaque" quad
     * geometry and not the normal or facing.
     * <p>
     * Since the indexes might be compressed, the count needs to be stored
     * separately from before compression.
     */
    record NodeReuseData(float[][] quadExtents, int[] indexes, int indexCount, int maxIndex) {
    }

    InnerPartitionBSPNode(NodeReuseData reuseData, int axis) {
        this.planeNormal = ModelQuadFacing.ALIGNED_NORMALS[axis];
        this.axis = axis;
        this.reuseData = reuseData;
    }

    abstract void addPartitionPlanes(BSPWorkspace workspace);

    static NodeReuseData prepareNodeReuse(BSPWorkspace workspace, IntArrayList indexes, int depth) {
        // if node reuse is enabled, only enable on the first level of children (not the
        // root node and not anything deeper than its children)
        if (workspace.prepareNodeReuse && depth == 1 && indexes.size() > NODE_REUSE_THRESHOLD) {
            // collect the extents of the indexed quads and hash them
            var quadExtents = new float[indexes.size()][];
            int maxIndex = -1;
            for (int i = 0; i < indexes.size(); i++) {
                var index = indexes.getInt(i);
                var quad = workspace.quads[index];
                var extents = quad.getExtents();
                quadExtents[i] = extents;
                maxIndex = Math.max(maxIndex, index);
            }

            // compress indexes but without sorting them, as the order needs to be the same
            // for the extents comparison loop to work
            return new NodeReuseData(
                    quadExtents,
                    BSPSortState.compressIndexes(indexes, false),
                    indexes.size(),
                    maxIndex);
        }
        return null;
    }

    private static class IndexRemapper implements IntConsumer {
        private final int[] indexMap;
        private final IntArrayList newIndexes;
        private int index = 0;
        private int firstOffset = 0;

        private static final int OFFSET_CHANGED = Integer.MIN_VALUE;

        IndexRemapper(int length, IntArrayList newIndexes) {
            this.indexMap = new int[length];
            this.newIndexes = newIndexes;
        }

        @Override
        public void accept(int oldIndex) {
            var newIndex = this.newIndexes.getInt(this.index);
            this.indexMap[oldIndex] = newIndex;
            var newOffset = newIndex - oldIndex;
            if (this.index == 0) {
                this.firstOffset = newOffset;
            } else if (this.firstOffset != newOffset) {
                this.firstOffset = OFFSET_CHANGED;
            }
            this.index++;
        }

        boolean hasFixedOffset() {
            return this.firstOffset != OFFSET_CHANGED;
        }
    }

    static InnerPartitionBSPNode attemptNodeReuse(BSPWorkspace workspace, IntArrayList newIndexes, InnerPartitionBSPNode oldNode) {
        if (oldNode == null) {
            return null;
        }

        oldNode.indexMap = null;
        oldNode.fixedIndexOffset = BSPSortState.NO_FIXED_OFFSET;

        var reuseData = oldNode.reuseData;
        if (reuseData == null) {
            return null;
        }

        var oldExtents = reuseData.quadExtents;
        if (oldExtents.length != newIndexes.size()) {
            return null;
        }

        for (int i = 0; i < newIndexes.size(); i++) {
            if (!workspace.quads[newIndexes.getInt(i)].extentsEqual(oldExtents[i])) {
                return null;
            }
        }

        // reuse old node and either apply a fixed offset or calculate an index map to
        // map from old to new indices
        var remapper = new IndexRemapper(reuseData.maxIndex + 1, newIndexes);
        BSPSortState.decompressOrRead(reuseData.indexes, remapper);

        // use a fixed offset if possible (if all old indices differ from the new ones
        // by the same amount)
        if (remapper.hasFixedOffset()) {
            oldNode.fixedIndexOffset = remapper.firstOffset;
        } else {
            oldNode.indexMap = remapper.indexMap;
        }

        // import the triggering data from the old node to ensure it still triggers at
        // the right time
        oldNode.addPartitionPlanes(workspace);

        return oldNode;
    }

    /**
     * Encoding with {@link MathUtil#floatToComparableInt(float)} is necessary here to ensure negative distances are not sorted backwards. Simply converting potentially negative floats to int bits using {@link Float#floatToRawIntBits(float)} would sort negative floats backwards amongst themselves.
     * <p>
     * Note that negative floats convert to negative integers with this method which is ok, since it yields an overall negative long that gets sorted correctly before the longs that encode positive floats as distances.
     */
    private static long encodeIntervalPoint(float distance, int quadIndex, int type) {
        return ((long) MathUtil.floatToComparableInt(distance) << 32) | ((long) type << 30) | quadIndex;
    }

    private static float decodeDistance(long encoded) {
        return MathUtil.comparableIntToFloat((int) (encoded >>> 32));
    }

    private static int decodeQuadIndex(long encoded) {
        return (int) (encoded & 0x3FFFFFFF);
    }

    private static int decodeType(long encoded) {
        return (int) (encoded >>> 30) & 0b11;
    }

    public static void validateQuadCount(int quadCount) {
        if (quadCount * 2 > 0x3FFFFFFF) {
            throw new IllegalArgumentException("Too many quads: " + quadCount);
        }
    }

    // the indices of the type are chosen such that tie-breaking items that have the
    // same distance with the type ascending yields a beneficial sort order
    // (END of the current interval, on-edge quads, then the START of the next
    // interval)

    // the start of a quad's extent in this direction
    private static final int INTERVAL_START = 2;

    // the end of a quad's extent in this direction
    private static final int INTERVAL_END = 0;

    // looking at a quad from the side where it has zero thickness
    private static final int INTERVAL_SIDE = 1;

    static BSPNode build(BSPWorkspace workspace, IntArrayList indexes, int depth, BSPNode oldNode) {
        // attempt reuse of the old node if possible
        if (oldNode instanceof InnerPartitionBSPNode oldInnerNode) {
            var reusedNode = InnerPartitionBSPNode.attemptNodeReuse(workspace, indexes, oldInnerNode);
            if (reusedNode != null) {
                return reusedNode;
            }
        }

        ReferenceArrayList<Partition> partitions = new ReferenceArrayList<>();
        LongArrayList points = new LongArrayList((int) (indexes.size() * 1.5));

        // find any aligned partition, search each axis
        for (int axisCount = 0; axisCount < 3; axisCount++) {
            int axis = (axisCount + depth + 1) % 3;
            var oppositeDirection = axis + 3;
            int alignedFacingBitmap = 0;
            boolean onlyIntervalSide = true;

            // collect all the geometry's start and end points in this direction
            points.clear();
            for (int quadIndex : indexes) {
                var quad = workspace.quads[quadIndex];
                var extents = quad.getExtents();
                var posExtent = extents[axis];
                var negExtent = extents[oppositeDirection];
                if (posExtent == negExtent) {
                    points.add(encodeIntervalPoint(posExtent, quadIndex, INTERVAL_SIDE));
                } else {
                    points.add(encodeIntervalPoint(posExtent, quadIndex, INTERVAL_END));
                    points.add(encodeIntervalPoint(negExtent, quadIndex, INTERVAL_START));
                    onlyIntervalSide = false;
                }

                alignedFacingBitmap |= 1 << quad.getFacing().ordinal();
            }

            // simplified SNR heuristic as seen in TranslucentGeometryCollector#sortTypeHeuristic (case D)
            if (!ModelQuadFacing.bitmapHasUnassigned(alignedFacingBitmap)) {
                int alignedNormalCount = Integer.bitCount(alignedFacingBitmap);
                if (alignedNormalCount == 1 || alignedNormalCount == 2 && ModelQuadFacing.bitmapIsOpposingAligned(alignedFacingBitmap)) {
                    // this can be handled with SNR instead of partitioning,
                    // instead create a fixed order node that uses SNR sorting

                    // check if the geometry is aligned to the axis
                    if (onlyIntervalSide) {
                        // this means the already generated points array can be used
                        return buildSNRLeafNodeFromPoints(workspace, points);
                    } else {
                        return buildSNRLeafNodeFromQuads(workspace, indexes, points);
                    }
                }
            }

            // sort interval points by distance ascending and then by type. Sorting the
            // longs directly has the same effect because of the encoding.
            Arrays.sort(points.elements(), 0, points.size());

            // find gaps
            partitions.clear();
            float distance = Float.NaN;
            IntArrayList quadsBefore = null;
            IntArrayList quadsOn = null;
            int thickness = 0;
            for (long point : points) {
                switch (decodeType(point)) {
                    case INTERVAL_START -> {
                        // unless at the start, flush if there's a gap
                        if (thickness == 0 && (quadsBefore != null || quadsOn != null)) {
                            partitions.add(new Partition(distance, quadsBefore, quadsOn));
                            distance = Float.NaN;
                            quadsBefore = null;
                            quadsOn = null;
                        }

                        thickness++;

                        // flush to partition if still writing last partition
                        if (quadsOn != null) {
                            if (Float.isNaN(distance)) {
                                throw new IllegalStateException("distance not set");
                            }
                            partitions.add(new Partition(distance, quadsBefore, quadsOn));
                            distance = Float.NaN;
                            quadsOn = null;
                        }
                        if (quadsBefore == null) {
                            quadsBefore = new IntArrayList();
                        }
                        quadsBefore.add(decodeQuadIndex(point));
                    }
                    case INTERVAL_END -> {
                        thickness--;
                        if (quadsOn == null) {
                            distance = decodeDistance(point);
                        }
                    }
                    case INTERVAL_SIDE -> {
                        // if this point in a gap, it can be put on the plane itself
                        int pointQuadIndex = decodeQuadIndex(point);
                        if (thickness == 0) {
                            float pointDistance = decodeDistance(point);
                            if (quadsOn == null) {
                                // no partition end created yet, set here
                                quadsOn = new IntArrayList();
                                distance = pointDistance;
                            } else if (distance != pointDistance) {
                                // partition end has passed already, flush for new partition plane distance
                                partitions.add(new Partition(distance, quadsBefore, quadsOn));
                                distance = pointDistance;
                                quadsBefore = null;
                                quadsOn = new IntArrayList();
                            }
                            quadsOn.add(pointQuadIndex);
                        } else {
                            if (quadsBefore == null) {
                                throw new IllegalStateException("there must be started intervals here");
                            }
                            quadsBefore.add(pointQuadIndex);
                            if (quadsOn == null) {
                                distance = decodeDistance(point);
                            }
                        }
                    }
                }
            }

            // check a different axis if everything is in one quadsBefore,
            // which means there are no gaps
            if (quadsBefore != null && quadsBefore.size() == indexes.size()) {
                continue;
            }

            // check if there's a trailing plane. Otherwise, the last plane has distance -1
            // since it just holds the trailing quads
            boolean endsWithPlane = quadsOn != null;

            // flush the last partition, use the -1 distance to indicate the end if it
            // doesn't use quadsOn (which requires a certain distance to be given)
            if (quadsBefore != null || quadsOn != null) {
                partitions.add(new Partition(endsWithPlane ? distance : Float.NaN, quadsBefore, quadsOn));
            }

            // check if this can be turned into a binary partition node
            // (if there's at most two partitions and one plane)
            if (partitions.size() <= 2) {
                // get the two partitions
                var inside = partitions.get(0);
                var outside = partitions.size() == 2 ? partitions.get(1) : null;
                if (outside == null || !endsWithPlane) {
                    return InnerBinaryPartitionBSPNode.buildFromPartitions(workspace, indexes, depth, oldNode,
                            inside, outside, axis);
                }
            }

            // create a multi-partition node
            return InnerMultiPartitionBSPNode.buildFromPartitions(workspace, indexes, depth, oldNode,
                    partitions, axis, endsWithPlane);
        }

        var intersectingHandling = handleIntersecting(workspace, indexes, depth, oldNode);
        if (intersectingHandling != null) {
            return intersectingHandling;
        }

        // attempt topo sorting on the geometry if intersection handling failed
        var multiLeafNode = buildTopoMultiLeafNode(workspace, indexes);
        if (multiLeafNode == null) {
            throw new BSPBuildFailureException("No partition found but not intersecting and can't be statically topo sorted");
        }
        return multiLeafNode;
    }

    static private BSPNode handleIntersecting(BSPWorkspace workspace, IntArrayList indexes, int depth, BSPNode oldNode) {
        Int2IntOpenHashMap intersectionCounts = null;
        IntOpenHashSet primaryIntersectorIndexes = null;
        int primaryIntersectorThreshold = Mth.clamp(indexes.size() / 2, 2, 4);

        int i = -1;
        int j = 0;
        final int quadCount = indexes.size();
        int stepSize = Math.max(1, (quadCount * (quadCount - 1) / 2) / MAX_INTERSECTION_ATTEMPTS);
        int variance = 0;

        // if doing random stepping, subtract some and calculate the variance to apply
        Random random = null;
        if (stepSize > 1) {
            int half = stepSize / 2;
            stepSize = Math.max(1, stepSize - half);
            variance = stepSize;
            random = new Random();
        }

        while (true) {
            // pick indexes in serial fashion without repeating pairs (i < j always holds)
            i += stepSize;
            if (variance > 0) {
                i += random.nextInt(variance);
            }

            // step i and j until they're valid indexes with i < j
            while (i >= j) {
                i -= j;
                j++;
            }

            // stop if we're out of indexes
            if (j >= indexes.size()) {
                break;
            }

            var quadA = workspace.quads[indexes.getInt(i)];
            var quadB = workspace.quads[indexes.getInt(j)];

            // aligned quads intersect if their bounding boxes intersect
            if (TQuad.extentsIntersect(quadA, quadB)) {
                if (intersectionCounts == null) {
                    intersectionCounts = new Int2IntOpenHashMap();
                }

                int aCount = intersectionCounts.get(i) + 1;
                intersectionCounts.put(i, aCount);
                int bCount = intersectionCounts.get(j) + 1;
                intersectionCounts.put(j, bCount);

                if (aCount >= primaryIntersectorThreshold) {
                    if (primaryIntersectorIndexes == null) {
                        primaryIntersectorIndexes = new IntOpenHashSet(2);
                    }
                    primaryIntersectorIndexes.add(i);
                }
                if (bCount >= primaryIntersectorThreshold) {
                    if (primaryIntersectorIndexes == null) {
                        primaryIntersectorIndexes = new IntOpenHashSet(2);
                    }
                    primaryIntersectorIndexes.add(j);
                }

                // cancel primary intersector search if they all intersect with each other
                if (primaryIntersectorIndexes != null && primaryIntersectorIndexes.size() == indexes.size()) {
                    // return multi leaf node as this is impossible to sort
                    return new LeafMultiBSPNode(BSPSortState.compressIndexes(indexes));
                }
            }
        }

        if (primaryIntersectorIndexes != null) {
            // put the primary intersectors in a separate node that's always rendered last
            var nonPrimaryIntersectors = new IntArrayList(indexes.size() - primaryIntersectorIndexes.size());
            var primaryIntersectorQuadIndexes = new IntArrayList(primaryIntersectorIndexes.size());
            for (int k = 0; k < indexes.size(); k++) {
                if (primaryIntersectorIndexes.contains(k)) {
                    primaryIntersectorQuadIndexes.add(indexes.getInt(k));
                } else {
                    nonPrimaryIntersectors.add(indexes.getInt(k));
                }
            }
            return InnerFixedDoubleBSPNode.buildFromParts(workspace, indexes, depth, oldNode,
                    nonPrimaryIntersectors, primaryIntersectorQuadIndexes);
        }

        // this means we didn't manage to find primary intersectors
        return null;
    }

    private static class QuadIndexConsumerIntoArray implements IntConsumer {
        final int[] indexes;
        private int index = 0;

        QuadIndexConsumerIntoArray(int size) {
            this.indexes = new int[size];
        }

        @Override
        public void accept(int value) {
            this.indexes[this.index++] = value;
        }
    }

    static private BSPNode buildTopoMultiLeafNode(BSPWorkspace workspace, IntArrayList indexes) {
        var quadCount = indexes.size();

        if (quadCount > TranslucentGeometryCollector.STATIC_TOPO_UNKNOWN_FALLBACK_LIMIT) {
            return null;
        }

        var quads = new TQuad[quadCount];
        var activeToRealIndex = new int[quadCount];
        for (int i = 0; i < indexes.size(); i++) {
            var quadIndex = indexes.getInt(i);
            quads[i] = workspace.quads[quadIndex];
            activeToRealIndex[i] = quadIndex;
        }

        var indexWriter = new QuadIndexConsumerIntoArray(quadCount);
        if (!TopoGraphSorting.topoGraphSort(indexWriter, quads, quads.length, activeToRealIndex, null, null)) {
            return null;
        }

        // no need to add the geometry to the workspace's trigger registry
        // since it's being sorted statically and the sort order won't change based on the camera position

        return new LeafMultiBSPNode(BSPSortState.compressIndexesInPlace(indexWriter.indexes, false));
    }

    static private BSPNode buildSNRLeafNodeFromQuads(BSPWorkspace workspace, IntArrayList indexes, LongArrayList points) {
        final var indexBuffer = indexes.elements();
        final var indexCount = indexes.size();

        final var keys = new int[indexCount];
        final var perm = new int[indexCount];

        for (int i = 0; i < indexCount; i++) {
            TQuad quad = workspace.quads[indexBuffer[i]];
            keys[i] = MathUtil.floatToComparableInt(quad.getAccurateDotProduct());
            perm[i] = i;
        }

        RadixSort.sortIndirect(perm, keys);

        for (int i = 0; i < indexCount; i++) {
            perm[i] = indexBuffer[perm[i]];
        }

        return new LeafMultiBSPNode(BSPSortState.compressIndexes(IntArrayList.wrap(perm), false));
    }

    static private BSPNode buildSNRLeafNodeFromPoints(BSPWorkspace workspace, LongArrayList points) {
        // also sort by ascending encoded point but then process as an SNR result
        Arrays.sort(points.elements(), 0, points.size());

        // since the quads are aligned and are all INTERVAL_SIDE, there's no issues with duplicates.
        // the length of the array is exactly how many quads there are.
        int[] quadIndexes = new int[points.size()];
        int forwards = 0;
        int backwards = quadIndexes.length - 1;
        for (int i = 0; i < points.size(); i++) {
            // based one each quad's facing, order them forwards or backwards,
            // this means forwards is written from the start and backwards is written from the end
            var quadIndex = decodeQuadIndex(points.getLong(i));
            if (workspace.quads[quadIndex].getFacing().getSign() == 1) {
                quadIndexes[forwards++] = quadIndex;
            } else {
                quadIndexes[backwards--] = quadIndex;
            }
        }

        return new LeafMultiBSPNode(BSPSortState.compressIndexes(IntArrayList.wrap(quadIndexes), false));
    }
}
