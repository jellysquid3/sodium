package net.caffeinemc.mods.sodium.client.render.chunk.translucent_sorting.data;

import it.unimi.dsi.fastutil.objects.Object2ReferenceOpenHashMap;
import net.caffeinemc.mods.sodium.client.model.quad.properties.ModelQuadFacing;
import net.caffeinemc.mods.sodium.client.render.chunk.translucent_sorting.AlignableNormal;
import net.caffeinemc.mods.sodium.client.render.chunk.translucent_sorting.TQuad;
import net.caffeinemc.mods.sodium.client.util.collections.BitArray;
import org.joml.Vector3fc;

import java.util.function.IntConsumer;

/**
 * This class contains the sorting algorithms that do topological or distance
 * sorting. The algorithms are combined in this class to separate them from the
 * general management code in other classes.
 * <p>
 * Rough attempts at underapproximation of the visibility graph where the
 * conditions for visibility between quads are made more strict did not yield
 * significantly more robust topo sorting.
 */
public class TopoGraphSorting {
    private static final float HALF_SPACE_EPSILON = 0.001f;

    private TopoGraphSorting() {
    }

    /**
     * Test if the given point is within the half space defined by the plane anchor
     * and the plane normal. The normal points away from the space considered to be
     * inside.
     *
     * @param planeDistance dot product of the plane
     * @param planeNormal   the normal of the plane
     * @param point         the point to test
     */
    private static boolean pointOutsideHalfSpace(float planeDistance, Vector3fc planeNormal, Vector3fc point) {
        return planeNormal.dot(point) > planeDistance;
    }

    /**
     * Test if the given point is within the half space defined by the plane anchor and the plane normal. The normal points away from the space considered to be inside.
     * <p>
     * A small epsilon is added in the test to account for floating point errors, making it harder for a point on the edge to be considered inside.
     *
     * @param planeDistance dot product of the plane
     * @param planeNormal   the normal of the plane
     * @param x             x coordinate of the point
     * @param y             y coordinate of the point
     * @param z             z coordinate of the point
     * @return true if the point is inside the half space
     */
    private static boolean pointInsideHalfSpaceEpsilon(float planeDistance, Vector3fc planeNormal, float x, float y, float z) {
        return planeNormal.dot(x, y, z) + HALF_SPACE_EPSILON < planeDistance;
    }

    /**
     * Test if the given point is outside the half space defined by the plane anchor and the plane normal. The normal points away from the space considered to be inside.
     * <p>
     * A small epsilon is subtracted in the test to account for floating point errors, making it harder for a point on the edge to be considered outside.
     *
     * @param planeDistance dot product of the plane
     * @param planeNormal   the normal of the plane
     * @param x             x coordinate of the point
     * @param y             y coordinate of the point
     * @param z             z coordinate of the point
     * @return true if the point is inside the half space
     */
    private static boolean pointOutsideHalfSpaceEpsilon(float planeDistance, Vector3fc planeNormal, float x, float y, float z) {
        return planeNormal.dot(x, y, z) - HALF_SPACE_EPSILON > planeDistance;
    }

    public static boolean orthogonalQuadVisibleThrough(TQuad quadA, TQuad quadB) {
        var aDirection = quadA.getFacing().ordinal();
        var aOpposite = quadA.getFacing().getOpposite().ordinal();
        var bDirection = quadB.getFacing().ordinal();
        var aSign = quadA.getFacing().getSign();
        var bSign = quadB.getFacing().getSign();

        var aExtents = quadA.getExtents();
        var bExtents = quadB.getExtents();

        // test that B has an extent within A's half space and that A is not fully within B's half space
        float BIntoADescent = aSign * aExtents[aDirection] - aSign * bExtents[aOpposite];
        float AOutsideBAscent = bSign * aExtents[bDirection] - bSign * bExtents[bDirection];

        var vis = BIntoADescent > 0 && AOutsideBAscent > 0;

        // if they're visible and their bounding boxes intersect and apply a heuristic to resolve
        if (vis && TQuad.extentsIntersect(aExtents, bExtents)) {
            return BIntoADescent + AOutsideBAscent > 1;
        }
        return vis;
    }

    private static boolean testSeparatorRange(Object2ReferenceOpenHashMap<Vector3fc, float[]> distancesByNormal,
                                              Vector3fc normal, float start, float end) {
        var distances = distancesByNormal.get(normal);
        if (distances == null) {
            return false;
        }
        return AlignableNormal.queryRange(distances, start, end);
    }

    private static boolean visibilityWithSeparator(TQuad quadA, TQuad quadB,
                                                   Object2ReferenceOpenHashMap<Vector3fc, float[]> distancesByNormal, Vector3fc cameraPos) {
        // check if there is an aligned separator
        for (int direction = 0; direction < ModelQuadFacing.DIRECTIONS; direction++) {
            var facing = ModelQuadFacing.VALUES[direction];
            var oppositeFacing = facing.getOpposite();
            var oppositeDirection = oppositeFacing.ordinal();
            var sign = facing.getSign();

            // test that they're not overlapping in this direction. Multiplication with the
            // sign makes the > work in the other direction which is necessary since the
            // facing turns the whole space around. The start and end are ordered along the
            // < relation as is the normal. The normal always points in the direction of
            // greater values, even if all the geometry has negative values.
            var separatorRangeStart = sign * quadB.getExtents()[direction];
            var separatorRangeEnd = sign * quadA.getExtents()[oppositeDirection];
            if (separatorRangeStart > separatorRangeEnd) {
                continue;
            }

            // test that the camera doesn't exclude all separators
            var facingNormal = ModelQuadFacing.ALIGNED_NORMALS[direction];
            var cameraDistance = facingNormal.dot(cameraPos);
            if (cameraDistance > separatorRangeEnd) {
                continue;
            }

            // use camera distance as the start because even if there's no geometry that
            // generates such separator plane itself, if there's any plane that triggers the
            // section before the camera can see B through A, this is enough. The separator
            // doesn't need to be between B and A if the camera will cross another separator
            // before any separator that could be between B and A.
            separatorRangeStart = cameraDistance;

            // swapping the start and end is not necessary since the start is always smaller
            // than the end value.

            // test if there is a separator plane that is outside/on the surface of the
            // current trigger volume.
            if (testSeparatorRange(distancesByNormal, facingNormal, separatorRangeStart, separatorRangeEnd)) {
                return false;
            }
        }

        // NOTE: unaligned normals for separators are not checked because doing so is a
        // hard problem and this is an approximation. The fully correct topo sort would need
        // to be much more complicated.

        // visibility not disproven
        return true;
    }

    /**
     * Checks if one quad is visible through the other quad. This accepts arbitrary
     * quads, even unaligned ones.
     *
     * @param quad              the quad through which the other quad is being tested
     * @param other             the quad being tested
     * @param distancesByNormal a map of normals to sorted arrays of face plane distances for disproving that the quads are visible through each other, null to disable
     * @return true if the other quad is visible through the first quad
     */
    private static boolean quadVisibleThrough(TQuad quad, TQuad other,
                                              Object2ReferenceOpenHashMap<Vector3fc, float[]> distancesByNormal, Vector3fc cameraPos) {
        if (quad == other) {
            return false;
        }

        var quadFacing = quad.getFacing();
        var otherFacing = other.getFacing();
        boolean result = false;
        if (quadFacing != ModelQuadFacing.UNASSIGNED && otherFacing != ModelQuadFacing.UNASSIGNED) {
            // aligned quads

            // opposites never see each other
            if (quadFacing.getOpposite() == otherFacing) {
                return false;
            }

            // parallel quads, coplanar quads are not visible to each other
            if (quadFacing == otherFacing) {
                var sign = quadFacing.getSign();
                var direction = quadFacing.ordinal();
                result = sign * quad.getExtents()[direction] > sign * other.getExtents()[direction];
            } else {
                // orthogonal quads
                result = orthogonalQuadVisibleThrough(quad, other);
            }
        } else {
            // at least one unaligned quad

            var quadDot = quad.getAccurateDotProduct();
            var quadNormal = quad.getAccurateNormal();
            var otherVertexPositions = other.getVertexPositions();

            // at least one of the other quad's vertexes must be inside the half space of the first quad
            var otherInsideQuad = false;
            for (int i = 0, itemIndex = 0; i < 4; i++) {
                if (pointInsideHalfSpaceEpsilon(quadDot, quadNormal,
                        otherVertexPositions[itemIndex++],
                        otherVertexPositions[itemIndex++],
                        otherVertexPositions[itemIndex++])) {
                    otherInsideQuad = true;
                    break;
                }
            }
            if (otherInsideQuad) {
                var otherDot = other.getAccurateDotProduct();
                var otherNormal = other.getAccurateNormal();
                var quadVertexPositions = quad.getVertexPositions();

                // not all the quad's vertexes must be inside the half space of the other quad
                // i.e. there must be at least one vertex outside the other quad
                var quadNotFullyInsideOther = false;
                for (int i = 0, itemIndex = 0; i < 4; i++) {
                    if (pointOutsideHalfSpaceEpsilon(otherDot, otherNormal,
                            quadVertexPositions[itemIndex++],
                            quadVertexPositions[itemIndex++],
                            quadVertexPositions[itemIndex++])) {
                        quadNotFullyInsideOther = true;
                        break;
                    }
                }

                result = quadNotFullyInsideOther;
            }
        }

        // if enabled and necessary, try to disprove this see-through relationship with a separator plane
        if (result && distancesByNormal != null) {
            return visibilityWithSeparator(quad, other, distancesByNormal, cameraPos);
        }

        return result;
    }

    /**
     * Performs a topological sort without constructing the full graph in memory by
     * doing a DFS on the implicit graph. Edges are tested as they are searched for
     * and if necessary separator planes are used to disprove visibility.
     *
     * @param indexConsumer     the consumer to write the topo sort result to
     * @param allQuads          the quads to sort
     * @param distancesByNormal a map of normals to sorted arrays of face plane
     *                          distances, null to disable
     * @param cameraPos         the camera position, or null to disable the
     *                          visibility check
     */
    public static boolean topoGraphSort(
            IntConsumer indexConsumer, TQuad[] allQuads,
            Object2ReferenceOpenHashMap<Vector3fc, float[]> distancesByNormal,
            Vector3fc cameraPos) {
        // if enabled, check for visibility and produce a mapping of indices
        TQuad[] quads;
        int[] activeToRealIndex = null;

        // keep track of the number of quads to be processed, this is possibly less than quads.length
        int quadCount = 0;

        if (cameraPos != null) {
            // allocate the working quads and index map at the full size to avoid needing to
            // iterate the quads again after checking visibility
            quads = new TQuad[allQuads.length];
            activeToRealIndex = new int[allQuads.length];

            for (int i = 0; i < allQuads.length; i++) {
                TQuad quad = allQuads[i];
                if (pointOutsideHalfSpace(quad.getAccurateDotProduct(), quad.getAccurateNormal(), cameraPos)) {
                    activeToRealIndex[quadCount] = i;
                    quads[quadCount] = quad;
                    quadCount++;
                } else {
                    // write the invisible quads right away
                    indexConsumer.accept(i);
                }
            }
        } else {
            quads = allQuads;
            quadCount = allQuads.length;
        }

        return topoGraphSort(indexConsumer, quads, quadCount, activeToRealIndex, distancesByNormal, cameraPos);
    }

    public static boolean topoGraphSort(IntConsumer indexConsumer, TQuad[] quads, int quadCount, int[] activeToRealIndex, Object2ReferenceOpenHashMap<Vector3fc, float[]> distancesByNormal, Vector3fc cameraPos) {
        // special case for 0 to 2 quads
        if (quadCount == 0) {
            return true;
        }
        if (quadCount == 1) {
            if (activeToRealIndex != null) {
                indexConsumer.accept(activeToRealIndex[0]);
            } else {
                indexConsumer.accept(0);
            }
            return true;
        }

        // special case 2 quads for performance
        if (quadCount == 2) {
            var a = 0;
            var b = 1;
            if (quadVisibleThrough(quads[a], quads[b], null, null)) {
                a = 1;
                b = 0;
            }
            if (activeToRealIndex != null) {
                indexConsumer.accept(activeToRealIndex[a]);
                indexConsumer.accept(activeToRealIndex[b]);
            } else {
                indexConsumer.accept(a);
                indexConsumer.accept(b);
            }
            return true;
        }

        BitArray unvisited = new BitArray(quadCount);
        unvisited.set(0, quadCount);
        int visitedCount = 0;
        BitArray onStack = new BitArray(quadCount);
        int[] stack = new int[quadCount];
        int[] nextEdge = new int[quadCount];

        // start dfs searches until all quads are visited
        while (visitedCount < quadCount) {
            int stackPos = 0;
            var root = unvisited.nextSetBit(0);
            stack[stackPos] = root;
            onStack.set(root);
            nextEdge[stackPos] = 0;

            while (stackPos >= 0) {
                // start at next edge and find an unvisited quad
                var currentQuadIndex = stack[stackPos];
                var nextEdgeTest = unvisited.nextSetBit(nextEdge[stackPos]);
                if (nextEdgeTest != -1) {
                    if (currentQuadIndex != nextEdgeTest) {
                        var currentQuad = quads[currentQuadIndex];
                        var nextQuad = quads[nextEdgeTest];
                        if (quadVisibleThrough(currentQuad, nextQuad, distancesByNormal, cameraPos)) {
                            // if the visible quad is on the stack, there is a cycle
                            if (onStack.getAndSet(nextEdgeTest)) {
                                return false;
                            }

                            // set the next edge
                            nextEdge[stackPos] = nextEdgeTest + 1;

                            // visit the next quad, onStack is already set
                            stackPos++;
                            stack[stackPos] = nextEdgeTest;
                            nextEdge[stackPos] = 0;
                            continue;
                        }
                    }

                    // go to the next edge
                    nextEdgeTest++;

                    // if we haven't reached the end of the edges yet
                    if (nextEdgeTest < quadCount) {
                        nextEdge[stackPos] = nextEdgeTest;
                        continue;
                    }
                }

                // no more edges left, pop the stack
                onStack.unset(currentQuadIndex);
                visitedCount++;
                unvisited.unset(currentQuadIndex);
                stackPos--;

                // write to the index buffer since the order is now correct
                if (activeToRealIndex != null) {
                    indexConsumer.accept(activeToRealIndex[currentQuadIndex]);
                } else {
                    indexConsumer.accept(currentQuadIndex);
                }
            }
        }

        return true;
    }
}
